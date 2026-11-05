const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

test("III Cell Incharge Dashboard: Institutional overview sections appear in exact sequence without redundant buttons", () => {
  const file = path.resolve(__dirname, "../app/admin/dashboard/page.tsx");
  const content = fs.readFileSync(file, "utf8");

  // Verify the exact remaining sections appear in order:
  // 1. Student Overview
  // 2. Internship Progress
  // 3. Students Requiring Attention
  // 4. Lifecycle Progress
  // 5. Internship Source
  // 6. Company Type
  // 7. Internship Mode
  // 8. Internship Outcomes
  // 9. Top Companies by Student Count
  const s1 = content.indexOf('Section title="Student overview"');
  const s2 = content.indexOf('Section title="Internship progress"');
  const s3 = content.indexOf('Section title="Students requiring attention"');
  const s4 = content.indexOf('Section title="Lifecycle progress"');
  const s5 = content.indexOf('Section title="Internship source"');
  const s6 = content.indexOf('Section title="Company type"');
  const s7 = content.indexOf('Section title="Internship mode"');
  const s8 = content.indexOf('Section title="Internship outcomes"');
  const s9 = content.indexOf('Section title="Top companies by student count"');

  assert.ok(s1 !== -1, "Section 1: Student overview must be present");
  assert.ok(s2 !== -1, "Section 2: Internship progress must be present");
  assert.ok(s3 !== -1, "Section 3: Students requiring attention must be present");
  assert.ok(s4 !== -1, "Section 4: Lifecycle progress must be present");
  assert.ok(s5 !== -1, "Section 5: Internship source must be present");
  assert.ok(s6 !== -1, "Section 6: Company type must be present");
  assert.ok(s7 !== -1, "Section 7: Internship mode must be present");
  assert.ok(s8 !== -1, "Section 8: Internship outcomes must be present");
  assert.ok(s9 !== -1, "Section 9: Top companies by student count must be present");

  assert.ok(s1 < s2, "Section 1 must precede Section 2");
  assert.ok(s2 < s3, "Section 2 must precede Section 3");
  assert.ok(s3 < s4, "Section 3 must precede Section 4");
  assert.ok(s4 < s5, "Section 4 must precede Section 5");
  assert.ok(s5 < s6, "Section 5 must precede Section 6");
  assert.ok(s6 < s7, "Section 6 must precede Section 7");
  assert.ok(s7 < s8, "Section 7 must precede Section 8");
  assert.ok(s8 < s9, "Section 8 must precede Section 9");

  // Redundant buttons / detailed panels removed from Home
  assert.ok(!content.includes("Open student directory"), "Redundant 'Open student directory' button must be removed from Home");
  assert.ok(!content.includes("<CompletionStatistics"), "Redundant detailed CompletionStatistics panel must be removed from Home");

  // Interactive cards & rows use button semantics and DashboardStudentList
  assert.ok(content.includes("DashboardStudentList"), "Must use DashboardStudentList component");
  assert.ok(content.includes("overview-summary-card"), "Uses overview-summary-card class");
  assert.ok(content.includes("dashboard-interactive-btn"), "Uses dashboard-interactive-btn class");

  // Verify the 4 moved sections are NOT on Home Dashboard
  assert.ok(!content.includes("Weekly internship progress"), "Weekly internship progress must not appear on Home");
  assert.ok(!content.includes("RecentNotifications"), "Recent activity / RecentNotifications must not appear on Home");
  assert.ok(!content.includes("AdminDualInternshipPanel"), "Second Internship Requests must not appear on Home");
  assert.ok(!content.includes("AttendanceAbsenceOverview"), "Student Attendance must not appear on Home");
});

test("Dedicated Pages: verify routes exist for Progress, Accounts, Weekly/Monthly Progress, Recent Activity, Second Internship Requests, Attendance, and Completions", () => {
  const progressPath = path.resolve(__dirname, "../app/admin/progress/page.tsx");
  const accountsPath = path.resolve(__dirname, "../app/admin/accounts/page.tsx");
  const recentActivityPath = path.resolve(__dirname, "../app/admin/recent-activity/page.tsx");
  const secondInternshipsPath = path.resolve(__dirname, "../app/admin/second-internship-requests/page.tsx");
  const attendancePath = path.resolve(__dirname, "../app/admin/attendance/page.tsx");
  const completionsPath = path.resolve(__dirname, "../app/admin/internship-completions/page.tsx");

  assert.ok(fs.existsSync(progressPath), "Progress page must exist");
  assert.ok(fs.existsSync(accountsPath), "Accounts page must exist");
  assert.ok(fs.existsSync(recentActivityPath), "Recent activity page must exist");
  assert.ok(fs.existsSync(secondInternshipsPath), "Second internship requests page must exist");
  assert.ok(fs.existsSync(attendancePath), "Attendance page must exist");
  assert.ok(fs.existsSync(completionsPath), "Completions page must exist");

  const progressContent = fs.readFileSync(progressPath, "utf8");
  assert.ok(progressContent.includes("WeeklyProgress"), "Progress page must include WeeklyProgress");
  assert.ok(progressContent.includes("MonthlyProgressReportList"), "Progress page must include MonthlyProgressReportList");
  assert.ok(progressContent.includes("Weekly"), "Progress page must have Weekly tab");
  assert.ok(progressContent.includes("Monthly"), "Progress page must have Monthly tab");

  const accountsContent = fs.readFileSync(accountsPath, "utf8");
  assert.ok(accountsContent.includes("StudentAccountsContent"), "Accounts page must include StudentAccountsContent");
  assert.ok(accountsContent.includes("MentorAccountsContent"), "Accounts page must include MentorAccountsContent");
  assert.ok(accountsContent.includes("Students"), "Accounts page must have Students tab");
  assert.ok(accountsContent.includes("Mentors"), "Accounts page must have Mentors tab");

  const completionsContent = fs.readFileSync(completionsPath, "utf8");
  assert.ok(completionsContent.includes("CompletionStatistics"), "Completions page must include CompletionStatistics");
});


test("Student Detail Page: supports Back to Student Overview when navigated from dashboard", () => {
  const file = path.resolve(__dirname, "../app/admin/students/[id]/page.tsx");
  const content = fs.readFileSync(file, "utf8");

  assert.ok(content.includes("Back to Student Overview"), "Page must support 'Back to Student Overview' action");
  assert.ok(content.includes("/admin/dashboard?category="), "Back action must link back to dashboard with category preserved");
  assert.ok(content.includes("Back to students"), "Preserves default 'Back to students' fallback");
});

test("DashboardStudentList: renders simple name-only links with black light text and dark contrast", () => {
  const file = path.resolve(__dirname, "../components/DashboardStudentList.tsx");
  const content = fs.readFileSync(file, "utf8");

  assert.ok(content.includes("text-slate-900 hover:text-black dark:text-slate-100 dark:hover:text-white hover:underline"),
    "Student name hyperlinks must use black/near-black light text and readable dark contrast with hover underline");
  assert.ok(!content.includes("{student.uid}"), "Student list must not display UID");
  assert.ok(!content.includes("{student.branch}"), "Student list must not display branch");
  assert.ok(!content.includes("{student.phoneNumber}"), "Student list must not display phone number");
  assert.ok(content.includes("academicSessionId"), "Must include academicSessionId filter");
  assert.ok(content.includes("Load more students"), "Must support pagination / incremental loading");
});

test("globals.css: excludes overview-summary-card and dashboard-interactive-btn from global button background overrides", () => {
  const file = path.resolve(__dirname, "../app/globals.css");
  const content = fs.readFileSync(file, "utf8");

  assert.ok(content.includes(":not(.overview-summary-card)"), "Must exclude overview-summary-card from global button background");
  assert.ok(content.includes(":not(.dashboard-interactive-btn)"), "Must exclude dashboard-interactive-btn from global button background");
  assert.ok(content.includes("@custom-variant dark"), "Must define custom dark variant tied to data-theme=dark");
});
