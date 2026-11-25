"use client";

import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { AttendanceAbsenceOverview } from "@/components/AttendanceAbsenceOverview";
import { LateAttendancePermissions } from "@/components/LateAttendancePermissions";
import { AttendanceHolidayExceptions } from "@/components/AttendanceHolidayExceptions";

export default function MentorAttendancePage() {
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

          {/* SECTION 3: COMPANY HOLIDAY REQUESTS */}
          <section id="company-holiday-exceptions" className="mt-12 scroll-mt-24 border-t border-slate-200 pt-8 space-y-4">
            <div className="border-b border-slate-200 pb-3">
              <h2 className="text-xl font-bold text-slate-900">Company Holiday Exceptions</h2>
              <p className="mt-1 text-xs text-slate-500">
                Review, approve, or reject company-specific holiday declarations submitted by students.
              </p>
            </div>
            <AttendanceHolidayExceptions />
          </section>
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
