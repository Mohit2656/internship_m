"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/monthly-progress";
import { AppNotification, NotificationInbox, NotificationRole, syncNotificationCount } from "@/lib/notifications";

const eventLabels: Record<string, string> = {
  RESUME_UPLOADED: "Resume uploaded", RESUME_UPDATED: "Resume updated", RESUME_CORRECTED: "Resume corrected", RESUME_PARSED: "Resume analysis complete",
  INTERNSHIP_COMPLETED: "Internship completed", INTERNSHIP_REOPENED: "Internship reopened",
  OPPORTUNITY_MATCH: "Opportunity recommendation", ATTENDANCE_APPROVED: "Attendance approved", ATTENDANCE_REJECTED: "Attendance rejected", ATTENDANCE_MANUAL_REVIEW: "Attendance review requested",
  WEEKLY_REPORT_SUBMITTED: "Weekly report submitted", WEEKLY_REPORT_APPROVED: "Weekly report approved", WEEKLY_REPORT_REJECTED: "Weekly report rejected", WEEKLY_REPORT_REVIEWED: "Weekly report reviewed", WORKPLACE_REJECTED: "Workplace feedback", REPORT_APPROVED: "Report approved", REPORT_SUBMITTED: "Report submitted",
};
const internshipApprovalTypes = new Set(["INTERNSHIP_MENTOR_APPROVED", "INTERNSHIP_IIIC_APPROVED"]);

export function RecentNotifications({ role = "ADMIN" }: { role?: NotificationRole }) {
  const path = `/${role.toLowerCase()}/notifications`;
  const [inbox, setInbox] = useState<NotificationInbox | null>(null);
  const [error, setError] = useState("");
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    api.get<NotificationInbox>(path, { signal: abort.signal }).then(({ data }) => {
      if (!abort.signal.aborted) { setInbox(data); setError(""); }
    }).catch(() => { if (!abort.signal.aborted) setError("Recent activity could not be loaded."); });
    return () => abort.abort();
  }, [path, refresh]);
  return <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 sm:p-6">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h2 className="text-lg font-bold">Recent activity{inbox ? ` · ${inbox.unreadCount} unread` : ""}</h2>
      <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Recent activity actions">
        <button type="button" data-button-variant="text" className="ui-button" onClick={() => setRefresh(value => value + 1)}>Refresh activity</button>
        <Link href={path} data-button-variant="text" className="ui-button">All notifications</Link>
      </div>
    </div>
    {error ? <p role="alert" className="mt-4 text-sm text-red-700">{error}</p> : !inbox ? <p className="mt-4 text-sm text-slate-500">Loading activity...</p> : inbox.content.length === 0 ? <p className="mt-4 text-sm text-slate-500">No activity yet. Student resume updates will appear here.</p> : <ul className="mt-4 divide-y divide-slate-100">{inbox.content.slice(0, 3).map(item => <li key={item.id} className="py-3"><div className="flex flex-wrap justify-between gap-2"><Link href={item.actionUrl.startsWith(`/${role.toLowerCase()}/`) ? item.actionUrl : path} className="text-sm font-semibold text-blue-700 hover:underline">{item.title}</Link><span className="text-xs text-slate-500">{item.readAt ? "Read" : "Unread"} · <time dateTime={item.createdAt}>{formatDateTime(item.createdAt)}</time></span></div><p className="mt-2 text-sm text-slate-600">{item.message}</p></li>)}</ul>}
  </section>;
}

export function NotificationsContent({ role = "STUDENT" }: { role?: NotificationRole } = {}) {
  const router = useRouter();
  const rolePath = `/${role.toLowerCase()}`;
  const notificationsPath = `${rolePath}/notifications`;
  const [inbox, setInbox] = useState<NotificationInbox | null>(null);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setError("");
    const refresh = () => { void api.get<NotificationInbox>(notificationsPath, { params: { page }, signal: abort.signal })
      .then(({ data }) => { if (!abort.signal.aborted) { setInbox(data); syncNotificationCount(); } })
      .catch(() => { if (!abort.signal.aborted) setError("Notifications could not be loaded. Please try again."); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); }); };
    refresh(); const timer = window.setInterval(refresh, 30_000); window.addEventListener("focus", refresh);
    return () => { abort.abort(); window.clearInterval(timer); window.removeEventListener("focus", refresh); };
  }, [notificationsPath, page, revision]);
  async function markRead(notification?: AppNotification, open = false) {
    if (busy) return; setBusy(true); setError("");
    try {
      if (!notification || !notification.readAt) await api.patch(notification ? `${notificationsPath}/${notification.id}/read` : `${notificationsPath}/read-all`);
      syncNotificationCount(); setRevision(value => value + 1);
      if (open && notification && notification.actionUrl.startsWith(`${rolePath}/`)) router.push(notification.actionUrl);
    } catch { setError("Notifications could not be updated. Please try again."); }
    finally { setBusy(false); }
  }
  return <main className="mx-auto max-w-4xl px-5 py-10 sm:px-8">
    <Link href={`${rolePath}/dashboard`} className="text-sm font-semibold text-blue-700 hover:underline">Back to dashboard</Link>
    <div className="mt-6 flex flex-wrap items-center justify-between gap-4"><div><h1 className="text-3xl font-bold tracking-tight text-slate-950">Notifications</h1><p className="mt-2 text-sm leading-6 text-slate-600">{role === "ADMIN" ? "Student resume updates and review activity in one place." : role === "MENTOR" ? "Attendance and progress submissions from your assigned students in one place." : "Internship approvals, new opportunities, report feedback, and deadline reminders in one place."}</p></div><button type="button" data-button-variant="outline" disabled={loading || busy || !inbox?.unreadCount} onClick={() => void markRead()} className="text-sm">Mark all as read</button></div>
    {error && <p role="alert" className="mt-5 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}<button type="button" data-button-variant="text" onClick={() => setRevision(value => value + 1)} className="ml-3">Retry</button></p>}
    {loading ? <p role="status" className="mt-8 rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">Loading notifications...</p> : inbox && <>
      <p role="status" className="mt-5 text-sm font-semibold text-blue-700">{inbox.unreadCount} unread</p>
      {inbox.content.length === 0 ? <section className="mt-5 rounded-2xl border border-slate-200 bg-white px-6 py-12 text-center"><h2 className="font-semibold text-slate-900">No notifications yet</h2><p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">{role === "ADMIN" ? "Student resume updates and review activity will appear here." : role === "MENTOR" ? "Attendance and progress submissions from your assigned students will appear here." : "Internship approvals, new opportunities, report feedback, and deadline reminders will appear here."}</p><Link href={role === "ADMIN" ? "/admin/students" : role === "MENTOR" ? "/mentor/attendance" : "/student/opportunities"} className="mt-5 inline-block text-sm font-semibold text-blue-700 hover:underline">{role === "ADMIN" ? "View students" : role === "MENTOR" ? "View attendance" : "Browse opportunities"}</Link></section> : <div className="mt-5 space-y-4" aria-busy={busy}>{inbox.content.map(item => <article key={item.id} className={`rounded-2xl border p-5 sm:p-6 ${item.readAt ? "border-slate-200 bg-white" : "border-blue-200 bg-blue-50/60"}`}>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-blue-700">{item.type === "INTERNSHIP_MENTOR_APPROVED" ? "Faculty mentor approval" : item.type === "INTERNSHIP_IIIC_APPROVED" ? "IIIC coordinator approval" : item.type === "ATTENDANCE_SUBMITTED" ? "Attendance submitted" : item.type === "OPPORTUNITY_PUBLISHED" ? "New opportunity" : item.type === "REPORT_REJECTED" ? "Report feedback" : eventLabels[item.type] ?? item.type.replaceAll("_", " ").toLowerCase()}</p>
        <div className="flex flex-wrap items-start justify-between gap-3"><h2 className="min-w-0 break-words text-lg font-bold text-slate-950">{item.title}</h2><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${item.readAt ? "bg-slate-100 text-slate-600" : "bg-blue-700 text-white"}`}>{item.readAt ? "Read" : "Unread"}</span></div>
        <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-6 text-slate-700">{item.message}</p>
        {item.rejectionReason && <div className="mt-4 rounded-xl border border-red-100 bg-red-50 p-4 text-sm text-red-800"><p className="font-bold">Rejection reason</p><p className="mt-2 whitespace-pre-wrap break-words">{item.rejectionReason}</p></div>}
        <p className="mt-4 text-xs leading-5 text-slate-500">{internshipApprovalTypes.has(item.type) ? "Approved: " : item.type === "ATTENDANCE_SUBMITTED" ? "Submitted: " : item.type === "OPPORTUNITY_PUBLISHED" ? "Published: " : item.type === "REPORT_REJECTED" ? "Rejected: " : "Notified: "}<time dateTime={item.createdAt}>{formatDateTime(item.createdAt)}</time>{item.mentorName && <> · Faculty mentor: {item.mentorName}</>}</p>
        <div className="mt-5 flex flex-wrap gap-3"><button type="button" disabled={busy} onClick={() => void markRead(item, true)} className="text-sm">{internshipApprovalTypes.has(item.type) ? "View internship" : item.type === "ATTENDANCE_SUBMITTED" ? "View attendance" : item.type === "OPPORTUNITY_PUBLISHED" ? "View opportunity" : item.type === "REPORT_REJECTED" ? "Open report" : "Open task"}</button>{!item.readAt && <button type="button" data-button-variant="outline" disabled={busy} onClick={() => void markRead(item)} className="text-sm">Mark as read</button>}</div>
      </article>)}</div>}
      {inbox.totalPages > 1 && <div className="mt-6 flex items-center justify-between gap-3 text-sm"><button type="button" data-button-variant="outline" disabled={page === 0 || busy} onClick={() => setPage(value => value - 1)}>Previous</button><span>Page {page + 1} of {inbox.totalPages}</span><button type="button" data-button-variant="outline" disabled={page + 1 >= inbox.totalPages || busy} onClick={() => setPage(value => value + 1)}>Next</button></div>}
    </>}
  </main>;
}
