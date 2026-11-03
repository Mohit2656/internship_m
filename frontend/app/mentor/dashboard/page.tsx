"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { MentorAccount } from "@/lib/mentor-accounts";
import { StudentAttendanceSummary } from "@/components/AttendanceAbsenceOverview";
import { DashboardReportExport } from "@/components/DashboardReportExport";

type MentorNotification = {
  id: number;
  title: string;
  message: string;
  actionUrl: string | null;
  readAt: string | null;
  createdAt: string;
};

type LateRequest = {
  id: number;
  status: "PENDING" | "APPROVED" | "REJECTED";
};

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleDateString(undefined, {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return value;
  }
}

function MentorDashboardContent() {
  const [mentor, setMentor] = useState<MentorAccount | null>(null);
  const [students, setStudents] = useState<StudentAttendanceSummary[]>([]);
  const [notifications, setNotifications] = useState<MentorNotification[]>([]);
  const [pendingLateCount, setPendingLateCount] = useState(0);
  const [pendingProgressCount, setPendingProgressCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([
      api.get<MentorAccount>("/mentor/profile"),
      api.get<StudentAttendanceSummary[]>("/mentor/attendance/students"),
      api
        .get<{ content: MentorNotification[] }>("/mentor/notifications", { params: { page: 0, size: 4 } })
        .then((r) => r.data?.content ?? [])
        .catch(() => [] as MentorNotification[]),
      api
        .get<LateRequest[]>("/mentor/attendance/late-permissions")
        .then((r) => (Array.isArray(r.data) ? r.data.filter((req) => req.status === "PENDING").length : 0))
        .catch(() => 0),
      api
        .get<any[]>("/mentor/weekly-progress")
        .then((r) => (Array.isArray(r.data) ? r.data.filter((w) => w.status === "PENDING_MENTOR_REVIEW").length : 0))
        .catch(() => 0),
    ])
      .then(([profileRes, assignedRes, notifs, lateCount, progressCount]) => {
        setMentor(profileRes.data);
        setStudents(assignedRes.data);
        setNotifications(notifs);
        setPendingLateCount(lateCount);
        setPendingProgressCount(progressCount);
      })
      .catch(() => setError("Unable to load your mentor dashboard."))
      .finally(() => setLoading(false));
  }, []);

  const totalAssigned = mentor?.assignedStudentCount ?? students.length;
  const lowAttendanceStudents = students.filter(
    (s) => (s.attendance?.percentage ?? 100) < 75
  );
  const companiesCount = new Set(students.map((s) => s.company).filter(Boolean)).size;

  return (
    <main className="mx-auto max-w-7xl px-5 py-8 sm:px-8">
      {/* Header - Compact Spacing & Concise Description */}
      <div>
        <p className="text-xs font-bold uppercase tracking-wider text-blue-700">Faculty Mentor</p>
        <h1 className="mt-1 text-2xl sm:text-3xl font-bold tracking-tight text-slate-950">
          Welcome back, {mentor?.name || "Mentor"}!
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          Manage students, attendance, and progress.
        </p>
      </div>

      {error ? (
        <p className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>
      ) : loading || !mentor ? (
        <p className="mt-6 text-slate-500">Loading mentor dashboard…</p>
      ) : (
        <div className="mt-5 space-y-5">
          {/* Summary Metric Cards - Compact Padding */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {/* Card 1: Assigned Students */}
            <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex items-center justify-between">
                <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Assigned Students</p>
                <span className="rounded-full bg-blue-50 px-2 py-0.5 text-xs font-semibold text-blue-700">Active</span>
              </div>
              <p className="mt-2 text-2xl font-bold text-slate-950">{totalAssigned}</p>
              <div className="mt-2 flex items-center justify-between text-xs text-slate-500">
                <span>Across {companiesCount} companies</span>
                <Link href="/mentor/students" className="font-semibold text-blue-700 hover:underline">
                  View table →
                </Link>
              </div>
            </div>

            {/* Card 2: Attendance Status */}
            <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex items-center justify-between">
                <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Attendance Attention</p>
                {lowAttendanceStudents.length > 0 ? (
                  <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-800 border border-amber-200">
                    {lowAttendanceStudents.length} Low
                  </span>
                ) : (
                  <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-semibold text-emerald-700">
                    Good
                  </span>
                )}
              </div>
              <p className="mt-2 text-2xl font-bold text-slate-950">{lowAttendanceStudents.length}</p>
              <div className="mt-2 flex items-center justify-between text-xs text-slate-500">
                <span>Below 75% threshold</span>
                <Link href="/mentor/attendance" className="font-semibold text-blue-700 hover:underline">
                  Review →
                </Link>
              </div>
            </div>

            {/* Card 3: Pending Late Requests */}
            <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex items-center justify-between">
                <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Late Attendance</p>
                {pendingLateCount > 0 ? (
                  <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-800 border border-amber-200">
                    Action
                  </span>
                ) : (
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600">
                    Up to date
                  </span>
                )}
              </div>
              <p className="mt-2 text-2xl font-bold text-slate-950">{pendingLateCount}</p>
              <div className="mt-2 flex items-center justify-between text-xs text-slate-500">
                <span>Pending requests</span>
                <Link
                  href="/mentor/attendance#late-attendance-permissions"
                  className="font-semibold text-blue-700 hover:underline"
                >
                  Manage →
                </Link>
              </div>
            </div>

            {/* Card 4: Mentor Profile Summary */}
            <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Faculty Profile</p>
              <p className="mt-1.5 text-base font-bold text-slate-900 truncate">{mentor.name}</p>
              <p className="text-xs text-slate-500 truncate">
                {mentor.designation} · {mentor.department}
              </p>
              <div className="mt-2 pt-2 border-t border-slate-100 text-xs text-slate-500">
                <span>ID: </span>
                <span className="font-semibold text-slate-700">{mentor.employeeId}</span>
              </div>
            </div>
          </div>

          {/* Action Required Section - Compact & No Empty Space */}
          {(lowAttendanceStudents.length > 0 || pendingLateCount > 0 || pendingProgressCount > 0) && (
            <section
              className="rounded-xl border border-amber-200 bg-amber-50/70 p-4 shadow-sm"
              aria-label="Action required"
            >
              <div className="flex items-center justify-between border-b border-amber-200/60 pb-2">
                <h2 className="text-xs font-bold uppercase tracking-wider text-amber-900">
                  Action Required
                </h2>
                <span className="text-[11px] font-semibold text-amber-800">
                  {(pendingLateCount > 0 ? 1 : 0) + (lowAttendanceStudents.length > 0 ? 1 : 0) + (pendingProgressCount > 0 ? 1 : 0)} items pending
                </span>
              </div>

              <div className="mt-2.5 flex flex-col gap-2">
                {pendingLateCount > 0 && (
                  <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-200/80 bg-white px-3.5 py-2.5 text-sm shadow-sm">
                    <div className="flex items-center gap-2.5">
                      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-amber-100 text-xs font-bold text-amber-800">
                        !
                      </span>
                      <p className="text-xs font-medium text-slate-800">
                        <span className="font-semibold text-slate-900">
                          {pendingLateCount} late attendance {pendingLateCount === 1 ? "request" : "requests"}
                        </span>{" "}
                        awaiting review.
                      </p>
                    </div>
                    <Link
                      href="/mentor/attendance#late-attendance-permissions"
                      className="ui-button text-xs whitespace-nowrap"
                    >
                      Review Requests
                    </Link>
                  </div>
                )}

                {pendingProgressCount > 0 && (
                  <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-200/80 bg-white px-3.5 py-2.5 text-sm shadow-sm">
                    <div className="flex items-center gap-2.5">
                      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-amber-100 text-xs font-bold text-amber-800">
                        !
                      </span>
                      <p className="text-xs font-medium text-slate-800">
                        <span className="font-semibold text-slate-900">
                          {pendingProgressCount} progress {pendingProgressCount === 1 ? "report" : "reports"}
                        </span>{" "}
                        awaiting review.
                      </p>
                    </div>
                    <Link href="/mentor/progress" className="ui-button text-xs whitespace-nowrap">
                      Review Progress
                    </Link>
                  </div>
                )}

                {lowAttendanceStudents.length > 0 && (
                  <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-200/80 bg-white px-3.5 py-2.5 text-sm shadow-sm">
                    <div className="flex items-center gap-2.5">
                      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-amber-100 text-xs font-bold text-amber-800">
                        !
                      </span>
                      <p className="text-xs font-medium text-slate-800">
                        <span className="font-semibold text-slate-900">
                          {lowAttendanceStudents.length} students below 75% attendance.
                        </span>
                      </p>
                    </div>
                    <Link href="/mentor/attendance" className="ui-button text-xs whitespace-nowrap">
                      View Attendance
                    </Link>
                  </div>
                )}
              </div>
            </section>
          )}



          {/* Recent Notifications - Compact */}
          {notifications.length > 0 && (
            <section className="rounded-xl border border-slate-200 bg-white p-4 sm:p-5 shadow-sm">
              <div className="flex items-center justify-between gap-3 border-b border-slate-100 pb-3">
                <h2 className="text-base font-bold text-slate-900">Recent Workflow Notifications</h2>
                <Link href="/mentor/notifications" className="text-xs font-semibold text-blue-700 hover:underline">
                  All notifications →
                </Link>
              </div>
              <ul className="divide-y divide-slate-100">
                {notifications.map((item) => (
                  <li key={item.id} className="py-2.5">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <Link
                        href={item.actionUrl?.startsWith("/mentor/") ? item.actionUrl : "/mentor/notifications"}
                        className="text-xs font-semibold text-blue-700 hover:underline"
                      >
                        {item.title}
                      </Link>
                      <span className="text-[11px] text-slate-400">
                        {item.readAt ? "Read" : "Unread"} · {formatDateTime(item.createdAt)}
                      </span>
                    </div>
                    <p className="mt-0.5 text-xs text-slate-600 line-clamp-1">{item.message}</p>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {/* Institutional Reports Export - Compact */}
          <div className="rounded-xl border border-slate-200 bg-white p-4 sm:p-5 shadow-sm">
            <h2 className="text-base font-bold text-slate-900">Institutional Report Generation</h2>
            <p className="text-xs text-slate-500 mb-3">
              Export attendance and progress data for departmental records.
            </p>
            <DashboardReportExport role="MENTOR" />
          </div>
        </div>
      )}
    </main>
  );
}

export default function MentorDashboardPage() {
  return (
    <ProtectedRoute role="MENTOR">
      <AppFrame>
        <MentorDashboardContent />
      </AppFrame>
    </ProtectedRoute>
  );
}
