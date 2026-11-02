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

test("students can edit their own internship details and profile without mentor permission", () => {
  const internship = fs.readFileSync(path.join(__dirname, "../app/student/internship/details/page.tsx"), "utf8");
  const profile = fs.readFileSync(path.join(__dirname, "../app/student/profile/page.tsx"), "utf8");
  const mentorStudent = fs.readFileSync(path.join(__dirname, "../app/mentor/students/[id]/page.tsx"), "utf8");

  // Internship details page allows editing without mentor edit permission locks
  for (const slot of [1, 2]) {
    assert.match(internship, new RegExp(`<button onClick=\\{\\(\\) => setEditing${slot}\\(true\\)\\} className="ui-button text-sm">\\s*Edit Details\\s*<\\/button>`));
  }
  assert.doesNotMatch(internship, /disabled=\{!editAllowed\}|StudentEditPermissionStatus/);

  // Student profile remains editable without mentor permission controls
  assert.doesNotMatch(profile, /editAllowed|StudentEditPermissionStatus/);

  // Mentor student view no longer renders mentor edit permission controls
  assert.doesNotMatch(mentorStudent, /MentorEditPermissionPanel/);

  // Authorization and protected routes are preserved
  assert.match(internship, /<ProtectedRoute role="STUDENT">/);
  assert.match(profile, /<ProtectedRoute role="STUDENT">/);

  // Protected workplace location verification component is preserved
  for (const slot of [1, 2]) {
    assert.match(internship, new RegExp(`<InternshipLocationManager\\s+key=\\{details${slot}\\.updatedAt\\}\\s+scope="student"\\s+internshipId=\\{details${slot}\\.id\\}`));
  }
});
