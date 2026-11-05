"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { NotificationMenu } from "@/components/NotificationMenu";
import { ThemeToggle } from "@/components/ThemeToggle";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { StudentSidebar } from "@/components/StudentSidebar";
import { AdminSidebar } from "@/components/AdminSidebar";
import { MentorSidebar } from "@/components/MentorSidebar";
import { SettingsDropdown } from "@/components/SettingsDropdown";

export function AppFrame({ children, activeSection }: { children: React.ReactNode; activeSection?: string }) {
  const { user, logout } = useAuth(); const router = useRouter();
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const [hash, setHash] = useState("");
  const menuButton = useRef<HTMLButtonElement>(null);
  const header = useRef<HTMLElement>(null);
  const studentSidebar = useRef<HTMLDivElement>(null);
  const isAdmin = user?.role === "ADMIN";
  const isStudent = user?.role === "STUDENT";
  const isMentor = user?.role === "MENTOR";
  const hasSidebar = isStudent || isAdmin || isMentor;
  function closeMenu(restoreFocus = false) { setMenuOpen(false); if (restoreFocus) requestAnimationFrame(() => menuButton.current?.focus()); }
  useEffect(() => {
    if (!menuOpen) return;
    function closeOutside(event: PointerEvent) {
      if (event.target instanceof Node && !header.current?.contains(event.target) && !studentSidebar.current?.contains(event.target)) closeMenu(true);
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") closeMenu(true);
    }
    document.addEventListener("pointerdown", closeOutside);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOutside);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [menuOpen]);
  useEffect(() => {
    const syncHash = () => setHash(window.location.hash.slice(1));
    syncHash(); window.addEventListener("hashchange", syncHash);
    return () => window.removeEventListener("hashchange", syncHash);
  }, [pathname]);
  useEffect(() => {
    if (!menuOpen || !hasSidebar) return;
    requestAnimationFrame(() => studentSidebar.current?.querySelector<HTMLElement>("a[aria-current='page'], a")?.focus());
  }, [menuOpen, hasSidebar]);
  async function signOut() { await logout(); router.replace("/login"); }
  const home = user?.role === "ADMIN" ? "/admin/dashboard" : user?.role === "MENTOR" ? "/mentor/dashboard" : "/student/dashboard";
  const links = user?.role === "ADMIN" ? [
    ["/admin/weekly-progress", "Weekly progress"], ["/admin/monthly-progress", "Monthly progress"], ["/admin/opportunities", "Opportunities"],
    ["/admin/student-accounts", "Student accounts"], ["/admin/mentor-accounts", "Mentor accounts"],
    ["/admin/internship-completions", "Completions"], ["/admin/settings/academic-sessions", "Settings"], ["/admin/reports", "Reports"], ["/admin/attendance", "Attendance"],
  ] : user?.role === "MENTOR" ? [
    ["/mentor/weekly-progress", "Weekly progress"], ["/mentor/monthly-progress", "Monthly progress"], ["/mentor/reports", "Reports"], ["/mentor/attendance", "Attendance"],
  ] : user?.role === "STUDENT" ? [
    ["/student/weekly-progress", "Weekly progress"],
    ["/student/monthly-progress", "Monthly progress"],
    ["/student/documents", "Documents & Templates"],
  ] : [];
  const groups = [
    { title: "Internship Management", links: links.filter(([href]) => ["/admin/weekly-progress", "/admin/monthly-progress", "/admin/opportunities", "/admin/internship-completions"].includes(href)) },
    { title: "User Management", links: links.filter(([href]) => ["/admin/student-accounts", "/admin/mentor-accounts"].includes(href)) },
    { title: "Administration", links: links.filter(([href]) => ["/admin/settings/academic-sessions", "/admin/reports", "/admin/attendance"].includes(href)) },
  ];
  const renderLink = ([href, label]: string[]) => <Link key={href} href={href} className="app-nav-link" aria-current={pathname === href || pathname.startsWith(`${href}/`) ? "page" : undefined} onClick={() => setMenuOpen(false)}>{label}</Link>;
  return <div className="app-frame min-h-screen" data-role={user?.role}>
    <header ref={header} className="app-topbar"><div className="app-topbar-inner">
      <Link href={home} className="app-brand" onClick={() => setMenuOpen(false)}><span className="app-brand-mark">IMS</span><span className="app-brand-copy"><span className="app-brand-name">Internship Management System</span><span className="app-brand-role">{user?.roleLabel}</span></span></Link>
      <div className="app-topbar-actions" role="group" aria-label="Account controls" onClick={event => { if (event.target instanceof Element && event.target.closest("a")) setMenuOpen(false); }}>
        <ThemeToggle />
        {(user?.role === "STUDENT" || user?.role === "MENTOR" || user?.role === "ADMIN") && <NotificationMenu key={`${user.role}-${user.id}`} role={user.role} />}
        {isStudent && <SettingsDropdown />}
        <button ref={menuButton} type="button" data-button-variant="outline" className="app-menu-toggle" aria-label={menuOpen ? "Close navigation" : "Open navigation"} aria-expanded={menuOpen} aria-controls={isStudent ? "student-navigation" : isAdmin ? "admin-navigation" : isMentor ? "mentor-navigation" : "app-navigation"} onClick={() => setMenuOpen(open => !open)}>
          <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="20" height="20"><path strokeLinecap="round" d={menuOpen ? "M6 6l12 12M6 18L18 6" : "M4 6h16M4 12h16M4 18h16"} /></svg><span className="app-menu-label">Menu</span>
        </button>
      </div>
      {!hasSidebar && <nav id="app-navigation" aria-label="Primary navigation" className="app-navigation" data-open={menuOpen}>
        {isAdmin ? <>{groups.map(group => <section key={group.title} className="app-nav-group" aria-label={group.title}><h2 className="app-nav-heading">{group.title}</h2>{group.links.map(renderLink)}</section>)}<section className="app-nav-group" aria-label="Account"><h2 className="app-nav-heading">Account</h2><p className="app-nav-hint">Notifications and theme controls are above.</p><button type="button" data-button-variant="outline" onClick={signOut} className="app-sign-out">Sign out</button></section></> : <>{links.map(renderLink)}<button type="button" data-button-variant="outline" onClick={signOut} className="app-sign-out">Sign out</button></>}
      </nav>}
    </div></header>
    {hasSidebar ? <div className="student-app-layout"><div ref={studentSidebar}>{isStudent ? <StudentSidebar pathname={pathname} hash={hash} open={menuOpen} onNavigate={() => closeMenu()} onSignOut={signOut} /> : isAdmin ? <AdminSidebar pathname={pathname} hash={hash} activeSection={activeSection} open={menuOpen} onNavigate={() => closeMenu()} onSignOut={signOut} /> : <MentorSidebar pathname={pathname} hash={hash} open={menuOpen} onNavigate={() => closeMenu()} onSignOut={signOut} />}</div><div className="app-page-content">{children}</div></div> : <div className="app-page-content">{children}</div>}
  </div>;
}
