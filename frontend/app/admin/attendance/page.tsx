"use client";

import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { AttendanceAbsenceOverview } from "@/components/AttendanceAbsenceOverview";
import { BackButton } from "@/components/BackButton";

export default function AdminAttendancePage() {
  return (
    <ProtectedRoute role="ADMIN">
      <AppFrame>
        <main className="mx-auto max-w-6xl px-5 py-8 sm:px-8 sm:py-10">
          <BackButton fallbackHref="/admin/dashboard" fallbackLabel="Back to dashboard" />
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
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
