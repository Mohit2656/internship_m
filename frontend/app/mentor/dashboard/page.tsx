"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { MentorAccount } from "@/lib/mentor-accounts";
import { StudentAttendanceSummary } from "@/components/AttendanceAbsenceOverview";

type MentorNotification = {
  id: number;
  type?: string;
  title: string;
  message: string;
  actionUrl: string | null;
  readAt: string | null;
  createdAt: string;
};

type NotificationChangeEntry = {
  field: string;
  from: string;
  to: string;
};

const WEEKDAY_SHORT: Record<string, string> = {
  MONDAY: "Mon",
  TUESDAY: "Tue",
  WEDNESDAY: "Wed",
  THURSDAY: "Thu",
  FRIDAY: "Fri",
  SATURDAY: "Sat",
  SUNDAY: "Sun",
};

function formatChangeToken(value: string): string {
  const trimmed = value.trim();
  if (/^(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)(,\s*(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY))*$/i.test(trimmed)) {
    return trimmed
      .split(",")
      .map((part) => WEEKDAY_SHORT[part.trim().toUpperCase()] ?? part.trim())
      .join(", ");
  }
  return trimmed;
}

function notificationTypeBadge(item: MentorNotification): string {
  const t = item.type?.toUpperCase() ?? "";
  if (t === "INTERNSHIP_DETAILS_UPDATED") return "Details Updated";
  if (t === "INTERNSHIP_DETAILS_SUBMITTED") return "Internship Submitted";
  if (t === "ATTENDANCE_SUBMITTED") return "Attendance";
  if (t === "COMPANY_HOLIDAY_REQUESTED") return "Holiday Request";
  if (t.includes("WEEKLY")) return "Weekly Report";
  if (t.includes("MONTHLY")) return "Monthly Report";
  if (t.includes("PROFILE")) return "Profile Updated";
  return item.title.replace(/\s*\([^)]*\)\s*$/, "").trim() || "Workflow";
}

function parseWorkflowNotification(item: MentorNotification): {
  typeLabel: string;
  student: string;
  diffs: NotificationChangeEntry[];
  summary: string;
} {
  const titleMatch = item.title.match(/\(([^)]+)\)\s*$/);
  const msgStudentMatch = item.message.match(/^Student\s+([^\s]+)\s+/i);
  const student = titleMatch?.[1]?.trim() || msgStudentMatch?.[1]?.trim() || "Student";
  const typeLabel = notificationTypeBadge(item);

  const updateMatch = item.message.match(
    /^Student\s+.+?\s+updated\s+(?:Internship\s+\d+\s+)?(.+?)\s+on\s+\d{1,2}\s+[A-Za-z]{3}\s+\d{4}\./i
  );
  if (updateMatch) {
    const rawDiffs = updateMatch[1]
      .split(/,\s+(?=[A-Z][A-Za-z0-9 ()/-]*?\s+from\s+)/)
      .map((part) => part.trim())
      .filter(Boolean);
    const diffs: NotificationChangeEntry[] = [];
    for (const part of rawDiffs) {
      const pair = part.match(/^(.+?)\s+from\s+(.+?)\s+to\s+(.+)$/i);
      if (pair) {
        diffs.push({
          field: pair[1].trim(),
          from: formatChangeToken(pair[2]),
          to: formatChangeToken(pair[3]),
        });
      }
    }
    if (diffs.length > 0) {
      return { typeLabel, student, diffs, summary: "" };
    }
  }

  const cleanSummary = item.message
    .replace(/\s*Action required:.*$/i, "")
    .trim();
  return { typeLabel, student, diffs: [], summary: cleanSummary || item.message };
}

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
  const [pendingProgressCount, setPendingProgressCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([
      api.get<MentorAccount>("/mentor/profile"),
      api.get<StudentAttendanceSummary[]>("/mentor/attendance/students"),
      api
        .get<{ content: MentorNotification[] }>("/mentor/notifications", { params: { page: 0, size: 5 } })
        .then((r) => r.data?.content ?? [])
        .catch(() => [] as MentorNotification[]),
      api
        .get<any[]>("/mentor/weekly-progress")
        .then((r) => (Array.isArray(r.data) ? r.data.filter((w) => w.status === "PENDING_MENTOR_REVIEW").length : 0))
        .catch(() => 0),
    ])
      .then(([profileRes, assignedRes, notifs, progressCount]) => {
        setMentor(profileRes.data);
        setStudents(assignedRes.data);
        setNotifications(notifs);
        setPendingProgressCount(progressCount);
      })
      .catch(() => setError("Unable to load your mentor dashboard."))
      .finally(() => setLoading(false));
  }, []);

  const totalAssigned = mentor?.assignedStudentCount ?? students.length;
  const attendanceInternships = students.filter((s) => s.internshipId != null).length;
  const companiesCount = new Set(students.map((s) => s.company).filter(Boolean)).size;
  const pendingAttendanceCount = students.reduce((sum, s) => sum + (s.reviewSummary?.pending ?? 0), 0);
  const totalPendingReviews = pendingAttendanceCount + pendingProgressCount;

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
            <div className="flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div>
                <div className="flex items-center justify-between">
                  <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Assigned Students</p>
                  <span className="rounded-full bg-blue-50 px-2 py-0.5 text-xs font-semibold text-blue-700">Active</span>
                </div>
                <p className="mt-2 text-2xl font-bold text-slate-950">{totalAssigned}</p>
              </div>
              <div className="mt-3 flex items-center justify-between gap-2 text-xs text-slate-500">
                <span>Across {companiesCount} {companiesCount === 1 ? "company" : "companies"}</span>
                <Link
                  href="/mentor/students"
                  data-button-variant="outline"
                  className="ui-button !px-3 !py-1.5 !text-xs whitespace-nowrap"
                >
                  View Students →
                </Link>
              </div>
            </div>

            {/* Card 2: Attendance Records */}
            <div className="flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div>
                <div className="flex items-center justify-between">
                  <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Attendance Records</p>
                  <span className="rounded-full bg-blue-50 px-2 py-0.5 text-xs font-semibold text-blue-700">Available</span>
                </div>
                <p className="mt-2 text-2xl font-bold text-slate-950">{attendanceInternships}</p>
              </div>
              <div className="mt-3 flex items-center justify-between gap-2 text-xs text-slate-500">
                <span>Internships tracked independently</span>
                <Link
                  href="/mentor/attendance"
                  data-button-variant="outline"
                  className="ui-button !px-3 !py-1.5 !text-xs whitespace-nowrap"
                >
                  Review →
                </Link>
              </div>
            </div>

            {/* Card 3: Pending Reviews */}
            <div className="flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div>
                <div className="flex items-center justify-between">
                  <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Pending Reviews</p>
                  {totalPendingReviews > 0 ? (
                    <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-800 border border-amber-200">
                      Action
                    </span>
                  ) : (
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600">
                      Up to date
                    </span>
                  )}
                </div>
                <p className="mt-2 text-2xl font-bold text-slate-950">{totalPendingReviews}</p>
              </div>
              <div className="mt-3 flex items-center justify-between gap-2 text-xs text-slate-500">
                <span>{pendingAttendanceCount} attendance · {pendingProgressCount} weekly</span>
                <Link
                  href={pendingAttendanceCount > 0 ? "/mentor/attendance" : "/mentor/progress"}
                  data-button-variant="outline"
                  className="ui-button !px-3 !py-1.5 !text-xs whitespace-nowrap"
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
          {(pendingAttendanceCount > 0 || pendingProgressCount > 0) && (
            <section
              className="rounded-xl border border-amber-200 bg-amber-50/70 p-4 shadow-sm"
              aria-label="Action required"
            >
              <div className="flex items-center justify-between border-b border-amber-200/60 pb-2">
                <h2 className="text-xs font-bold uppercase tracking-wider text-amber-900">
                  Action Required
                </h2>
                <span className="text-[11px] font-semibold text-amber-800">
                  {(pendingAttendanceCount > 0 ? 1 : 0) + (pendingProgressCount > 0 ? 1 : 0)} items pending
                </span>
              </div>

              <div className="mt-2.5 flex flex-col gap-2">
                {pendingAttendanceCount > 0 && (
                  <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-200/80 bg-white px-3.5 py-2.5 text-sm shadow-sm">
                    <div className="flex items-center gap-2.5">
                      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-amber-100 text-xs font-bold text-amber-800">
                        !
                      </span>
                      <p className="text-xs font-medium text-slate-800">
                        <span className="font-semibold text-slate-900">
                          {pendingAttendanceCount} attendance {pendingAttendanceCount === 1 ? "submission" : "submissions"}
                        </span>{" "}
                        awaiting mentor verification.
                      </p>
                    </div>
                    <Link
                      href="/mentor/attendance"
                      data-button-variant="outline"
                      className="ui-button !px-3 !py-1.5 !text-xs whitespace-nowrap"
                    >
                      Review Attendance →
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
                    <Link
                      href="/mentor/progress"
                      data-button-variant="outline"
                      className="ui-button !px-3 !py-1.5 !text-xs whitespace-nowrap"
                    >
                      Review Progress →
                    </Link>
                  </div>
                )}

              </div>
            </section>
          )}

          {/* Recent Workflow Notifications - Structured Table */}
          {notifications.length > 0 && (
            <section className="rounded-xl border border-slate-200 bg-white p-4 sm:p-5 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
                <div>
                  <h2 className="text-base font-bold text-slate-900">Recent Workflow Notifications</h2>
                  <p className="mt-0.5 text-xs text-slate-500">Latest internship, attendance, and report updates from your assigned students.</p>
                </div>
                <Link
                  href="/mentor/notifications"
                  data-button-variant="outline"
                  className="ui-button !px-3 !py-1.5 !text-xs whitespace-nowrap"
                >
                  All Notifications →
                </Link>
              </div>
              <div className="mt-3 overflow-x-auto">
                <table className="w-full min-w-[720px] border-collapse text-left text-xs">
                  <thead>
                    <tr className="border-b border-slate-200 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                      <th className="py-2.5 pr-3 whitespace-nowrap">Type</th>
                      <th className="py-2.5 px-3 whitespace-nowrap">Student</th>
                      <th className="py-2.5 px-3">Changes</th>
                      <th className="py-2.5 px-3 whitespace-nowrap">Time</th>
                      <th className="py-2.5 pl-3 text-right whitespace-nowrap">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {notifications.map((item) => {
                      const parsed = parseWorkflowNotification(item);
                      const href = item.actionUrl?.startsWith("/mentor/") ? item.actionUrl : "/mentor/notifications";
                      return (
                        <tr key={item.id} className="align-top transition-colors hover:bg-slate-50/70">
                          <td className="py-3 pr-3 whitespace-nowrap">
                            <span
                              className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${
                                item.readAt
                                  ? "bg-slate-100 text-slate-700"
                                  : "bg-blue-50 text-blue-700 border border-blue-200"
                              }`}
                            >
                              {!item.readAt && <span className="h-1.5 w-1.5 rounded-full bg-blue-600" aria-hidden="true" />}
                              {parsed.typeLabel}
                            </span>
                          </td>
                          <td className="py-3 px-3 font-semibold text-slate-900 whitespace-nowrap">
                            {parsed.student}
                          </td>
                          <td className="py-3 px-3 text-slate-700">
                            {parsed.diffs.length > 0 ? (
                              <ul className="space-y-1">
                                {parsed.diffs.map((diff, idx) => (
                                  <li key={idx} className="leading-snug">
                                    <span className="font-semibold text-slate-900">{diff.field}:</span>{" "}
                                    <span className="text-slate-500">{diff.from}</span>{" "}
                                    <span className="text-slate-400">→</span>{" "}
                                    <span className="font-medium text-slate-900">{diff.to}</span>
                                  </li>
                                ))}
                              </ul>
                            ) : (
                              <p className="leading-relaxed text-slate-700">{parsed.summary}</p>
                            )}
                          </td>
                          <td className="py-3 px-3 whitespace-nowrap text-slate-500">
                            <span className="block font-medium text-slate-700">{formatDateTime(item.createdAt)}</span>
                            <span className={`text-[11px] ${item.readAt ? "text-slate-400" : "font-semibold text-blue-700"}`}>
                              {item.readAt ? "Read" : "Unread"}
                            </span>
                          </td>
                          <td className="py-3 pl-3 text-right whitespace-nowrap">
                            <Link
                              href={href}
                              data-button-variant="outline"
                              className="ui-button !px-2.5 !py-1 !text-xs whitespace-nowrap"
                            >
                              Review →
                            </Link>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </section>
          )}
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
