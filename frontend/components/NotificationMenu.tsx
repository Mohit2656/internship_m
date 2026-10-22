"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { NOTIFICATIONS_CHANGED } from "@/lib/notifications";

export function NotificationMenu() {
  const [unread, setUnread] = useState<number | null>(null);
  useEffect(() => {
    let active = true;
    const refresh = () => { void api.get<{ unreadCount: number }>("/student/notifications/unread-count")
      .then(({ data }) => { if (active) setUnread(data.unreadCount); }).catch(() => {}); };
    refresh(); const timer = window.setInterval(refresh, 30_000);
    window.addEventListener("focus", refresh); window.addEventListener(NOTIFICATIONS_CHANGED, refresh);
    return () => { active = false; window.clearInterval(timer); window.removeEventListener("focus", refresh); window.removeEventListener(NOTIFICATIONS_CHANGED, refresh); };
  }, []);
  return <Link href="/student/notifications" className="inline-flex items-center gap-2 text-sm font-semibold text-blue-700 hover:text-blue-900">
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="h-5 w-5"><path strokeLinecap="round" strokeLinejoin="round" d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
    Notifications{unread !== null && unread > 0 && <span aria-label={`${unread} unread notifications`} className="rounded-full bg-blue-700 px-2 py-0.5 text-xs text-white">{unread > 99 ? "99+" : unread}</span>}
  </Link>;
}
