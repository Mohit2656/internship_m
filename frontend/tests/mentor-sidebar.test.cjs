const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript");
const React = require("react"), { renderToStaticMarkup } = require("react-dom/server");

function render(pathname, hash = "") {
  const source = fs.readFileSync(path.join(__dirname, "../components/MentorSidebar.tsx"), "utf8");
  const module = { exports: {} };
  new Function(
    "require",
    "module",
    "exports",
    ts.transpileModule(source, {
      compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 },
    }).outputText
  )(
    (key) =>
      key === "next/link"
        ? { default: ({ children, ...props }) => React.createElement("a", props, children) }
        : require(key),
    module,
    module.exports
  );
  return renderToStaticMarkup(
    React.createElement(module.exports.MentorSidebar, { pathname, hash, open: false, onNavigate() {}, onSignOut() {} })
  );
}

test("mentor sidebar contains consolidated mentor destinations", () => {
  const html = render("/mentor/dashboard");
  for (const label of [
    "Home",
    "Assigned Students",
    "Attendance",
    "Progress",
    "Documents",
    "Reports",
    "Notifications",
    "Change Password",
  ]) {
    assert.ok(html.includes(`>${label}<`), label);
  }
  // Confirms redundant sidebar items are removed
  assert.doesNotMatch(html, />Late Attendance Permissions</);
  assert.doesNotMatch(html, />Weekly Progress</);
  assert.doesNotMatch(html, />Monthly Progress</);
});

test("mentor nested and consolidated navigation highlight correctly", () => {
  assert.match(render("/mentor/students/42"), /href="\/mentor\/students"[^>]*aria-current="page"/);
  assert.match(render("/mentor/students"), /href="\/mentor\/students"[^>]*aria-current="page"/);
  assert.match(render("/mentor/attendance"), /href="\/mentor\/attendance"[^>]*aria-current="page"/);
  assert.match(render("/mentor/progress"), /href="\/mentor\/progress"[^>]*aria-current="page"/);
});

test("assigned students table is moved to /mentor/students and removed from dashboard", () => {
  const studentsSource = fs.readFileSync(path.join(__dirname, "../app/mentor/students/page.tsx"), "utf8");
  for (const heading of ["UID", "Student Name", "Branch", "Company", "Attendance %"]) {
    assert.ok(studentsSource.includes(heading), heading);
  }
  assert.match(studentsSource, /localeCompare\(right\.uid[^\n]+numeric: true/);
  assert.match(studentsSource, /href=\{`\/mentor\/students\/\$\{student\.studentUserId\}`\}/);

  const dashboardSource = fs.readFileSync(path.join(__dirname, "../app/mentor/dashboard/page.tsx"), "utf8");
  assert.doesNotMatch(dashboardSource, /<table/);
  assert.doesNotMatch(dashboardSource, /<tbody>/);
  assert.match(dashboardSource, /href="\/mentor\/students"/);
  assert.match(dashboardSource, /href="\/mentor\/attendance"/);
  assert.match(dashboardSource, /href="\/mentor\/progress"/);
});

test("student profile includes back button to assigned students", () => {
  const profileSource = fs.readFileSync(path.join(__dirname, "../app/mentor/students/[id]/page.tsx"), "utf8");
  assert.match(profileSource, /href="\/mentor\/students"/);
  assert.match(profileSource, /Back to Assigned Students/);
});
