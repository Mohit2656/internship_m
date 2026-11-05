"use client";

import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { AttendanceAbsenceOverview } from "@/components/AttendanceAbsenceOverview";
import { ReportsCenter } from "@/components/ReportsCenter";

export default function AdminAttendancePage() {
  return (
    <ProtectedRoute role="ADMIN">
      <AppFrame>
        <main className="mx-auto max-w-6xl px-5 py-8 sm:px-8 sm:py-10">
          <Link href="/admin/dashboard" className="text-sm font-semibold text-blue-700 hover:underline">
            ← Back to dashboard
          </Link>
          <div className="mt-4">
            <h1 className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Student Attendance
            </h1>
            <p className="mt-2 text-slate-600 dark:text-slate-400">
              Monitor daily attendance records, GPS locations, photo liveness checks, automatic absence tracking, and mentor approvals across all registered internships.
            </p>
          </div>

          <div className="mt-6">
            <AttendanceAbsenceOverview role="ADMIN" />
          </div>

          <div className="mt-10 border-t border-slate-200 pt-8 dark:border-slate-800">
            <h2 className="text-xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Attendance Reports & Exports
            </h2>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Generate and download institutional attendance reports and compliance summaries.
            </p>
            <div className="mt-4">
              <ReportsCenter role="ADMIN" attendanceOnly={true} />
            </div>
          </div>
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
