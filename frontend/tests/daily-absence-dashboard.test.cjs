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
function harness(file, name, props, api) {
  const states = [], effects = []; let cursor = 0;
  const hooks = { useState(initial) { const i = cursor++; if (!(i in states)) states[i] = initial; return [states[i], value => { states[i] = value; }]; }, useEffect(effect) { if (effects.length === 0) effects.push(effect); } };
  const component = load(file, { react: hooks, "@/lib/api": { api }, "@/lib/attendance": load("lib/attendance.ts"), "next/link": { default: ({ children, ...rest }) => React.createElement("a", rest, children) } })[name];
  function render() { cursor = 0; return renderToStaticMarkup(React.createElement(component, props)); }
  return { render, async mount() { render(); effects[0](); await new Promise(resolve => setImmediate(resolve)); } };
}
test("student daily history includes automatic absences in totals and preserves submitted verification", async () => {
  const records = [
    { id: 1, attendanceDate: "2026-10-19", attendanceResult: "ABSENT", automaticAbsent: true, submissionType: "AUTO_ABSENT", livenessStatus: "NOT_APPLICABLE" },
    { id: 2, attendanceDate: "2026-10-16", attendanceResult: "PRESENT", submissionType: "REMOTE_CAPTURE", livenessStatus: "VERIFIED" }
  ];
  const calls = [];
  const h = harness("components/DailyAttendanceHistory.tsx", "DailyAttendanceHistory", {}, { async get(url) { calls.push(url); return { data: records }; } });
  await h.mount(); const html = h.render();
  assert.deepEqual(calls, ["/student/attendance"]); assert.match(html, /2 daily records · 1 present · 1 absent/);
  assert.match(html, /2026-10-19/); assert.match(html, /Absent/); assert.match(html, /Automatic — no submission before deadline/);
  assert.match(html, /Liveness verified/); assert.match(html, /Student submission/);
});
for (const role of ["ADMIN", "MENTOR"]) test(`${role} dashboard uses the authorized attendance report scope for daily absences`, async () => {
  const calls = [];
  const api = { async get(url) { calls.push({ url }); return { data: { date: "2026-10-19" } }; }, async post(url, body) { calls.push({ url, body }); return { data: { totalRows: 1, rows: [{ recordId: 1, studentName: "Assigned student", uid: "123", date: "2026-10-19", result: "ABSENT", automaticAbsent: true }] } }; } };
  const h = harness("components/AttendanceAbsenceOverview.tsx", "AttendanceAbsenceOverview", { role }, api);
  await h.mount(); const html = h.render(); const request = calls[1];
  assert.equal(request.url, `/${role.toLowerCase()}/reports/table`); assert.deepEqual(request.body.studentIds, []);
  assert.deepEqual(request.body.filters, { attendanceStatus: "ABSENT", historyFrom: "2026-09-19", historyTo: "2026-10-19" });
  assert.match(html, /Assigned student/); assert.match(html, /1 absent daily records/); assert.match(html, /Automatic — no submission before deadline/);
  assert.doesNotMatch(html, /GPS|geofence|location/i);
});
