"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { MentorAccount } from "@/lib/mentor-accounts";
import { StudentAttendanceSummary, formatAttendancePercentage } from "@/components/AttendanceAbsenceOverview";
import { DashboardReportExport } from "@/components/DashboardReportExport";

function MentorDashboardContent() {
  const [mentor, setMentor] = useState<MentorAccount | null>(null);
  const [students, setStudents] = useState<StudentAttendanceSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([
      api.get<MentorAccount>("/mentor/profile"),
      api.get<StudentAttendanceSummary[]>("/mentor/attendance/students")
    ]).then(([profile, assigned]) => {
      setMentor(profile.data); setStudents(assigned.data);
    }).catch(() => setError("Unable to load your mentor dashboard."))
      .finally(() => setLoading(false));
  }, []);
  const sortedStudents = [...students].sort((left, right) => {
    if (!left.uid) return right.uid ? 1 : left.studentUserId - right.studentUserId;
    if (!right.uid) return -1;
    return left.uid.localeCompare(right.uid, undefined, { numeric: true, sensitivity: "base" });
  });

  return (
    <main className="mx-auto max-w-7xl px-5 py-10 sm:px-8">
      <p className="text-sm font-semibold text-blue-700">Mentor workspace</p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Mentor Dashboard</h1>
      {error ? <p className="mt-7 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p> : loading || !mentor ? <p className="mt-7 text-slate-500">Loading mentor dashboard…</p> : (
        <>
          <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-7">
            <h2 className="text-xl font-bold">Mentor Profile</h2>
            <div className="mt-5 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-5">
              <div><p className="text-slate-500">Name</p><p className="mt-1 font-semibold">{mentor.name}</p></div>
              <div><p className="text-slate-500">Employee ID</p><p className="mt-1 font-semibold">{mentor.employeeId}</p></div>
              <div><p className="text-slate-500">Department</p><p className="mt-1 font-semibold">{mentor.department}</p></div>
              <div><p className="text-slate-500">Designation</p><p className="mt-1 font-semibold">{mentor.designation}</p></div>
              <div><p className="text-slate-500">Assigned Students</p><p className="mt-1 text-2xl font-bold">{mentor.assignedStudentCount}</p></div>
            </div>
          </section>
          <div className="mt-6"><DashboardReportExport role="MENTOR" /></div>
          <section id="assigned-students" className="mt-6 scroll-mt-24 rounded-2xl border border-slate-200 bg-white p-6">
            <h2 className="text-xl font-bold">Assigned Students</h2>
            <p className="mt-2 text-sm text-slate-600">Open a student profile for internship and workplace review. Attendance percentages use applicable recorded working days.</p>
            <div className="mt-5 overflow-x-auto"><table className="w-full min-w-[680px] text-left text-sm"><thead><tr>{["UID", "Student Name", "Branch", "Company", "Attendance %"].map(label => <th key={label} className="p-3">{label}</th>)}</tr></thead>
              <tbody>{sortedStudents.map(student => <tr key={student.studentUserId} className="border-t border-slate-100"><td className="p-3 font-medium">{student.uid || "—"}</td><td className="p-3"><Link href={`/mentor/students/${student.studentUserId}`} className="font-semibold text-slate-950 hover:underline">{student.studentName || "Name unavailable"}</Link></td><td className="p-3">{student.branch || "—"}</td><td className="p-3">{student.company || "—"}</td><td className="p-3 font-semibold text-blue-700">{formatAttendancePercentage(student.attendance.percentage)}</td></tr>)}</tbody>
            </table>{!sortedStudents.length && <p className="p-3 text-slate-500">No students are currently assigned.</p>}</div>
          </section>
        </>
      )}
    </main>
  );
}

export default function MentorDashboardPage() {
  return <ProtectedRoute role="MENTOR"><AppFrame><MentorDashboardContent /></AppFrame></ProtectedRoute>;
}
