"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { MentorRecordDelete } from "@/components/MentorRecordDelete";
import { MentorAccount } from "@/lib/mentor-accounts";
import { MentorStudentAttendance, attendanceLabel } from "@/lib/attendance";
import { MonthlyProgressReportList } from "@/components/MonthlyProgressReportList";
import { DashboardReportExport } from "@/components/DashboardReportExport";
import { AttendanceLocations } from "@/components/AttendanceLocations";

function MentorDashboardContent() {
  const [notice, setNotice] = useState("");
  const [mentor, setMentor] = useState<MentorAccount | null>(null);
  const [students, setStudents] = useState<MentorStudentAttendance[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([
      api.get<MentorAccount>("/mentor/profile"),
      api.get<MentorStudentAttendance[]>("/mentor/attendance/today")
    ]).then(([profile, assigned]) => {
      setMentor(profile.data); setStudents(assigned.data);
    }).catch(() => setError("Unable to load your mentor dashboard."))
      .finally(() => setLoading(false));
  }, []);

  return (
    <main className="mx-auto max-w-5xl px-5 py-10 sm:px-8">
      <p className="text-sm font-semibold text-blue-700">Mentor workspace</p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Mentor Dashboard</h1>
      <div className="mt-5"><DashboardReportExport role="MENTOR" /></div>
      {notice && <p role="status" className="mt-5 text-sm text-emerald-700">{notice}</p>}
      {error ? <p className="mt-7 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p> : loading || !mentor ? <p className="mt-7 text-slate-500">Loading mentor dashboard…</p> : (
        <>
          <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-7">
            <p className="text-sm text-slate-500">Welcome</p>
            <h2 className="mt-1 text-2xl font-bold text-slate-950">{mentor.name}</h2>
            <div className="mt-5 grid gap-4 text-sm sm:grid-cols-4">
              <div><p className="text-slate-500">Employee ID</p><p className="mt-1 font-semibold">{mentor.employeeId}</p></div>
              <div><p className="text-slate-500">Department</p><p className="mt-1 font-semibold">{mentor.department}</p></div>
              <div><p className="text-slate-500">Designation</p><p className="mt-1 font-semibold">{mentor.designation}</p></div>
              <div><p className="text-slate-500">Assigned Students</p><p className="mt-1 text-2xl font-bold">{students.length}</p></div>
            </div>
          </section>
          <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-7">
            <h2 className="text-xl font-bold">Assigned Students</h2>
            <p className="mt-2 text-sm text-slate-600">Attendance appears automatically for students currently assigned to you.</p>
            {students.length === 0 ? <p className="mt-6 text-sm text-slate-500">No students are currently assigned.</p> : <div className="mt-5 overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b text-xs uppercase tracking-wide text-slate-500"><tr><th className="pb-3">Student</th><th className="pb-3">Company</th><th className="pb-3">Mode</th><th className="pb-3">Today</th><th className="pb-3">Today&apos;s Attendance</th></tr></thead><tbody>{students.map(student => <tr key={student.studentUserId} className="border-b border-slate-100 last:border-0"><td className="py-4"><Link href={`/mentor/students/${student.studentUserId}`} className="font-semibold text-blue-700 hover:underline">{student.name || "Profile incomplete"}</Link><p className="mt-1 text-xs text-slate-500">{student.uid || "UID unavailable"}</p></td><td className="py-4">{student.companyName || "—"}</td><td className="py-4">{student.internshipModeLabel || "—"}</td><td className="py-4">{student.todayWorkMode === "OFFICE" ? "Office" : student.todayWorkMode === "REMOTE" ? "Remote" : "Not configured"}</td><td className="py-4 font-medium">{student.todayAttendance ? attendanceLabel(student.todayAttendance) : "Not Submitted"}{student.todayAttendance && <AttendanceLocations evidence={student.todayAttendance.evidence} />}{student.todayAttendance && <div className="mt-2"><MentorRecordDelete kind="attendance" id={student.todayAttendance.id} description={`${student.name ?? "Student"} (${student.uid ?? student.studentUserId}) - attendance date ${student.todayAttendance.attendanceDate}`} onDeleted={async () => { setNotice("Attendance record deleted."); const response = await api.get<MentorStudentAttendance[]>("/mentor/attendance/today"); setStudents(response.data); }} /></div>}</td></tr>)}</tbody></table></div>}
          </section>
          <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-7"><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Student records</p><h2 className="mt-3 text-xl font-bold">Internship Documents</h2><p className="mt-2 text-sm text-slate-600">Check upload status and securely view documents for your assigned students.</p><Link href="/mentor/documents" className="ui-button mt-5 inline-block text-sm">View Internship Documents</Link></section>
          <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-7"><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Internship records</p><h2 className="mt-3 text-xl font-bold">Monthly Progress Reports</h2><p className="mt-2 text-sm text-slate-600">Review submitted reports for your currently assigned students.</p><Link href="/mentor/monthly-progress" className="ui-button mt-5 inline-block text-sm">View Monthly Progress</Link><MonthlyProgressReportList scope="mentor" /></section>
        </>
      )}
    </main>
  );
}

export default function MentorDashboardPage() {
  return <ProtectedRoute role="MENTOR"><AppFrame><MentorDashboardContent /></AppFrame></ProtectedRoute>;
}
