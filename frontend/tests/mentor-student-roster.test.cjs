const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript");

const source = fs.readFileSync(path.join(__dirname, "../lib/mentor-student-roster.ts"), "utf8");
const moduleUnderTest = { exports: {} };
new Function("require", "module", "exports", ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 },
}).outputText)(require, moduleUnderTest, moduleUnderTest.exports);
const { buildMentorStudentRoster } = moduleUnderTest.exports;

const attendance = (studentUserId, internshipId, internshipNumber, company, presentDays, absentDays) => ({
  studentUserId, internshipId, internshipNumber, company,
  uid: `UID-${studentUserId}`, studentName: `Student ${studentUserId}`, branch: "CSE",
  facultyMentor: "Mentor", workflowStatus: "ACTIVE",
  attendance: {
    presentDays, absentDays, totalDays: presentDays + absentDays,
    percentage: presentDays * 100 / (presentDays + absentDays),
  },
});

test("assigned-student roster remains one row per student while attendance remains internship-scoped", () => {
  const assigned = [
    { userId: 1, name: "Single Student", uid: "UID-1", branch: "CSE" },
    { userId: 2, name: "Dual Student", uid: "UID-2", branch: "ECE" },
  ];
  const attendanceRows = [
    attendance(1, 11, 1, "Company A", 4, 1),
    attendance(2, 21, 1, "Company B", 3, 1),
    attendance(2, 22, 2, "Company C", 2, 2),
  ];

  const roster = buildMentorStudentRoster(assigned, attendanceRows);
  assert.equal(roster.length, 2);
  assert.deepEqual(roster.map((row) => row.studentUserId), [1, 2]);
  assert.equal(roster[1].company, "Company B · Company C");
  assert.deepEqual(roster[1].attendance, { presentDays: 5, absentDays: 3, totalDays: 8, percentage: 62.5 });
  assert.equal(attendanceRows.length, 3, "internship-level attendance rows must remain separate");
  assert.deepEqual(attendanceRows.filter((row) => row.studentUserId === 2).map((row) => row.internshipId), [21, 22]);
});

test("assigned students without attendance keep a stable roster row and no percentage", () => {
  const roster = buildMentorStudentRoster(
    [{ userId: 7, name: "New Student", uid: "UID-7", branch: "ME" }],
    [],
  );
  assert.equal(roster.length, 1);
  assert.equal(roster[0].studentUserId, 7);
  assert.equal(roster[0].attendance.percentage, null);
  assert.equal(roster[0].company, null);
});
