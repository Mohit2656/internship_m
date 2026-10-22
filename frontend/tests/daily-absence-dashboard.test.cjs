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
  const hooks = { useState(initial) { const i = cursor++; if (!(i in states)) states[i] = initial; return [states[i], value => { states[i] = typeof value === "function" ? value(states[i]) : value; }]; }, useEffect(effect) { if (effects.length === 0) effects.push(effect); } };
  const component = load(file, { react: hooks, "@/lib/api": { api }, "@/components/MentorRecordDelete": { MentorRecordDelete: () => null }, "@/lib/attendance": load("lib/attendance.ts"), "next/link": { default: ({ children, ...rest }) => React.createElement("a", rest, children) } })[name];
  function render() { cursor = 0; return renderToStaticMarkup(React.createElement(component, props)); }
  return { render, tree() { cursor = 0; return component(props); }, async mount() { render(); effects[0](); await new Promise(resolve => setImmediate(resolve)); } };
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
for (const role of ["ADMIN", "MENTOR"]) test(`${role} dashboard shows one summary per student and opens the selected history`, async () => {
  const calls = [];
  const row = { studentUserId: 42, studentName: "Assigned student", uid: "ENR42", branch: "Engineering", facultyMentor: "Faculty", company: "Company", workflowStatus: "INTERNSHIP_DETAILS_COMPLETE", attendance: { presentDays: 1, absentDays: 1, totalDays: 2, percentage: 50 } };
  const h = harness("components/AttendanceAbsenceOverview.tsx", "AttendanceAbsenceOverview", { role }, { async get(url) { calls.push(url); return { data: [row] }; } });
  await h.mount(); const html = h.render();
  assert.deepEqual(calls, [`/${role.toLowerCase()}/attendance/students`]);
  assert.equal((html.match(/Assigned student/g) || []).length, 1); assert.match(html, /50.0%/);
  function find(node, predicate) { if (!node || typeof node !== "object") return; if (predicate(node)) return node; for (const child of React.Children.toArray(node.props?.children)) { const found = find(child, predicate); if (found) return found; } }
  const button = find(h.tree(), node => node.type === "button" && node.props.children === "Assigned student");
  button.props.onClick();
  const panel = find(h.tree(), node => typeof node.type === "function" && node.props.studentId === 42);
  assert.equal(panel.props.studentId, 42); assert.equal(panel.props.role, role);
});

test("history loads the selected student's chronological records and backend totals", async () => {
  const calls = [];
  const h = harness("components/AttendanceAbsenceOverview.tsx", "StudentAttendanceHistory", { role: "MENTOR", studentId: 42, name: "Assigned student", onBack() {} }, { async get(url) { calls.push(url); return { data: { attendanceZone: "Asia/Kolkata", totals: { presentDays: 1, absentDays: 1, totalDays: 2, percentage: 50 }, records: [
    { id: 1, attendanceDate: "2026-10-19", attendanceResult: "PRESENT", serverSubmittedAt: "2026-10-19T07:00:00Z", livenessStatus: "VERIFIED", submittedLate: true },
    { id: 2, attendanceDate: "2026-10-20", attendanceResult: "ABSENT", submissionType: "AUTO_ABSENT", livenessStatus: "NOT_APPLICABLE" }
  ] } }; } });
  await h.mount(); const html = h.render();
  assert.deepEqual(calls, ["/mentor/attendance/students/42"]);
  assert.match(html, /1 present/); assert.match(html, /1 absent/); assert.match(html, /50.0%/);
  assert.ok(html.indexOf("2026-10-19") < html.indexOf("2026-10-20"));
  assert.match(html, /Back to student list/); assert.match(html, /mentor permission/); assert.match(html, /Liveness verified/);
});
