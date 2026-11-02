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

test("student sidebar contains every audited destination", () => {
  const html = render("/student/dashboard");
  for (const label of ["Home", "Profile", "Professional Profile", "Skill Gaps", "Internship", "Internship Details", "Second Internship", "Opportunities", "Weekly Progress", "Monthly Progress", "Attendance", "Documents &amp; Templates", "Notifications", "Change Password"]) assert.ok(html.includes(`>${label}<`), label);
  assert.match(html, /href="\/student\/dashboard#daily-attendance"/);
});

test("nested routes and attendance anchor select only their correct item", () => {
  const opportunity = render("/student/opportunities/42");
  assert.match(opportunity, /href="\/student\/opportunities"[^>]*aria-current="page"/);
  const attendance = render("/student/dashboard", "daily-attendance");
  assert.match(attendance, /href="\/student\/dashboard#daily-attendance"[^>]*aria-current="page"/);
  assert.doesNotMatch(attendance, /href="\/student\/dashboard" aria-current="page"/);
});
