"use client";

import Link from "next/link";

type Item = { href: string; label: string; exact?: boolean; section?: string };
const groups: Array<{ heading: string; items: Item[] }> = [
  { heading: "Overview", items: [
    { href: "/student/dashboard", label: "Home", exact: true },
    { href: "/student/profile", label: "Profile", exact: true },
    { href: "/student/professional-profile", label: "Professional Profile", exact: true },
    { href: "/student/skill-gaps", label: "Skill Gaps", exact: true },
  ] },
  { heading: "Internship", items: [
    { href: "/student/internship", label: "Internship", exact: true },
    { href: "/student/internship/details", label: "Internship Details" },
    { href: "/student/internship/second", label: "Second Internship" },
    { href: "/student/opportunities", label: "Opportunities" },
  ] },
  { heading: "Records", items: [
    { href: "/student/weekly-progress", label: "Weekly Progress" },
    { href: "/student/monthly-progress", label: "Monthly Progress" },
    { href: "/student/dashboard#daily-attendance", label: "Attendance", section: "daily-attendance" },
    { href: "/student/documents", label: "Documents & Templates" },
  ] },
  { heading: "Account", items: [
    { href: "/student/notifications", label: "Notifications" },
    { href: "/student/change-password", label: "Change Password" },
  ] },
];

function active(item: Item, pathname: string, hash: string) {
  const route = item.href.split("#")[0];
  if (item.section) return pathname === route && hash === item.section;
  if (route === "/student/dashboard" && pathname === route && hash) return false;
  return item.exact ? pathname === route : pathname === route || pathname.startsWith(`${route}/`);
}

export function StudentSidebar({ pathname, hash, open, onNavigate, onSignOut }: {
  pathname: string; hash: string; open: boolean; onNavigate: () => void; onSignOut: () => void;
}) {
  return <>
    <button type="button" aria-label="Close navigation" tabIndex={open ? 0 : -1} className="student-sidebar-backdrop" data-open={open} onClick={onNavigate}/>
    <aside id="student-navigation" className="student-sidebar" data-open={open} aria-label="Student navigation">
      <div className="student-sidebar-scroll">
        <div className="student-sidebar-header"><p className="student-sidebar-title">Student workspace</p><button type="button" data-button-variant="text" className="student-sidebar-close" aria-label="Close navigation" onClick={onNavigate}>×</button></div>
        <nav aria-label="Student sections" className="student-sidebar-nav">
          {groups.map(group => <section key={group.heading} className="student-sidebar-group" aria-labelledby={`student-nav-${group.heading.toLowerCase()}`}>
            <h2 id={`student-nav-${group.heading.toLowerCase()}`} className="student-sidebar-heading">{group.heading}</h2>
            {group.items.map(item => <Link key={item.href} href={item.href} className="student-sidebar-link" aria-current={active(item, pathname, hash) ? "page" : undefined} onClick={onNavigate}>{item.label}</Link>)}
          </section>)}
        </nav>
        <button type="button" data-button-variant="outline" onClick={onSignOut} className="student-sidebar-signout">Sign out</button>
      </div>
    </aside>
  </>;
}
