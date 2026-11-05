"use client";

import Link from "next/link";

type Item = { href: string; label: string; exact?: boolean; section?: string };

const groups: { heading: string; items: Item[] }[] = [
  {
    heading: "Overview",
    items: [
      { href: "/admin/dashboard", label: "Home", exact: true },
      { href: "/admin/settings/academic-sessions", label: "Academic Sessions" },
      { href: "/admin/students", label: "Student Directory" },
      { href: "/admin/recent-activity", label: "Recent Activity" },
      { href: "/admin/second-internship-requests", label: "Second Internship Requests" },
    ],
  },
  {
    heading: "Internship Management",
    items: [
      { href: "/admin/opportunities", label: "Opportunities" },
      { href: "/admin/progress", label: "Progress" },
      { href: "/admin/attendance", label: "Attendance" },
      { href: "/admin/internship-completions", label: "Completions" },
      { href: "/admin/reports", label: "Reports" },
    ],
  },
  {
    heading: "User Management",
    items: [
      { href: "/admin/accounts", label: "Accounts" },
    ],
  },
  {
    heading: "Account",
    items: [
      { href: "/admin/notifications", label: "Notifications" },
    ],
  },
];

function active(item: Item, pathname: string, hash: string, activeSection?: string) {
  const effectiveSection = activeSection || (typeof window !== "undefined" && new URLSearchParams(window.location.search).get("from") === "attendance" && pathname.startsWith("/admin/students/") ? "/admin/attendance" : undefined);
  if (effectiveSection) {
    return item.href === effectiveSection;
  }
  const route = item.href.split("#")[0];
  if (item.section) return pathname === route && hash === item.section;
  if (item.href === "/admin/second-internship-requests" && hash === "second-internship-requests") return true;
  if (item.href === "/admin/progress" && (pathname === "/admin/weekly-progress" || pathname === "/admin/monthly-progress")) return true;
  if (item.href === "/admin/accounts" && (pathname === "/admin/student-accounts" || pathname === "/admin/mentor-accounts")) return true;
  if (route === "/admin/dashboard" && pathname === route && hash) return false;
  return item.exact ? pathname === route : pathname === route || pathname.startsWith(`${route}/`);
}

export function AdminSidebar({
  pathname,
  hash,
  activeSection,
  open,
  onNavigate,
  onSignOut,
}: {
  pathname: string;
  hash: string;
  activeSection?: string;
  open: boolean;
  onNavigate: () => void;
  onSignOut: () => void;
}) {
  return (
    <>
      <button
        type="button"
        aria-label="Close navigation"
        tabIndex={open ? 0 : -1}
        className="student-sidebar-backdrop"
        data-open={open}
        onClick={onNavigate}
      />
      <aside id="admin-navigation" className="student-sidebar" data-open={open} aria-label="III Cell navigation">
        <div className="student-sidebar-scroll">
          <div className="student-sidebar-header">
            <p className="student-sidebar-title">III Cell workspace</p>
            <button
              type="button"
              data-button-variant="text"
              className="student-sidebar-close"
              aria-label="Close navigation"
              onClick={onNavigate}
            >
              ×
            </button>
          </div>
          <nav aria-label="III Cell sections" className="student-sidebar-nav">
            {groups.map((group) => (
              <section key={group.heading} className="student-sidebar-group" aria-label={group.heading}>
                <h2 className="student-sidebar-heading">{group.heading}</h2>
                {group.items.map((item) => (
                  <Link
                    key={item.href}
                    href={item.href}
                    className="student-sidebar-link"
                    aria-current={active(item, pathname, hash, activeSection) ? "page" : undefined}
                    onClick={onNavigate}
                  >
                    {item.label}
                  </Link>
                ))}
              </section>
            ))}
          </nav>
          <button
            type="button"
            data-button-variant="outline"
            onClick={onSignOut}
            className="student-sidebar-signout"
          >
            Sign out
          </button>
        </div>
      </aside>
    </>
  );
}
