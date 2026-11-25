const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");

function loadModule(relPath) {
  const source = fs.readFileSync(path.join(__dirname, relPath), "utf8");
  const mod = { exports: {} };
  const js = ts.transpileModule(source, {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 }
  }).outputText;
  new Function("require", "module", "exports", js)(require, mod, mod.exports);
  return mod.exports;
}

test("formatNumericUid correctly accepts only numeric UIDs and preserves leading zeros", () => {
  const { formatNumericUid } = loadModule("../lib/uid-formatter.ts");

  // Valid numeric UIDs
  assert.equal(formatNumericUid("12345"), "12345");
  assert.equal(formatNumericUid("0012345"), "0012345", "Preserves leading zeros");
  assert.equal(formatNumericUid("0"), "0");
  assert.equal(formatNumericUid("2024001"), "2024001");
  assert.equal(formatNumericUid(" 45678 "), "45678", "Trims leading/trailing whitespace");

  // Invalid: emails, letters, symbols, negative numbers, missing
  assert.equal(formatNumericUid("student@example.com"), "", "Rejects email address");
  assert.equal(formatNumericUid("STU12345"), "", "Rejects alphanumeric string with letters");
  assert.equal(formatNumericUid("123-456"), "", "Rejects strings with hyphens");
  assert.equal(formatNumericUid("12.34"), "", "Rejects decimal numbers");
  assert.equal(formatNumericUid(""), "", "Rejects empty string");
  assert.equal(formatNumericUid("   "), "", "Rejects whitespace-only string");
  assert.equal(formatNumericUid(null), "", "Rejects null");
  assert.equal(formatNumericUid(undefined), "", "Rejects undefined");
  assert.equal(formatNumericUid("BISMEET-77"), "", "Rejects non-numeric ID strings");
});

test("navigation route-to-title and formatBackLabel match required origins and destinations", () => {
  const { getRouteTitle, formatBackLabel } = loadModule("../lib/navigation.ts");

  // 1. Reports Analysis -> Student Profile shows "Back to Reports Analysis"
  assert.equal(getRouteTitle("/admin/reports"), "Reports Analysis");
  assert.equal(getRouteTitle("/admin/reports?tab=QUICK"), "Reports Analysis");
  assert.equal(formatBackLabel(getRouteTitle("/admin/reports")), "Back to Reports Analysis");

  // 2. Student Directory -> Student Profile shows "Back to Student Directory"
  assert.equal(getRouteTitle("/admin/students"), "Student Directory");
  assert.equal(formatBackLabel(getRouteTitle("/admin/students")), "Back to Student Directory");

  // 3. Mentor Attendance -> Student Profile shows "Back to Attendance"
  assert.equal(getRouteTitle("/mentor/attendance"), "Attendance");
  assert.equal(formatBackLabel(getRouteTitle("/mentor/attendance")), "Back to Attendance");

  // 4. Admin Attendance -> Student Profile shows "Back to Attendance"
  assert.equal(getRouteTitle("/admin/attendance"), "Attendance");
  assert.equal(formatBackLabel(getRouteTitle("/admin/attendance")), "Back to Attendance");

  // 5. Dashboard routes
  assert.equal(getRouteTitle("/admin/dashboard"), "Dashboard");
  assert.equal(formatBackLabel(getRouteTitle("/admin/dashboard")), "Back to Dashboard");
  assert.equal(getRouteTitle("/admin/dashboard?category=SELF"), "Student Overview");
  assert.equal(formatBackLabel(getRouteTitle("/admin/dashboard?category=SELF")), "Back to Student Overview");

  // 6. Mentor student directory
  assert.equal(getRouteTitle("/mentor/students"), "Student Directory");

  // 7. Fallback labels and clean formatting without duplicate arrows or prefixes
  assert.equal(formatBackLabel(undefined, "Back to dashboard"), "Back to dashboard");
  assert.equal(formatBackLabel(undefined, "← Back to Attendance"), "Back to Attendance");
  assert.equal(formatBackLabel(undefined, "Back to Assigned Students"), "Back to Assigned Students");
  assert.equal(formatBackLabel(undefined, "Dashboard"), "Back to Dashboard");
  assert.equal(formatBackLabel(undefined, ""), "Back");
});

test("CustomReportBuilder: 5-step stepper contains all required stage names and accessible styling", () => {
  const file = path.resolve(__dirname, "../components/CustomReportBuilder.tsx");
  const content = fs.readFileSync(file, "utf8");

  // All 5 stage names
  assert.ok(content.includes('"Title, Session & Scope"'), "Stage 1 must be Title, Session & Scope");
  assert.ok(content.includes('"Select Columns"'), "Stage 2 must be Select Columns");
  assert.ok(content.includes('"Apply Filters"'), "Stage 3 must be Apply Filters");
  assert.ok(content.includes('"Group & Sort"'), "Stage 4 must be Group & Sort");
  assert.ok(content.includes('"Preview & Export"'), "Stage 5 must be Preview & Export");

  // Active step styling
  assert.ok(content.includes("bg-blue-600"), "Active step must use primary blue theme");
  assert.ok(content.includes("text-white"), "Active step must have white text");
  assert.ok(content.includes("ring-2 ring-blue-300"), "Active step number badge must have prominent ring");

  // Completed step styling
  assert.ok(content.includes("✓"), "Completed step must show checkmark");
  assert.ok(content.includes("border-blue-200"), "Completed step must use subtle border");

  // Grid layout for 5 steps
  assert.ok(content.includes("lg:grid-cols-5"), "Stepper must use 5-column responsive grid layout");
});
