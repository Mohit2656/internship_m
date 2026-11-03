const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path");

test("student dashboard no longer contains Internship Slots & Requests section", () => {
  const dashboardSource = fs.readFileSync(path.join(__dirname, "../app/student/dashboard/page.tsx"), "utf8");
  assert.ok(!dashboardSource.includes("Internship Slots & Requests"));
  assert.ok(!dashboardSource.includes("Internship Slots &amp; Requests"));
  assert.ok(!dashboardSource.includes("renderInternshipOverview"));
  assert.ok(!dashboardSource.includes("id=\"tab-slot-1\""));
  assert.ok(!dashboardSource.includes("id=\"tab-slot-2\""));
  assert.ok(!dashboardSource.includes("id=\"panel-slot-1\""));
  assert.ok(!dashboardSource.includes("id=\"panel-slot-2\""));
  
  // Confirms essential dashboard elements are preserved
  assert.ok(dashboardSource.includes("TodayAttendanceCard"));
  assert.ok(dashboardSource.includes("CompactProfileCompletion"));
  assert.ok(dashboardSource.includes("Pending Actions"));
  assert.ok(dashboardSource.includes("InternshipLocationManager"));
  assert.ok(dashboardSource.includes("renderRecentNotifications"));
});

test("my internships page includes InternshipSlotsSection near top before detailed steps", () => {
  const internshipPageSource = fs.readFileSync(path.join(__dirname, "../app/student/internship/page.tsx"), "utf8");
  assert.ok(internshipPageSource.includes("InternshipSlotsSection"));
  assert.ok(internshipPageSource.includes("<InternshipSlotsSection"));
  
  const slotsIndex = internshipPageSource.indexOf("<InternshipSlotsSection");
  const detailsIndex = internshipPageSource.indexOf("Preparation &amp; Document Verification");
  assert.ok(slotsIndex > 0, "InternshipSlotsSection should be present in page JSX");
  assert.ok(detailsIndex > slotsIndex, "InternshipSlotsSection should be placed before detailed internship information");
});

test("InternshipSlotsSection component implements dual slots, actions, and isolation", () => {
  const slotsSectionSource = fs.readFileSync(path.join(__dirname, "../components/InternshipSlotsSection.tsx"), "utf8");
  assert.ok(slotsSectionSource.includes("Internship Slots &amp; Requests"));
  assert.ok(slotsSectionSource.includes("id=\"tab-slot-1\""));
  assert.ok(slotsSectionSource.includes("id=\"tab-slot-2\""));
  assert.ok(slotsSectionSource.includes("id=\"panel-slot-1\""));
  assert.ok(slotsSectionSource.includes("id=\"panel-slot-2\""));
  assert.ok(slotsSectionSource.includes("id=\"raise-request-slot-1\""));
  assert.ok(slotsSectionSource.includes("id=\"raise-request-slot-2\""));
  assert.ok(slotsSectionSource.includes("Second Internship Slot"));
  assert.ok(slotsSectionSource.includes("First Internship Slot"));
});
