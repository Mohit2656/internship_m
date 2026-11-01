const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");

function loadLibrary() {
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, "../lib/edit-permissions.ts"), "utf8");
  new Function("module", "exports", ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText)(module, module.exports);
  return module.exports;
}

test("student permission labels distinguish pre-start, active and locked access", () => {
  const { permissionLabel } = loadLibrary();
  assert.match(permissionLabel({ internshipStarted: false }), /Editable until/);
  assert.match(permissionLabel({ internshipStarted: true, editable: true, expiresAt: "2026-11-02T10:00:00Z" }), /One edit available until/);
  assert.match(permissionLabel({ internshipStarted: true, editable: false, status: "LOCKED" }), /Locked/);
});

test("mentor control is internship-only while student profile remains editable", () => {
  const control = fs.readFileSync(path.join(__dirname, "../components/EditPermissionStatus.tsx"), "utf8");
  const internship = fs.readFileSync(path.join(__dirname, "../app/student/internship/details/page.tsx"), "utf8");
  const profile = fs.readFileSync(path.join(__dirname, "../app/student/profile/page.tsx"), "utf8");
  assert.match(control, /Grant Edit Access/); assert.match(control, /Revoke Edit Access/);
  assert.match(control, /Internship details edit access/); assert.doesNotMatch(control, /Student profile<\/p>/);
  assert.match(internship, /disabled={!editAllowed}/); assert.doesNotMatch(profile, /editAllowed|StudentEditPermissionStatus/);
});
