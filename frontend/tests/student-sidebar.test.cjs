const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript");
const React = require("react"), { renderToStaticMarkup } = require("react-dom/server");

function render(pathname, hash = "") {
  const source = fs.readFileSync(path.join(__dirname, "../components/StudentSidebar.tsx"), "utf8");
  const module = { exports: {} };
  new Function("require", "module", "exports", ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText)(key => key === "next/link" ? { default: ({ children, ...props }) => React.createElement("a", props, children) } : require(key), module, module.exports);
  return renderToStaticMarkup(React.createElement(module.exports.StudentSidebar, { pathname, hash, open: false, onNavigate() {}, onSignOut() {} }));
}

test("student sidebar contains simplified, grouped destinations without duplicate settings or nested report dropdowns", () => {
  const html = render("/student/dashboard");
  for (const label of ["Dashboard", "My Internships", "Attendance History", "Progress Reports", "Opportunities", "Notifications"]) {
    assert.ok(html.includes(`>${label}<`), label);
  }
  assert.match(html, /href="\/student\/attendance"/);
  assert.match(html, /href="\/student\/progress-reports"/);
  assert.doesNotMatch(html, /href="\/student\/settings"/);
  assert.doesNotMatch(html, />Weekly Reports</);
  assert.doesNotMatch(html, />Monthly Reports</);
});

test("nested routes select only their correct item", () => {
  const opportunity = render("/student/opportunities/42");
  assert.match(opportunity, /href="\/student\/opportunities"[^>]*aria-current="page"/);

  const attendance = render("/student/attendance");
  assert.match(attendance, /href="\/student\/attendance"[^>]*aria-current="page"/);
  assert.doesNotMatch(attendance, /href="\/student\/dashboard" aria-current="page"/);

  const progress = render("/student/progress-reports");
  assert.match(progress, /href="\/student\/progress-reports"[^>]*aria-current="page"/);

  const weeklyOld = render("/student/weekly-progress");
  assert.match(weeklyOld, /href="\/student\/progress-reports"[^>]*aria-current="page"/);
});
