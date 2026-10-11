const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
function load(file, imports = {}) {
  const m = { exports: {} };
  new Function("require", "module", "exports", ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText)(name => imports[name] || require(name), m, m.exports);
  return m.exports;
}
const attendance = load("lib/attendance.ts");
const flush = () => new Promise(resolve => setImmediate(resolve));
const record = status => ({ id: 42, attendanceDate: "2026-10-11", attendanceResult: "PRESENT", verificationStatus: "VERIFIED",
  livenessStatus: status, submissionType: "PHYSICAL_CAPTURE", serverSubmittedAt: "2026-10-11T10:00:00Z",
  evidence: { latitude: 18, longitude: 73, verificationReason: "GPS_ACCURACY_TOO_LOW", workplaceAddress: "Private historical workplace" } });

test("attendance labels use explicit liveness results, preserving absence and historical records", () => {
  for (const [status, expected] of [["VERIFIED", "Liveness verified"], ["FAILED", "Liveness not confirmed"],
    ["NOT_PERFORMED", "Liveness not performed"], ["HISTORICAL", "Historical attendance — liveness not reassessed"],
    [undefined, "Historical attendance — liveness not reassessed"]]) {
    const item = record(status);
    assert.equal(attendance.attendanceLabel(item), `Present — ${expected}`);
    assert.equal(attendance.attendanceLabel({ ...item, attendanceResult: "ABSENT" }), "Absent");
  }
  assert.equal(attendance.attendanceLabel({ ...record("VERIFIED"), verificationStatus: "UNVERIFIED" }), "Present — Liveness verified");
});

function renderStudent(item) {
  let cursor = 0;
  const hooks = { useEffect() {}, useRef() { return { current: null }; }, useState(initial) {
    const index = cursor++;
    return [index === 0 ? { attendance: item } : index === 3 ? false : initial, () => {}];
  }};
  const { TodayAttendanceCard } = load("components/TodayAttendanceCard.tsx", {
    react: hooks, "@/lib/api": {}, "@/lib/attendance": attendance, "@/lib/edge-ai": {}
  });
  return renderToStaticMarkup(React.createElement(TodayAttendanceCard));
}
function renderMentor(item) {
  let cursor = 0;
  const student = { studentUserId: 1, name: "Assigned Student", todayAttendance: item };
  const hooks = { useEffect() {}, useState(initial) {
    const index = cursor++;
    return [index === 1 ? { name: "Mentor" } : index === 2 ? [student] : index === 3 ? false : initial, () => {}];
  }};
  const children = ({ children }) => children;
  const empty = () => null;
  const { default: Page } = load("app/mentor/dashboard/page.tsx", {
    react: hooks, "next/link": { default: ({ children, ...props }) => React.createElement("a", props, children) },
    "@/lib/api": {}, "@/lib/attendance": attendance,
    "@/components/AppFrame": { AppFrame: children }, "@/components/ProtectedRoute": { ProtectedRoute: children },
    "@/components/MentorRecordDelete": { MentorRecordDelete: empty },
    "@/components/MonthlyProgressReportList": { MonthlyProgressReportList: empty },
    "@/components/DashboardReportExport": { DashboardReportExport: empty }
  });
  return renderToStaticMarkup(React.createElement(Page));
}
for (const [status, expected] of [["VERIFIED", "Liveness verified"], ["FAILED", "Liveness not confirmed"],
  ["NOT_PERFORMED", "Liveness not performed"], ["HISTORICAL", "Historical attendance — liveness not reassessed"]]) {
  test(`student and mentor consistently display ${status} without location reasons`, () => {
    for (const html of [renderStudent(record(status)), renderMentor(record(status))]) {
      assert.ok(html.includes(expected));
      assert.doesNotMatch(html, /Attendance unverified|Attendance verified|Private historical workplace|GPS_ACCURACY|Registered workplace|Attendance GPS map/);
    }
  });
}
test("student and mentor render ABSENT separately from liveness verification", () => {
  for (const html of [renderStudent({ ...record("VERIFIED"), attendanceResult: "ABSENT" }), renderMentor({ ...record("VERIFIED"), attendanceResult: "ABSENT" })]) {
    assert.match(html, />Absent</); assert.doesNotMatch(html, /Present|Liveness verified/);
  }
});

function cameraHarness({ liveness = true, method = "PHYSICAL" } = {}) {
  const states = [], refs = [], effects = []; let stateIndex = 0, refIndex = 0, effectIndex = 0;
  const pendingEffects = [], calls = [];
  const video = { videoWidth: 640, videoHeight: 480, async play() {} };
  const canvas = { getContext: () => ({ drawImage() {} }), toBlob: callback => callback(new Blob(["valid captured fixture"], { type: "image/jpeg" })) };
  const hooks = {
    useState(initial) { const i = stateIndex++; if (!(i in states)) states[i] = initial; return [states[i], value => { states[i] = typeof value === "function" ? value(states[i]) : value; }]; },
    useRef(initial) { const i = refIndex++; if (!(i in refs)) refs[i] = { current: i === 0 ? video : i === 1 ? canvas : initial }; return refs[i]; },
    useEffect(effect, dependencies) { const i = effectIndex++; if (!effects[i] || dependencies.some((value, n) => value !== effects[i][n])) { effects[i] = dependencies; pendingEffects.push(effect); } }
  };
  const api = {
    async get(url) { return { data: url.endsWith("/policy") ? { internshipMode: "OFFICE_REPORTING", verificationMethod: method, configurationReady: true } : { attendanceDate: "2026-10-11", attendance: null } }; },
    async post(url, body) { calls.push({ url, body }); return { data: url.endsWith("verification-session") ? { sessionId: "issued-by-server", challenge: "BLINK" } : record(method === "BASIC" ? "NOT_PERFORMED" : "VERIFIED") }; }
  };
  class Analyzer { async initialize() {} start(video, challenge, update) { if (liveness !== null) update({ faceCount: 1, faceDetected: true, singleFace: true, qualityPassed: true, livenessPassed: liveness }); } close() {} }
  const { TodayAttendanceCard } = load("components/TodayAttendanceCard.tsx", { react: hooks, "@/lib/api": { api },
    "@/lib/attendance": attendance, "@/lib/edge-ai": { EdgeAiAnalyzer: Analyzer, challengeLabel: () => "Blink", EDGE_AI_MODEL_VERSION: "existing-model" } });
  function render() { stateIndex = refIndex = effectIndex = 0; return TodayAttendanceCard(); }
  function nodes(type) { const result = []; function walk(node) { if (!node || typeof node !== "object") return; if (node.type === type) result.push(node); React.Children.forEach(node.props?.children, walk); } walk(render()); return result; }
  return { calls, button: label => nodes("button").find(node => node.props.children === label),
    html: () => renderToStaticMarkup(render()),
    async mount() { render(); pendingEffects.splice(0).forEach(effect => effect()); await flush(); },
    cancel() { const cancel = nodes("button").find(node => node.props.children === "Cancel"); cancel?.props.onClick(); }
  };
}
function navigatorWithoutLocation(t) {
  const original = Object.getOwnPropertyDescriptor(globalThis, "navigator"); let locationRequests = 0;
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: {
    mediaDevices: { async getUserMedia() { return { getTracks: () => [{ stop() {} }] }; } },
    get geolocation() { locationRequests += 1; throw new Error("Location permission denied"); }
  }});
  t.after(() => { if (original) Object.defineProperty(globalThis, "navigator", original); else delete globalThis.navigator; });
  return () => locationRequests;
}
test("attendance capture submits liveness evidence without workplace or denied location permission", async t => {
  const locationRequests = navigatorWithoutLocation(t);
  const h = cameraHarness(); await h.mount(); h.button("Mark Today's Attendance").props.onClick(); await flush();
  await h.button("Capture Attendance").props.onClick(); await flush();
  h.button("Submit Attendance").props.onClick(); await flush();
  assert.equal(locationRequests(), 0);
  const submission = h.calls.find(call => call.url.endsWith("/physical")); assert.ok(submission);
  assert.ok(submission.body.has("photo"));
  for (const key of ["latitude", "longitude", "accuracy", "workplace"]) assert.equal(submission.body.has(key), false);
  const evidence = JSON.parse(await submission.body.get("aiEvidence").text());
  assert.equal(evidence.sessionId, "issued-by-server"); assert.equal(evidence.livenessPassed, true);
  assert.match(h.html(), /Liveness verified/); assert.doesNotMatch(h.html(), /location|workplace|GPS/i); h.cancel();
});
for (const liveness of [false, null]) test(`${liveness === null ? "missing" : "failed"} liveness keeps capture disabled without submitting attendance`, async t => {
  const locationRequests = navigatorWithoutLocation(t);
  const h = cameraHarness({ liveness }); await h.mount(); h.button("Mark Today's Attendance").props.onClick(); await flush();
  const capture = h.button("Capture Attendance"); assert.equal(capture.props.disabled, true);
  capture.props.onClick(); await flush();
  assert.match(h.html(), /Complete face positioning and the liveness challenge/);
  assert.equal(h.calls.filter(call => !call.url.endsWith("verification-session")).length, 0);
  assert.equal(locationRequests(), 0); h.cancel();
});
test("existing basic attendance records liveness not performed instead of claiming verification", async t => {
  const locationRequests = navigatorWithoutLocation(t);
  const h = cameraHarness({ method: "BASIC" }); await h.mount(); h.button("Mark Today's Attendance").props.onClick(); await flush();
  assert.equal(h.calls.length, 1); assert.equal(h.calls[0].url, "/student/attendance/today");
  assert.match(h.html(), /Liveness not performed/); assert.doesNotMatch(h.html(), /Liveness verified/); assert.equal(locationRequests(), 0);
});
