const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path");
const { sidebarRenderer } = require("./helpers/load-sidebar.cjs");
const render = sidebarRenderer("StudentSidebar");

test("student sidebar groups weekly and monthly reports without duplicate settings", () => {
  const html = render("/student/dashboard");
  for (const label of ["Dashboard", "My Internships", "Attendance History", "Reports", "Weekly Report", "Monthly Report", "Opportunities", "Notifications"]) {
    assert.ok(html.includes(`>${label}<`), label);
  }
  assert.match(html, /href="\/student\/attendance"/);
  assert.match(html, /href="\/student\/weekly-progress"/);
  assert.match(html, /href="\/student\/monthly-progress"/);
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
  assert.match(progress, /id="student-reports-toggle"[^>]*data-current="true"[^>]*aria-expanded="true"/);

  const weekly = render("/student/weekly-progress");
  assert.match(weekly, /href="\/student\/weekly-progress"[^>]*aria-current="page"/);
});
