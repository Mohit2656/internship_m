"use client";

import Link from "next/link";

type Item = { href: string; label: string; exact?: boolean; section?: string; nested?: string };
const groups: Array<{ heading: string; items: Item[] }> = [
  {
    heading: "Overview",
    items: [
      { href: "/mentor/dashboard", label: "Home", exact: true },
      { href: "/mentor/students", label: "Assigned Students", nested: "/mentor/students" },
    ],
  },
  {
    heading: "Attendance",
    items: [
      { href: "/mentor/attendance", label: "Attendance" },
    ],
  },
  {
    heading: "Internship Management",
    items: [
      { href: "/mentor/progress", label: "Progress", nested: "/mentor/progress" },
      { href: "/mentor/documents", label: "Documents" },
      { href: "/mentor/reports", label: "Reports" },
    ],
  },
  {
    heading: "Account",
    items: [
      { href: "/mentor/notifications", label: "Notifications" },
      { href: "/mentor/change-password", label: "Change Password" },
    ],
  },
];

function active(item: Item, pathname: string, hash: string) {
  const route = item.href.split("#")[0];
  if (item.nested && (pathname === item.nested || pathname.startsWith(`${item.nested}/`))) return true;
  if (item.section) return pathname === route && hash === item.section;
  return item.exact ? pathname === route : pathname === route || pathname.startsWith(`${route}/`);
}

export function MentorSidebar({
  pathname,
  hash,
  open,
  onNavigate,
  onSignOut,
}: {
  pathname: string;
  hash: string;
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
      <aside id="mentor-navigation" className="student-sidebar" data-open={open} aria-label="Mentor navigation">
        <div className="student-sidebar-scroll">
          <div className="student-sidebar-header">
            <p className="student-sidebar-title">Mentor workspace</p>
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
          <nav aria-label="Mentor sections" className="student-sidebar-nav">
            {groups.map((group) => (
              <section key={group.heading} className="student-sidebar-group" aria-label={group.heading}>
                <h2 className="student-sidebar-heading">{group.heading}</h2>
                {group.items.map((item) => (
                  <Link
                    key={item.href}
                    href={item.href}
                    className="student-sidebar-link"
                    aria-current={active(item, pathname, hash) ? "page" : undefined}
                    onClick={onNavigate}
                  >
                    {item.label}
                  </Link>
                ))}
              </section>
            ))}
          </nav>
          <button type="button" data-button-variant="outline" onClick={onSignOut} className="student-sidebar-signout">
            Sign out
          </button>
        </div>
      </aside>
    </>
  );
}
