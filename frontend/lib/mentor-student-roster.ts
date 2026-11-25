import type { StudentAttendanceSummary } from "@/components/AttendanceAbsenceOverview";

export type AssignedStudent = {
  userId: number;
  name: string | null;
  uid: string | null;
  branch: string | null;
};

export function buildMentorStudentRoster(
  assignedStudents: AssignedStudent[],
  attendanceRows: StudentAttendanceSummary[],
): StudentAttendanceSummary[] {
  const attendanceByStudent = new Map<number, StudentAttendanceSummary[]>();
  for (const row of attendanceRows) {
    const rows = attendanceByStudent.get(row.studentUserId) ?? [];
    rows.push(row);
    attendanceByStudent.set(row.studentUserId, rows);
  }

  return assignedStudents.map((student) => {
    const rows = attendanceByStudent.get(student.userId) ?? [];
    const presentDays = rows.reduce((total, row) => total + (row.attendance?.presentDays ?? 0), 0);
    const absentDays = rows.reduce((total, row) => total + (row.attendance?.absentDays ?? 0), 0);
    const totalDays = rows.reduce((total, row) => total + (row.attendance?.totalDays ?? 0), 0);
    const companies = Array.from(new Set(rows.map((row) => row.company?.trim()).filter(Boolean)));
    const first = rows[0];

    return {
      studentUserId: student.userId,
      internshipId: null,
      internshipNumber: null,
      uid: student.uid,
      studentName: student.name,
      branch: student.branch,
      facultyMentor: first?.facultyMentor ?? null,
      company: companies.length ? companies.join(" · ") : null,
      workflowStatus: first?.workflowStatus ?? "NOT_STARTED",
      attendance: {
        presentDays,
        absentDays,
        totalDays,
        percentage: totalDays > 0 ? (presentDays * 100) / totalDays : null,
      },
    };
  });
}
