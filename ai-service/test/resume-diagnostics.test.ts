import assert from "node:assert/strict";
import { test } from "node:test";
import { inspect } from "node:util";
import { PDFDocument, StandardFonts } from "pdf-lib";
import { buildApp } from "../src/server.js";
import { createDiagnosticRedactor, describeError, resourceFailure } from "../src/resume-diagnostics.js";

function resumeForm(bytes: Uint8Array = new TextEncoder().encode("synthetic test resume")) {
  const form = new FormData();
  form.set("file", new File([new Uint8Array(bytes)], "diagnostic-resume.pdf", { type: "application/pdf" }));
  return form;
}

test("resume failure logs the original stack and full cause chain while keeping HTTP safe", async t => {
  const logs: unknown[][] = [];
  const errors: unknown[][] = [];
  t.mock.method(console, "info", (...args: unknown[]) => { logs.push(args); });
  t.mock.method(console, "error", (...args: unknown[]) => { errors.push(args); });
  const nativeCause = new Error("diagnostic native-library failure");
  const loaderCause = new Error("diagnostic model-loader failure", { cause: nativeCause });
  const failure = new TypeError("diagnostic inference failure", { cause: loaderCause });
  const app = buildApp({ analyzer: async (_bytes, _filename, _mime, _path, reportStage) => {
    reportStage?.("extraction_started");
    reportStage?.("extraction_completed");
    reportStage?.("model_inference_started");
    throw failure;
  } });
  try {
    const bytes = new TextEncoder().encode("synthetic test resume");
    const response = await app.inject({ method: "POST", url: "/internal/resume/analyze", payload: resumeForm(bytes) });
    assert.equal(response.statusCode, 500);
    assert.deepEqual(response.json(), { code: "INFERENCE_FAILED", message: "Local model inference failed" });
    assert.equal(errors.length, 1);
    const context = errors[0][1] as Record<string, unknown>;
    assert.equal(context.stage, "model_inference_started");
    assert.equal(context.filename, "diagnostic-resume.pdf");
    assert.equal(context.mimeType, "application/pdf");
    assert.equal(context.fileSize, bytes.length);
    assert.equal(context.errorName, failure.name);
    assert.equal(context.errorMessage, failure.message);
    assert.equal(context.stack, failure.stack);
    const rendered = inspect(errors, { depth: null });
    assert.ok(rendered.includes(nativeCause.message));
    assert.ok(rendered.includes(loaderCause.message));
    assert.equal(((context.cause as Record<string, unknown>).cause as Record<string, unknown>).stack, nativeCause.stack);
    const contexts = logs.map(log => log[1] as Record<string, unknown>);
    assert.deepEqual(contexts.map(context => context.event), ["request_received", "file_received", "file_read", "extraction_started", "extraction_completed", "model_inference_started"]);
    assert.ok(contexts.every(log => log.requestId === context.requestId));
  } finally { await app.close(); }
});

test("real PDF extraction logs its completion before a missing-model initialization failure", async t => {
  const logs: unknown[][] = [];
  const errors: unknown[][] = [];
  t.mock.method(console, "info", (...args: unknown[]) => { logs.push(args); });
  t.mock.method(console, "error", (...args: unknown[]) => { errors.push(args); });
  const document = await PDFDocument.create();
  const font = await document.embedFont(StandardFonts.Helvetica);
  document.addPage().drawText("Example Applicant. Skills: Java and Python.", { x: 40, y: 700, font, size: 12 });
  const bytes = await document.save();
  const app = buildApp({ modelPath: "/definitely/missing/diagnostic-model" });
  try {
    const response = await app.inject({ method: "POST", url: "/internal/resume/analyze", payload: resumeForm(bytes) });
    assert.equal(response.statusCode, 500);
    assert.deepEqual(response.json(), { code: "INFERENCE_FAILED", message: "Local model inference failed" });
    const events = logs.map(log => (log[1] as Record<string, unknown>).event);
    assert.ok(events.includes("extraction_started"));
    assert.ok(events.includes("extraction_completed"));
    assert.ok(events.includes("model_loading_started"));
    assert.ok(events.includes("model_initialization_started"));
    assert.ok(!events.includes("model_inference_started"));
    assert.equal(errors.length, 1);
    assert.equal((errors[0][1] as Record<string, unknown>).stage, "model_initialization_started");
    const resources = (errors[0][1] as Record<string, unknown>).resources as Record<string, unknown>;
    assert.ok(Number(resources.rssBytes) > 0);
    assert.ok(Number(resources.heapLimitBytes) > 0);
    assert.ok((errors[0][1] as Record<string, unknown>).stack);
  } finally { await app.close(); }
});

test("successful resume analysis logs completion and preserves the result", async t => {
  const logs: unknown[][] = [];
  t.mock.method(console, "info", (...args: unknown[]) => { logs.push(args); });
  const result = { modelName: "test", modelVersion: "1", skills: ["Java"], experience: [], education: [], certifications: [] };
  const app = buildApp({ analyzer: async (_bytes, _filename, _mime, _path, reportStage) => {
    for (const stage of ["extraction_started", "extraction_completed", "model_inference_started", "model_inference_completed", "response_preparation_started"] as const) reportStage?.(stage);
    return result;
  } });
  try {
    const response = await app.inject({ method: "POST", url: "/internal/resume/analyze", payload: resumeForm() });
    assert.equal(response.statusCode, 200);
    assert.deepEqual(response.json(), result);
    assert.equal((logs.at(-1)![1] as Record<string, unknown>).event, "completed");
  } finally { await app.close(); }
});

test("real extraction failures log their original error before inference starts", async t => {
  const logs: unknown[][] = [];
  const errors: unknown[][] = [];
  t.mock.method(console, "info", (...args: unknown[]) => { logs.push(args); });
  t.mock.method(console, "error", (...args: unknown[]) => { errors.push(args); });
  const app = buildApp({ modelPath: "/definitely/missing/diagnostic-model" });
  try {
    const response = await app.inject({ method: "POST", url: "/internal/resume/analyze", payload: resumeForm() });
    assert.ok([422, 500].includes(response.statusCode));
    assert.ok(!logs.some(log => (log[1] as Record<string, unknown>).event === "model_inference_started"));
    assert.equal(errors.length, 1);
    const context = errors[0][1] as Record<string, unknown>;
    assert.equal(context.stage, "extraction_started");
    assert.ok(context.errorMessage);
    assert.ok(context.stack);
    assert.deepEqual(Object.keys(response.json()).sort(), ["code", "message"]);
    assert.ok(!response.body.includes(String(context.stack)));
  } finally { await app.close(); }
});


test("diagnostic allowlist redacts input text, credentials and personal fields from errors and causes", async t => {
  const errors: unknown[][] = [];
  t.mock.method(console, "info", () => {});
  t.mock.method(console, "error", (...args: unknown[]) => { errors.push(args); });
  const privateText = "Private Applicant Resume Content";
  const nativeCause = Object.assign(new Error("ENOMEM allocation failed; api_key=private-key-example"), {
    input: privateText, password: "hidden-property-password", parsedResume: { name: "Hidden Personal Name" }
  });
  const failure = new Error(`Inference failed for ${privateText}; private@example.test; Bearer private-bearer-example`, { cause: nativeCause });
  const app = buildApp({ analyzer: async (_bytes, _filename, _mime, _path, reportStage, registerSensitiveText) => {
    registerSensitiveText?.(privateText);
    reportStage?.("model_inference_started");
    throw failure;
  } });
  try {
    const response = await app.inject({ method: "POST", url: "/internal/resume/analyze", payload: resumeForm() });
    assert.deepEqual(response.json(), { code: "INFERENCE_FAILED", message: "Local model inference failed" });
    const rendered = inspect(errors, { depth: null });
    for (const value of [privateText, "private-key-example", "private@example.test", "private-bearer-example", "hidden-property-password", "Hidden Personal Name"]) {
      assert.ok(!rendered.includes(value), `Sensitive test value leaked: ${value}`);
    }
    assert.ok(rendered.includes("ENOMEM"));
    assert.ok(rendered.includes("allocation failed"));
    assert.ok(rendered.includes("[REDACTED]"));
    assert.equal((errors[0][1] as Record<string, unknown>).resourceFailure, true);
  } finally { await app.close(); }
});

test("diagnostics preserve safe stacks, circular/aggregate causes, and redact configured credentials", () => {
  const { redact } = createDiagnosticRedactor(["configured-secret-example"]);
  const cause = new Error("Library failure configured-secret-example");
  const aggregate = new AggregateError([cause], "Resource exhausted");
  aggregate.cause = aggregate;
  const safe = describeError(aggregate, redact);
  assert.ok(safe.stack?.includes("AggregateError"));
  assert.equal(safe.cause?.name, "CircularCause");
  assert.ok(safe.errors?.[0].stack?.includes("[REDACTED]"));
  assert.ok(!JSON.stringify(safe).includes("configured-secret-example"));
  assert.equal(resourceFailure(safe), true);
  assert.deepEqual(describeError({ message: "Failure", resume: "private payload" }, redact), {
    name: "UnknownError", message: "Failure", stack: undefined, code: undefined, cause: undefined, errors: undefined
  });
});
