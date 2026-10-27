"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { NOTIFICATIONS_CHANGED, NotificationRole } from "@/lib/notifications";

export function NotificationMenu({ role = "STUDENT" }: { role?: NotificationRole } = {}) {
  const pathname = usePathname();
  const notificationsPath = `/${role.toLowerCase()}/notifications`;
  const [unread, setUnread] = useState<number | null>(null);
  useEffect(() => {
    let active = true;
    const refresh = () => { void api.get<{ unreadCount: number }>(`${notificationsPath}/unread-count`)
      .then(({ data }) => { if (active) setUnread(data.unreadCount); }).catch(() => {}); };
    refresh(); const timer = window.setInterval(refresh, 30_000);
    window.addEventListener("focus", refresh); window.addEventListener(NOTIFICATIONS_CHANGED, refresh);
    return () => { active = false; window.clearInterval(timer); window.removeEventListener("focus", refresh); window.removeEventListener(NOTIFICATIONS_CHANGED, refresh); };
  }, [notificationsPath]);
  return <Link href={notificationsPath} aria-label={unread === null ? "Notifications" : `Notifications, ${unread} unread notifications`} aria-current={pathname === notificationsPath ? "page" : undefined} className="app-nav-link notification-menu inline-flex items-center gap-2 text-sm font-semibold text-blue-700 hover:text-blue-900">
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="h-5 w-5"><path strokeLinecap="round" strokeLinejoin="round" d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
    <span className="notification-menu-label">Notifications</span>{unread !== null && unread > 0 && <span key={unread} aria-label={`${unread} unread notifications`} className="notification-badge rounded-full bg-blue-700 px-2 py-0.5 text-xs text-white">{unread > 99 ? "99+" : unread}</span>}
    <span className="sr-only" role="status">{unread === null ? "" : `${unread} unread notifications`}</span>
  </Link>;
}
