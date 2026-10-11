const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
function load(file, imports = {}) {
  const m = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, "..", file), "utf8");
  new Function("require", "module", "exports", ts.transpileModule(source, {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText)(name => imports[name] || require(name), m, m.exports);
  return m.exports;
}
const attendance = load("lib/attendance.ts");
const maps = load("components/LocationMapLink.tsx");
const locations = load("components/AttendanceLocations.tsx", { "@/components/LocationMapLink": maps });
const evidence = { latitude: 18.5204, longitude: 73.8567, gpsAccuracyMeters: 106,
  expectedLatitude: 18.5204, expectedLongitude: 73.8567, distanceMeters: 0,
  geofenceRadiusMeters: 200, locationCheckPassed: false, workplaceAddress: "Registered branch",
  verificationReason: "GPS_ACCURACY_TOO_LOW" };
function renderEvidence(overrides = {}) {
  return renderToStaticMarkup(React.createElement(locations.AttendanceLocations, { evidence: { ...evidence, ...overrides } }));
}
test("ABSENT remains absent independently of verification status", () => {
  for (const verificationStatus of ["UNVERIFIED", "VERIFIED"])
    assert.equal(attendance.attendanceLabel({ attendanceResult: "ABSENT", verificationStatus }), "Absent");
  assert.equal(attendance.attendanceLabel({ attendanceResult: "PRESENT", verificationStatus: "UNVERIFIED" }), "Present — Unverified");
});
test("near workplace with poor accuracy shows the reason while retaining both real Maps links", () => {
  const html = renderEvidence();
  assert.match(html, /accuracy is too low/);
  assert.match(html, /Reported GPS accuracy: 106 m/);
  assert.match(html, /GPS distance\/accuracy check: Failed/);
  assert.match(html, /Distance: 0 m; radius: 200 m/);
  assert.match(html, /Attendance GPS map/);
  assert.match(html, /Attendance workplace map/);
  assert.match(html, /query=18[.]5204%2C73[.]8567/);
});
test("passed GPS check is distinct from an AI verification failure", () => {
  const html = renderEvidence({ locationCheckPassed: true, verificationReason: "EDGE_AI_LIVENESS_FAILED", gpsAccuracyMeters: 10 });
  assert.match(html, /GPS distance\/accuracy check: Passed/);
  assert.match(html, /EDGE AI LIVENESS FAILED/);
  assert.doesNotMatch(html, /checks passed/);
});
test("a pending workplace explains dual approval even when the distance check passes", () => {
  const html = renderEvidence({ locationCheckPassed: true, verificationReason: "EXPECTED_LOCATION_NOT_CONFIRMED" });
  assert.match(html, /requires approval from both/);
  assert.doesNotMatch(html, /attendance verification checks passed/);
});
test("missing and invalid GPS never produce an attendance Maps link", () => {
  for (const verificationReason of ["LIVE_LOCATION_UNAVAILABLE", "INVALID_LIVE_LOCATION"]) {
    const html = renderEvidence({ latitude: null, longitude: null, gpsAccuracyMeters: null, locationCheckPassed: null, verificationReason });
    assert.match(html, /Browser GPS unavailable/);
    assert.match(html, /Attendance workplace map/);
    assert.doesNotMatch(html, /Attendance GPS map/);
  }
  assert.doesNotMatch(renderEvidence({ latitude: 91 }), /Attendance GPS map/);
});
test("student attendance card renders the actual server reason and a separate overall badge", () => {
  let cursor = 0;
  const record = { attendanceResult: "PRESENT", verificationStatus: "UNVERIFIED", submissionType: "PHYSICAL_CAPTURE", serverSubmittedAt: "2026-10-11T10:00:00Z", evidence };
  const hooks = { useEffect() {}, useRef() { return { current: null }; }, useState(initial) {
    const index = cursor++;
    return [index === 0 ? { attendance: record } : index === 3 ? false : initial, () => {}];
  }};
  const { TodayAttendanceCard } = load("components/TodayAttendanceCard.tsx", {
    react: hooks, "@/lib/api": {}, "@/lib/attendance": attendance,
    "@/lib/edge-ai": {}, "@/components/AttendanceLocations": locations
  });
  const html = renderToStaticMarkup(React.createElement(TodayAttendanceCard));
  assert.match(html, /Present — Unverified/);
  assert.match(html, /Attendance unverified/);
  assert.match(html, /accuracy is too low/);
  assert.match(html, /Attendance GPS map/);
});
test("mentor dashboard renders ABSENT / UNVERIFIED as absent", () => {
  let cursor = 0;
  const student = { studentUserId: 1, name: "Assigned Student", todayAttendance: {
    id: 1, attendanceDate: "2026-10-11", attendanceResult: "ABSENT", verificationStatus: "UNVERIFIED", evidence: null
  }};
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
    "@/components/AttendanceLocations": locations,
    "@/components/MentorRecordDelete": { MentorRecordDelete: empty },
    "@/components/MonthlyProgressReportList": { MonthlyProgressReportList: empty },
    "@/components/DashboardReportExport": { DashboardReportExport: empty }
  });
  const html = renderToStaticMarkup(React.createElement(Page));
  assert.match(html, />Absent</);
  assert.doesNotMatch(html, /Present/);
});
