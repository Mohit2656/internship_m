const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
function load(file, imports = {}) {
  const module = { exports: {} };
  new Function("require", "module", "exports", ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText)(name => imports[name] || require(name), module, module.exports);
  return module.exports;
}
const attendance = load("lib/attendance.ts");
const submissionMaps = load("components/LocationMapLink.tsx");
const submissionLocations = load("components/AttendanceLocations.tsx", {"@/components/LocationMapLink": submissionMaps});

const maps = load("components/LocationMapLink.tsx");
test("mentor attendance history opens stored submission coordinates independently of liveness", () => {
  const fields = [{ id: "attendanceLatitude", label: "Submission latitude" }, { id: "attendanceLongitude", label: "Submission longitude" }, { id: "gpsAccuracyMeters", label: "Submission GPS accuracy (m)" }, { id: "verification", label: "Liveness status" }];
  const selected = fields.map(field => field.id), role = "MENTOR";
  const config = JSON.stringify({ reportType: "ATTENDANCE", fields: [...new Set([...selected, "recordId", "studentName", "uid", "date"])], filters: {}, studentIds: [], page: 0, format: "XLSX" });
  const options = { types: [{ id: "ATTENDANCE", title: "Attendance", available: true, fields }], filters: [] };
  const table = { fields, rows: [{ attendanceLatitude: 18.5204, attendanceLongitude: 73.8567, gpsAccuracyMeters: 12, verification: "FAILED" }], summary: {}, totalRows: 1 };
  let cursor = 0; const values = { 0: options, 1: "ATTENDANCE", 4: selected, 5: table, 12: config };
  const hooks = { useEffect() {}, useState(initial) { const i = cursor++; return [i in values ? values[i] : initial, () => {}]; } }, empty = () => null;
  const { ReportsCenter } = load("components/ReportsCenter.tsx", { react: hooks, "@/lib/api": {}, "@/lib/attendance": attendance, "@/lib/monthly-progress": load("lib/monthly-progress.ts", { "@/lib/api": {} }), "@/components/AttendanceLocations": submissionLocations, "@/components/MentorRecordDelete": { MentorRecordDelete: empty }, "@/components/DashboardReportExport": { DashboardReportExport: empty } });
  const html = renderToStaticMarkup(React.createElement(ReportsCenter, { role, attendanceOnly: true }));
  assert.match(html, /query=18[.]5204%2C73[.]8567/); assert.match(html, /Open submission location in Google Maps/); assert.match(html, /Liveness not confirmed/); assert.doesNotMatch(html, /workplace|geofence|radius/i);
});
for (const role of ["ADMIN", "MENTOR"]) test(`${role} attendance records use liveness filters, historical status and readable summary`, () => {
  const fields = [{ id: "verification", label: "Liveness status" }, { id: "studentName", label: "Student" }, { id: "date", label: "Date" }];
  const selected = fields.map(field => field.id);
  const config = JSON.stringify({ reportType: "ATTENDANCE", fields: role === "MENTOR" ? [...new Set([...selected, "recordId", "studentName", "uid", "date"])] : selected,
    filters: {}, studentIds: [], page: 0, format: "XLSX" });
  const options = { types: [{ id: "ATTENDANCE", title: "Attendance", available: true, fields }], filters: [{ id: "verificationStatus", label: "Liveness status", type: "select",
    choices: ["VERIFIED", "FAILED", "NOT_PERFORMED", "HISTORICAL"].map(value => ({ value, label: attendance.livenessLabel(value) })) }] };
  const table = { fields, rows: ["VERIFIED", "FAILED", "NOT_PERFORMED", "HISTORICAL"].map(verification => ({ verification, studentName: "Assigned Student", date: "2026-10-11" })),
    summary: { verified: 1, unverified: 1, historical: 1, notPerformed: 1 }, totalRows: 4 };
  let cursor = 0;
  const values = { 0: options, 1: "ATTENDANCE", 4: selected, 5: table, 12: config };
  const hooks = { useEffect() {}, useState(initial) { const i = cursor++; return [i in values ? values[i] : initial, () => {}]; } };
  const empty = () => null;
  const { ReportsCenter } = load("components/ReportsCenter.tsx", { react: hooks, "@/lib/api": {}, "@/components/AttendanceLocations": submissionLocations, "@/lib/attendance": attendance, "@/lib/monthly-progress": load("lib/monthly-progress.ts", {"@/lib/api": {}}),
    "@/components/LocationMapLink": maps, "@/components/MentorRecordDelete": { MentorRecordDelete: empty }, "@/components/DashboardReportExport": { DashboardReportExport: empty } });
  const html = renderToStaticMarkup(React.createElement(ReportsCenter, { role, attendanceOnly: true }));
  for (const status of ["VERIFIED", "FAILED", "NOT_PERFORMED", "HISTORICAL"]) assert.ok(html.includes(attendance.livenessLabel(status)));
  assert.match(html, /Attendance Records|Liveness status/);
  assert.doesNotMatch(html, /Unverified|GPS|workplace|geofence|radius/i);
});

for (const role of ["ADMIN", "MENTOR"]) test(`${role} selected GPS report column shows backend location outcomes independently of liveness`, () => {
  const fields = [{ id: "locationVerificationStatus", label: "GPS location verification" }, { id: "verification", label: "Liveness status" }];
  const selected = fields.map(field => field.id);
  const config = JSON.stringify({ reportType: "ATTENDANCE", fields: role === "MENTOR" ? [...new Set([...selected, "recordId", "studentName", "uid", "date"])] : selected,
    filters: {}, studentIds: [], page: 0, format: "XLSX" });
  const options = { types: [{ id: "ATTENDANCE", title: "Attendance", available: true, fields }], filters: [] };
  const table = { fields, rows: [
    { locationVerificationStatus: "VERIFIED", verification: "FAILED" },
    { locationVerificationStatus: "OUTSIDE_RADIUS", verification: "VERIFIED" },
    { locationVerificationStatus: null, verification: "HISTORICAL" },
  ], summary: {}, totalRows: 3 };
  let cursor = 0;
  const values = { 0: options, 1: "ATTENDANCE", 4: selected, 5: table, 12: config };
  const empty = () => null;
  const { ReportsCenter } = load("components/ReportsCenter.tsx", {
    react: { useEffect() {}, useState(initial) { const i = cursor++; return [i in values ? values[i] : initial, () => {}]; } },
    "@/lib/api": {}, "@/lib/attendance": attendance, "@/lib/monthly-progress": load("lib/monthly-progress.ts", { "@/lib/api": {} }),
    "@/components/AttendanceLocations": submissionLocations,
    "@/components/MentorRecordDelete": { MentorRecordDelete: empty }, "@/components/DashboardReportExport": { DashboardReportExport: empty }
  });
  const html = renderToStaticMarkup(React.createElement(ReportsCenter, { role, attendanceOnly: true }));
  assert.match(html, /Workplace location verified<\/td><td[^>]*>Liveness not confirmed/);
  assert.match(html, /Outside permitted workplace radius<\/td><td[^>]*>Liveness verified/);
  assert.match(html, /Not assessed<\/td><td[^>]*>Historical attendance/);
  assert.doesNotMatch(html, />OUTSIDE_RADIUS</);
});
