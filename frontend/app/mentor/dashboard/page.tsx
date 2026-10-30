"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { MentorAccount } from "@/lib/mentor-accounts";
import { MentorStudentAttendance } from "@/lib/attendance";
import { AttendanceAbsenceOverview } from "@/components/AttendanceAbsenceOverview";
import { MonthlyProgressReportList } from "@/components/MonthlyProgressReportList";
import { DashboardReportExport } from "@/components/DashboardReportExport";
import { LateAttendancePermissions } from "@/components/LateAttendancePermissions";

function MentorDashboardContent() {
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
          <LateAttendancePermissions />
          <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-7"><h2 className="text-xl font-bold">Weekly Progress Review</h2><p className="mt-2 text-sm text-slate-600">Monitor generated weeks and review progress submitted by your assigned students.</p><Link href="/mentor/weekly-progress" className="ui-button mt-5 inline-block text-sm">Review Weekly Progress</Link></section>
          <AttendanceAbsenceOverview role="MENTOR" />
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
