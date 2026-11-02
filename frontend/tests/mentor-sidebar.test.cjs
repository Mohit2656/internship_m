const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path");
const { sidebarRenderer } = require("./helpers/load-sidebar.cjs");
const render = sidebarRenderer("MentorSidebar");

test("mentor sidebar preserves all mentor destinations", () => {
  const html = render("/mentor/dashboard");
  for (const label of ["Home", "Assigned Students", "Attendance", "Attendance Report", "Late Attendance Requests", "Reports", "Weekly Report", "Monthly Report", "Documents", "Report Center", "Notifications", "Change Password"]) assert.ok(html.includes(`>${label}<`), label);
});

test("mentor nested and attendance section navigation highlight correctly", () => {
  assert.match(render("/mentor/students/42"), /dashboard#assigned-students"[^>]*aria-current="page"/);
  const late = render("/mentor/attendance", "late-attendance-permissions");
  assert.match(late, /attendance#late-attendance-permissions"[^>]*aria-current="page"/);
  assert.doesNotMatch(late, /href="\/mentor\/attendance" aria-current="page"/);
});

test("mentor dashboard uses the concise authoritative student table", () => {
  const source = fs.readFileSync(path.join(__dirname, "../app/mentor/dashboard/page.tsx"), "utf8");
  for (const heading of ["UID", "Student Name", "Academic details", "Company", "Attendance %"]) assert.ok(source.includes(`"${heading}"`), heading);
  assert.match(source, /localeCompare\(right\.uid[^\n]+numeric: true/);
  assert.match(source, /href=\{`\/mentor\/students\/\$\{student\.studentUserId\}`\}/);
  assert.doesNotMatch(source, /Faculty Mentor|Mentor Review|Workflow Status|View Profile|LateAttendancePermissions|AttendanceAbsenceOverview role=/);
  assert.doesNotMatch(source, /instituteEmail|personalEmail/);
});
