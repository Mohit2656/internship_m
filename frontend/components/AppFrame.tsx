"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { NotificationMenu } from "@/components/NotificationMenu";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";

export function AppFrame({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth(); const router = useRouter();
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  async function signOut() { await logout(); router.replace("/login"); }
  const home = user?.role === "ADMIN" ? "/admin/dashboard" : user?.role === "MENTOR" ? "/mentor/dashboard" : "/student/dashboard";
  const links = user?.role === "ADMIN" ? [
    ["/admin/monthly-progress", "Monthly progress"], ["/admin/opportunities", "Opportunities"],
    ["/admin/student-accounts", "Student accounts"], ["/admin/mentor-accounts", "Mentor accounts"],
    ["/admin/reports", "Reports"], ["/admin/attendance", "Attendance"],
  ] : user?.role === "MENTOR" ? [
    ["/mentor/monthly-progress", "Monthly progress"], ["/mentor/reports", "Reports"], ["/mentor/attendance", "Attendance"],
  ] : user?.role === "STUDENT" ? [["/student/monthly-progress", "Monthly progress"]] : [];
  return <div className="app-frame min-h-screen">
    <header className="app-topbar"><div className="app-topbar-inner">
      <Link href={home} className="app-brand" onClick={() => setMenuOpen(false)}><span className="app-brand-mark">IMS</span><span className="app-brand-copy"><span className="app-brand-name">Internship Management System</span><span className="app-brand-role">{user?.roleLabel}</span></span></Link>
      <div className="app-topbar-actions">
        {user?.role === "STUDENT" && <NotificationMenu key={user.id} />}
        <button ref={menuButton} type="button" data-button-variant="outline" className="app-menu-toggle" aria-label={menuOpen ? "Close navigation" : "Open navigation"} aria-expanded={menuOpen} aria-controls="app-navigation" onClick={() => setMenuOpen(open => !open)}>
          <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="20" height="20"><path strokeLinecap="round" d={menuOpen ? "M6 6l12 12M6 18L18 6" : "M4 6h16M4 12h16M4 18h16"}/></svg><span className="app-menu-label">Menu</span>
        </button>
      </div>
      <nav id="app-navigation" aria-label="Primary navigation" className="app-navigation" data-open={menuOpen} onKeyDown={event => { if (event.key === "Escape") { setMenuOpen(false); menuButton.current?.focus(); } }}>
        {links.map(([href, label]) => <Link key={href} href={href} className="app-nav-link" aria-current={pathname === href || pathname.startsWith(`${href}/`) ? "page" : undefined} onClick={() => setMenuOpen(false)}>{label}</Link>)}
        <button type="button" data-button-variant="outline" onClick={signOut} className="app-sign-out">Sign out</button>
      </nav>
    </div></header>
    <div className="app-page-content">{children}</div>
  </div>;
}
