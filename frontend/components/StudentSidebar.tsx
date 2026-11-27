"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

type Item = {
  href: string;
  label: string;
  exact?: boolean;
};

const groups: Array<{ heading: string; items: Item[] }> = [
  {
    heading: "Overview",
    items: [
      { href: "/student/dashboard", label: "Dashboard", exact: true },
      { href: "/student/internship", label: "My Internships" },
      { href: "/student/attendance", label: "Attendance History" },
    ],
  },
  {
    heading: "Academics",
    items: [
      { href: "/student/progress-reports", label: "Progress Reports" },
      { href: "/student/opportunities", label: "Opportunities" },
    ],
  },
  {
    heading: "Account",
    items: [
      { href: "/student/notifications", label: "Notifications" },
    ],
  },
];

function active(item: Item, pathname: string) {
  const route = item.href.split("#")[0].split("?")[0];
  if (route === "/student/dashboard") return pathname === "/student/dashboard";
  if (item.href === "/student/progress-reports") {
    return (
      pathname === "/student/progress-reports" ||
      pathname === "/student/weekly-progress" ||
      pathname === "/student/monthly-progress"
    );
  }
  if (item.href === "/student/attendance") {
    return pathname === "/student/attendance" || pathname.startsWith("/student/attendance/");
  }
  return item.exact ? pathname === route : pathname === route || pathname.startsWith(`${route}/`);
}

export function StudentSidebar({
  pathname,
  open,
  onNavigate,
  onSignOut,
}: {
  pathname: string;
  hash?: string;
  open: boolean;
  onNavigate: () => void;
  onSignOut: () => void;
}) {
  const [lockedRoutes, setLockedRoutes] = useState<string[]>([]);

  useEffect(() => {
    const sync = () => {
      try {
        const raw = sessionStorage.getItem("ims_student_locked_routes");
        const parsed = raw ? JSON.parse(raw) : [];
        setLockedRoutes(Array.isArray(parsed) ? parsed : []);
      } catch {
        setLockedRoutes([]);
      }
    };
    sync();
    window.addEventListener("ims-student-completion-changed", sync);
    return () => window.removeEventListener("ims-student-completion-changed", sync);
  }, []);
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
      <aside
        id="student-navigation"
        className="student-sidebar"
        data-open={open}
        aria-label="Student navigation"
      >
        <div className="student-sidebar-scroll">
          <div className="student-sidebar-header">
            <p className="student-sidebar-title">Student workspace</p>
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
          <nav aria-label="Student sections" className="student-sidebar-nav">
            {groups.map((group) => (
              <section
                key={group.heading}
                className="student-sidebar-group"
                aria-labelledby={`student-nav-${group.heading.toLowerCase()}`}
              >
                <h2
                  id={`student-nav-${group.heading.toLowerCase()}`}
                  className="student-sidebar-heading"
                >
                  {group.heading}
                </h2>
                {group.items.map((item) => {
                  const isCurrent = active(item, pathname);
                  const isLocked = lockedRoutes.includes(item.href);
                  return (
                    <div key={item.href} className="space-y-1">
                      <Link
                        href={item.href}
                        className={`student-sidebar-link justify-between gap-2 ${isLocked ? "opacity-75" : ""}`}
                        aria-current={isCurrent ? "page" : undefined}
                        title={isLocked ? "Complete your profile to unlock" : undefined}
                        onClick={onNavigate}
                      >
                        <span>{item.label}</span>
                        {isLocked && (
                          <svg
                            aria-label="Locked until profile is complete"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2"
                            className="h-3.5 w-3.5 shrink-0 text-slate-400"
                          >
                            <rect x="4" y="11" width="16" height="10" rx="2" />
                            <path strokeLinecap="round" d="M8 11V7a4 4 0 0 1 8 0v4" />
                          </svg>
                        )}
                      </Link>
                    </div>
                  );
                })}
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
