const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

test("Mentor dashboard has compact descriptions, no redundant workspace cards, and preserves metrics", () => {
  const file = path.resolve(__dirname, "../app/mentor/dashboard/page.tsx");
  const content = fs.readFileSync(file, "utf8");

  // Welcome section description
  assert.ok(
    content.includes("Manage students, attendance, and progress."),
    "Welcome description must be 'Manage students, attendance, and progress.'"
  );
  assert.ok(
    !content.includes("Supervise assigned students, track workplace attendance, and review internship progress reports."),
    "Old verbose welcome description must be removed"
  );

  // Dynamic Action Required section
  assert.ok(
    content.includes("students below 75% attendance."),
    "Action Required alert must include dynamic '{count} students below 75% attendance.'"
  );

  // Redundant navigation cards removed from Home
  assert.ok(
    !content.includes("Mentor Workspace Sections"),
    "Duplicate Mentor Workspace Sections navigation cards must be removed from Home"
  );

  // Home dashboard preserves essential metrics
  assert.ok(content.includes("Assigned Students"), "Preserves Assigned Students metric");
  assert.ok(content.includes("Attendance Attention"), "Preserves Attendance Attention metric");
  assert.ok(content.includes("Late Attendance"), "Preserves Late Attendance metric");
  assert.ok(content.includes("Recent Workflow Notifications"), "Preserves Recent Notifications");
});

test("Mentor Documents page has compact, professional student selection list with subtle active highlighting", () => {
  const file = path.resolve(__dirname, "../app/mentor/documents/page.tsx");
  const content = fs.readFileSync(file, "utf8");

  // Student selection list must not use data-button-variant outline or giant blue blocks
  assert.ok(
    !content.includes('data-button-variant="outline"'),
    "Student selection list must not use data-button-variant outline"
  );
  assert.ok(
    content.includes('role="option"'),
    "Student selection rows must use option semantics"
  );
  assert.ok(
    content.includes("student-select-btn"),
    "Student selection rows must use student-select-btn class"
  );
  assert.ok(
    content.includes("border-l-blue-600") && content.includes("bg-blue-50"),
    "Selected student option must have subtle blue active highlighting and left accent"
  );
  assert.ok(
    content.includes("tab-mentor-doc-1") && content.includes("tab-mentor-doc-2"),
    "Internship 1 and 2 documents tabs must be present"
  );

  const cssFile = path.resolve(__dirname, "../app/globals.css");
  const cssContent = fs.readFileSync(cssFile, "utf8");
  assert.ok(
    cssContent.includes(".student-select-btn"),
    "globals.css must define dedicated styling for .student-select-btn"
  );
  assert.ok(
    cssContent.includes(":not(.student-select-btn)"),
    "globals.css must exclude .student-select-btn from global button background styles"
  );
});

test("Global dropdown select styling ensures aligned chevron arrow and safe text clearance", () => {
  const file = path.resolve(__dirname, "../app/globals.css");
  const content = fs.readFileSync(file, "utf8");

  assert.ok(
    content.includes("appearance: none !important"),
    "Select elements must use appearance: none to disable OS arrows"
  );
  assert.ok(
    content.includes("background-position: right 0.875rem center !important"),
    "Select elements must have vertically centered, inset chevron arrow"
  );
  assert.ok(
    content.includes("padding-right: 2.5rem !important"),
    "Select elements must have safe right padding so text never overlaps arrow"
  );
});

test("Internal segmented tabs across dashboards use subtle blue active state with readable dark-blue text", () => {
  const filesToCheck = [
    {
      file: "../app/mentor/progress/page.tsx",
      tabs: ["tab-weekly", "tab-monthly"]
    },
    {
      file: "../app/student/progress-reports/page.tsx",
      tabs: ["tab-internship-1", "tab-internship-2", "tab-weekly", "tab-monthly"]
    },
    {
      file: "../components/InternshipSlotsSection.tsx",
      tabs: ["tab-slot-1", "tab-slot-2"]
    },
    {
      file: "../app/student/internship/details/page.tsx",
      tabs: ["tab-details-slot-1", "tab-details-slot-2"]
    },
    {
      file: "../app/student/attendance/page.tsx",
      tabs: ["tab-attendance-slot-1", "tab-attendance-slot-2"]
    },
    {
      file: "../app/mentor/students/[id]/page.tsx",
      tabs: ["tab-mentor-slot-"]
    },
    {
      file: "../app/mentor/documents/page.tsx",
      tabs: ["tab-mentor-doc-1", "tab-mentor-doc-2"]
    },
    {
      file: "../app/student/settings/page.tsx",
      tabs: ["tab-profile", "tab-security", "tab-documents"]
    }
  ];

  for (const item of filesToCheck) {
    const fullPath = path.resolve(__dirname, item.file);
    const content = fs.readFileSync(fullPath, "utf8");

    for (const tab of item.tabs) {
      assert.ok(content.includes(tab), `File ${item.file} must include tab ${tab}`);
    }

    // Must use subtle blue active background and dark blue text
    assert.ok(
      content.includes("bg-blue-50") && content.includes("text-blue-700"),
      `File ${item.file} must have subtle blue active state (bg-blue-50 and text-blue-700)`
    );

    // Must not use oversized dark blue blocks
    assert.ok(
      !content.includes("bg-blue-600 text-white shadow-sm"),
      `File ${item.file} should not use solid dark-blue buttons for segmented controls`
    );

    // Must have focus indicator
    assert.ok(
      content.includes("focus:ring-blue-600"),
      `File ${item.file} must include accessible focus ring for keyboard navigation`
    );
  }
});

test("Mentor Reports Center uses polished navigation panel with light-blue active state and left border indicator", () => {
  const file = path.resolve(__dirname, "../components/ReportsCenter.tsx");
  const content = fs.readFileSync(file, "utf8");

  assert.ok(content.includes("report-nav-item"), "ReportsCenter must use report-nav-item class");
  assert.ok(content.includes("bg-blue-50/80"), "Active report must have light-blue background");
  assert.ok(content.includes("border-l-blue-600"), "Active report must have blue left border indicator");
  assert.ok(
    !content.includes("border-blue-700 bg-blue-600 text-white shadow-sm"),
    "ReportsCenter must not use solid dark-blue block buttons"
  );
});

test("Conditional Internship 2 tabs are not shown for single-internship students", () => {
  const attendanceFile = path.resolve(__dirname, "../app/student/attendance/page.tsx");
  const attendanceContent = fs.readFileSync(attendanceFile, "utf8");
  assert.ok(
    attendanceContent.includes("{details2 && ("),
    "Attendance page must only render internship tabs when details2 exists"
  );

  const progressFile = path.resolve(__dirname, "../app/student/progress-reports/page.tsx");
  const progressContent = fs.readFileSync(progressFile, "utf8");
  assert.ok(
    progressContent.includes("{details2 && ("),
    "Progress reports page must only render internship tabs when details2 exists"
  );

  const mentorStudentFile = path.resolve(__dirname, "../app/mentor/students/[id]/page.tsx");
  const mentorStudentContent = fs.readFileSync(mentorStudentFile, "utf8");
  assert.ok(
    mentorStudentContent.includes("internships.length > 1"),
    "Mentor student details page must only render dual tabs when internships.length > 1"
  );
});
