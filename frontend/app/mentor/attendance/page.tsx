"use client";

import { useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { ReportsCenter } from "@/components/ReportsCenter";
import { AttendanceAbsenceOverview } from "@/components/AttendanceAbsenceOverview";
import { LateAttendancePermissions } from "@/components/LateAttendancePermissions";

export default function MentorAttendancePage() {
  const [reports, setReports] = useState(false);

  return (
    <ProtectedRoute role="MENTOR">
      <AppFrame>
        <main className="mx-auto max-w-7xl px-5 py-10 sm:px-8">
          <div>
            <p className="text-sm font-semibold text-blue-700">Mentor workspace</p>
            <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950">Attendance &amp; Permissions</h1>
            <p className="mt-2 text-sm text-slate-600">
              Monitor daily attendance records, verify physical presence, and review late attendance permission requests.
            </p>
          </div>

          {/* SECTION 1: ATTENDANCE */}
          <section className="mt-8 space-y-4">
            <div className="border-b border-slate-200 pb-3">
              <h2 className="text-xl font-bold text-slate-900">Attendance</h2>
              <p className="mt-1 text-xs text-slate-500">
                Inspect daily attendance submissions, student physical presence verification, and absence statistics.
              </p>
            </div>
            <AttendanceAbsenceOverview role="MENTOR" />

            <details
              className="mt-6 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
              onToggle={(event) => setReports(event.currentTarget.open)}
            >
              <summary className="cursor-pointer font-semibold text-blue-700 hover:underline">
                Attendance reports and export
              </summary>
              {reports && (
                <div className="mt-4 border-t border-slate-100 pt-4">
                  <ReportsCenter role="MENTOR" attendanceOnly={true} />
                </div>
              )}
            </details>
          </section>

          {/* SECTION 2: LATE ATTENDANCE */}
          <section id="late-attendance-permissions" className="mt-12 scroll-mt-24 border-t border-slate-200 pt-8 space-y-4">
            <div className="border-b border-slate-200 pb-3">
              <h2 className="text-xl font-bold text-slate-900">Late Attendance Permissions</h2>
              <p className="mt-1 text-xs text-slate-500">
                Review, approve, or reject student requests to record attendance after standard working hours.
              </p>
            </div>
            <LateAttendancePermissions />
          </section>
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
