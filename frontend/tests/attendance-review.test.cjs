const { test } = require("node:test"), assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript");
const React = require("react"), { renderToStaticMarkup } = require("react-dom/server");
require("./register-tsx.cjs");
const flush = () => new Promise(resolve => setImmediate(resolve));
const record = { id: 91, attendanceDate: "2026-10-30", attendanceResult: "PRESENT", submissionType: "BASIC", review: { status: "PENDING", revision: 3, approvalAllowed: true } };
function all(node, predicate) { if (Array.isArray(node)) return node.flatMap(child => all(child, predicate)); if (!React.isValidElement(node)) return []; return [...(predicate(node) ? [node] : []), ...all(node.props.children, predicate)]; }
function harness(api, props = {}) {
  const states = []; let cursor = 0;
  const hooks = { useState(initial) { const i = cursor++; if (!(i in states)) states[i] = initial; return [states[i], value => { states[i] = typeof value === "function" ? value(states[i]) : value; }]; }, useRef(initial) { const i = cursor++; if (!(i in states)) states[i] = { current: initial }; return states[i]; } };
  const m = { exports: {} }, imports = { react: hooks, "@/lib/api": { api }, "@/lib/monthly-progress": { formatDateTime: value => value }, "@/lib/professional-profile": { resumeError: (cause, fallback) => cause?.message || fallback } };
  new Function("require", "module", "exports", ts.transpileModule(fs.readFileSync(path.join(__dirname, "../components/AttendanceReviewPanel.tsx"), "utf8"), { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText)(name => imports[name] || require(name), m, m.exports);
  const current = { role: "MENTOR", record, onChanged() {}, ...props };
  function tree() { cursor = 0; return m.exports.AttendanceReviewPanel(current); }
  return { tree, html: () => renderToStaticMarkup(tree()), button: label => all(tree(), node => node.type === "button" && node.props.children === label)[0],
    reason(value) { all(tree(), node => node.type === "textarea")[0].props.onChange({ target: { value } }); },
    async audit(open = true) { all(tree(), node => node.type === "details" && node.props.onToggle)[0].props.onToggle({ currentTarget: { open } }); await flush(); },
  };
}

test("mentor approval uses the selected record revision and refreshes after success", async () => {
  const calls = []; let changed = 0;
  const h = harness({ async post(url, body) { calls.push({ url, body }); return { data: {} }; } }, { onChanged() { changed++; } });
  assert.equal(h.button("Approve").props.disabled, false);
  h.button("Approve").props.onClick(); await flush();
  assert.deepEqual(calls, [{ url: "/mentor/attendance/91/review", body: { status: "APPROVED", revision: 3, reason: null } }]); assert.equal(changed, 1);
});
test("rejection needs a reason and failures retain the draft for correction", async () => {
  const calls = []; const h = harness({ async post(url, body) { calls.push(body); throw new Error("The review changed. Reload before retrying."); } });
  h.button("Reject").props.onClick(); await flush(); assert.equal(calls.length, 0); assert.match(h.html(), /at least 5 characters/);
  h.reason("Wrong workplace evidence"); h.button("Reject").props.onClick(); await flush();
  assert.equal(calls[0].reason, "Wrong workplace evidence"); assert.match(h.html(), /The review changed/); assert.match(h.html(), /Wrong workplace evidence/);
});
test("IIIC gets read-only review and audit history is fetched only when opened", async () => {
  const calls = []; const h = harness({ async get(url) { calls.push(url); return { data: [{ revision: 1, previousStatus: "PENDING", status: "REJECTED", reviewerName: "Faculty A", reviewedAt: "2026-10-30T08:00:00Z", reason: "GPS evidence needs review" }] }; } }, { role: "ADMIN" });
  assert.equal(h.button("Approve"), undefined); assert.deepEqual(calls, []); await h.audit(false); assert.deepEqual(calls, []);
  await h.audit(); await h.audit(); assert.deepEqual(calls, ["/admin/attendance/91/review/history"]); assert.match(h.html(), /Faculty A/); assert.match(h.html(), /GPS evidence needs review/);
});
test("verification failures disable approval and completed decisions hide mutation controls", () => {
  const failed = harness({}, { record: { ...record, review: { ...record.review, approvalAllowed: false } } });
  assert.equal(failed.button("Approve").props.disabled, true); assert.match(failed.html(), /does not permit approval/); assert.ok(failed.button("Manual review"));
  const completed = harness({}, { record: { ...record, review: { ...record.review, status: "APPROVED", reviewerName: "Faculty A", reviewedAt: "2026-10-30T08:00:00Z" } } });
  assert.equal(completed.button("Approve"), undefined); assert.equal(completed.button("Reject"), undefined); assert.match(completed.html(), /Faculty A/);
});
