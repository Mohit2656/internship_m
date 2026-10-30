"use client";
import { useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { ReportsCenter } from "@/components/ReportsCenter";
import { AttendanceAbsenceOverview } from "@/components/AttendanceAbsenceOverview";
export default function Page() {
  const [reports, setReports] = useState(false);
  return <ProtectedRoute role="MENTOR"><AppFrame><main className="mx-auto max-w-7xl px-5 py-10 sm:px-8"><h1 className="text-3xl font-bold">Attendance approval</h1><p className="mt-2 text-sm text-slate-600">Select an assigned student to inspect submitted evidence and record your decision.</p><AttendanceAbsenceOverview role="MENTOR" /><details className="mt-6" onToggle={event => setReports(event.currentTarget.open)}><summary className="cursor-pointer font-semibold text-blue-700">Attendance reports and export</summary>{reports && <ReportsCenter role="MENTOR" attendanceOnly={true} />}</details></main></AppFrame></ProtectedRoute>;
}
