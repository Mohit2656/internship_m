"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/monthly-progress";
import { NotificationInbox, StudentNotification, syncNotificationCount } from "@/lib/notifications";

export function NotificationsContent() {
  const router = useRouter();
  const [inbox, setInbox] = useState<NotificationInbox | null>(null);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setError("");
    const refresh = () => { void api.get<NotificationInbox>("/student/notifications", { params: { page }, signal: abort.signal })
      .then(({ data }) => { if (!abort.signal.aborted) { setInbox(data); syncNotificationCount(); } })
      .catch(() => { if (!abort.signal.aborted) setError("Notifications could not be loaded. Please try again."); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); }); };
    refresh(); const timer = window.setInterval(refresh, 30_000); window.addEventListener("focus", refresh);
    return () => { abort.abort(); window.clearInterval(timer); window.removeEventListener("focus", refresh); };
  }, [page, revision]);
  async function markRead(notification?: StudentNotification, open = false) {
    if (busy) return; setBusy(true); setError("");
    try {
      if (!notification || !notification.readAt) await api.patch(notification ? `/student/notifications/${notification.id}/read` : "/student/notifications/read-all");
      syncNotificationCount(); setRevision(value => value + 1);
      if (open && notification && notification.actionUrl.startsWith("/student/")) router.push(notification.actionUrl);
    } catch { setError("Notifications could not be updated. Please try again."); }
    finally { setBusy(false); }
  }
  return <main className="mx-auto max-w-4xl px-5 py-10 sm:px-8">
    <Link href="/student/dashboard" className="text-sm font-semibold text-blue-700 hover:underline">Back to dashboard</Link>
    <div className="mt-6 flex flex-wrap items-center justify-between gap-4"><div><h1 className="text-3xl font-bold">Notifications</h1><p className="mt-2 text-sm text-slate-600">Report feedback and reminders about configured deadlines.</p></div><button type="button" data-button-variant="outline" disabled={busy || !inbox?.unreadCount} onClick={() => void markRead()} className="text-sm">Mark all as read</button></div>
    {error && <p role="alert" className="mt-5 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}<button type="button" data-button-variant="text" onClick={() => setRevision(value => value + 1)} className="ml-3">Retry</button></p>}
    {loading ? <p className="mt-8 text-slate-500">Loading notifications...</p> : inbox && <>
      <p role="status" className="mt-5 text-sm font-semibold text-blue-700">{inbox.unreadCount} unread</p>
      {inbox.content.length === 0 ? <section className="mt-5 rounded-2xl border border-slate-200 bg-white p-8 text-center text-slate-500">No notifications yet. Report feedback and deadline reminders will appear here.</section> : <div className="mt-5 space-y-4">{inbox.content.map(item => <article key={item.id} className={`rounded-2xl border p-5 sm:p-6 ${item.readAt ? "border-slate-200 bg-white" : "border-blue-200 bg-blue-50/60"}`}>
        <div className="flex flex-wrap items-start justify-between gap-3"><h2 className="min-w-0 break-words text-lg font-bold text-slate-950">{item.title}</h2><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${item.readAt ? "bg-slate-100 text-slate-600" : "bg-blue-700 text-white"}`}>{item.readAt ? "Read" : "Unread"}</span></div>
        <p className="mt-3 whitespace-pre-wrap break-words text-sm text-slate-700">{item.message}</p>
        {item.rejectionReason && <div className="mt-4 rounded-xl border border-red-100 bg-red-50 p-4 text-sm text-red-800"><p className="font-bold">Rejection reason</p><p className="mt-2 whitespace-pre-wrap break-words">{item.rejectionReason}</p></div>}
        <p className="mt-4 text-xs text-slate-500">{item.type === "REPORT_REJECTED" ? "Rejected: " : "Notified: "}{formatDateTime(item.createdAt)}{item.mentorName && <> ? Faculty mentor: {item.mentorName}</>}</p>
        <div className="mt-5 flex flex-wrap gap-3"><button type="button" disabled={busy} onClick={() => void markRead(item, true)} className="text-sm">{item.type === "REPORT_REJECTED" ? "Open report" : "Open task"}</button>{!item.readAt && <button type="button" data-button-variant="outline" disabled={busy} onClick={() => void markRead(item)} className="text-sm">Mark as read</button>}</div>
      </article>)}</div>}
      {inbox.totalPages > 1 && <div className="mt-6 flex items-center justify-between gap-3 text-sm"><button type="button" data-button-variant="outline" disabled={page === 0 || busy} onClick={() => setPage(value => value - 1)}>Previous</button><span>Page {page + 1} of {inbox.totalPages}</span><button type="button" data-button-variant="outline" disabled={page + 1 >= inbox.totalPages || busy} onClick={() => setPage(value => value + 1)}>Next</button></div>}
    </>}
  </main>;
}
export default function StudentNotificationsPage() { return <ProtectedRoute role="STUDENT"><AppFrame><NotificationsContent /></AppFrame></ProtectedRoute>; }
