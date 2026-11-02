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

export function AppFrame({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth(); const router = useRouter();
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const [hash, setHash] = useState("");
  const menuButton = useRef<HTMLButtonElement>(null);
  const header = useRef<HTMLElement>(null);
  const workspaceSidebar = useRef<HTMLDivElement>(null);
  const isAdmin = user?.role === "ADMIN";
  const isStudent = user?.role === "STUDENT";
  const isMentor = user?.role === "MENTOR";
  const hasSidebar = isStudent || isAdmin || isMentor;
  function closeMenu(restoreFocus = false) { setMenuOpen(false); if (restoreFocus && menuOpen) requestAnimationFrame(() => menuButton.current?.focus()); }
  function navigateWorkspace(href?: string) {
    if (href) setHash(href.split("#")[1] ?? "");
    closeMenu(true);
  }
  useEffect(() => { setMenuOpen(false); }, [pathname]);
  useEffect(() => {
    if (!menuOpen) return;
    function closeOutside(event: PointerEvent) {
      if (event.target instanceof Node && !header.current?.contains(event.target) && !workspaceSidebar.current?.contains(event.target)) closeMenu(true);
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape" && !event.defaultPrevented) closeMenu(true);
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
    syncHash();
    window.addEventListener("hashchange", syncHash);
    window.addEventListener("popstate", syncHash);
    return () => {
      window.removeEventListener("hashchange", syncHash);
      window.removeEventListener("popstate", syncHash);
    };
  }, [pathname]);
  useEffect(() => {
    if (!menuOpen || !hasSidebar) return;
    const desktop = window.matchMedia("(min-width: 1280px)");
    if (desktop.matches) {
      setMenuOpen(false);
      return;
    }
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const visibleControls = () => Array.from(workspaceSidebar.current?.querySelectorAll<HTMLElement>("aside a[href], aside button:not([disabled])") ?? [])
      .filter(control => !control.closest("[hidden]") && control.getClientRects().length > 0);
    const focusFrame = requestAnimationFrame(() => {
      const controls = visibleControls();
      (controls.find(control => control.getAttribute("aria-current") === "page") ?? controls.find(control => control.tagName === "A") ?? controls[0])?.focus();
    });
    function containFocus(event: KeyboardEvent) {
      if (event.key !== "Tab") return;
      const controls = visibleControls();
      if (!controls.length) return;
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
    function closeOnDesktop(event: MediaQueryListEvent) {
      if (event.matches) setMenuOpen(false);
    }
    document.addEventListener("keydown", containFocus);
    desktop.addEventListener("change", closeOnDesktop);
    return () => {
      cancelAnimationFrame(focusFrame);
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", containFocus);
      desktop.removeEventListener("change", closeOnDesktop);
    };
  }, [menuOpen, hasSidebar]);
  async function signOut() { await logout(); router.replace("/login"); }
  const home = user?.role === "ADMIN" ? "/admin/dashboard" : user?.role === "MENTOR" ? "/mentor/dashboard" : "/student/dashboard";
  return <div className="app-frame min-h-screen" data-role={user?.role}>
    <header ref={header} className="app-topbar"><div className="app-topbar-inner">
      <Link href={home} className="app-brand" onClick={() => setMenuOpen(false)} onNavigate={() => setHash("")}><span className="app-brand-mark">IMS</span><span className="app-brand-copy"><span className="app-brand-name">Internship Management System</span><span className="app-brand-role">{user?.roleLabel}</span></span></Link>
      <div className="app-topbar-actions" role="group" aria-label="Account controls" onClick={event => { if (event.target instanceof Element && event.target.closest("a")) setMenuOpen(false); }}>
        <ThemeToggle />
        {(user?.role === "STUDENT" || user?.role === "MENTOR" || user?.role === "ADMIN") && <NotificationMenu key={`${user.role}-${user.id}`} role={user.role} />}
        {isStudent && <SettingsDropdown />}
        <button ref={menuButton} type="button" data-button-variant="outline" className="app-menu-toggle" aria-label={menuOpen ? "Close navigation" : "Open navigation"} aria-expanded={menuOpen} aria-controls={isStudent ? "student-navigation" : isAdmin ? "admin-navigation" : isMentor ? "mentor-navigation" : "app-navigation"} onClick={() => setMenuOpen(open => !open)}>
          <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="20" height="20"><path strokeLinecap="round" d={menuOpen ? "M6 6l12 12M6 18L18 6" : "M4 6h16M4 12h16M4 18h16"}/></svg><span className="app-menu-label">Menu</span>
        </button>
      </div>
      {!hasSidebar && <nav id="app-navigation" aria-label="Primary navigation" className="app-navigation" data-open={menuOpen}>
        <button type="button" data-button-variant="outline" onClick={signOut} className="app-sign-out">Sign out</button>
      </nav>}
    </div></header>
    {hasSidebar ? <div className="student-app-layout"><div ref={workspaceSidebar}>{isStudent ? <StudentSidebar pathname={pathname} hash={hash} open={menuOpen} onNavigate={navigateWorkspace} onSignOut={signOut}/> : isAdmin ? <AdminSidebar pathname={pathname} hash={hash} open={menuOpen} onNavigate={navigateWorkspace} onSignOut={signOut}/> : <MentorSidebar pathname={pathname} hash={hash} open={menuOpen} onNavigate={navigateWorkspace} onSignOut={signOut}/>}</div><div className="app-page-content">{children}</div></div>:<div className="app-page-content">{children}</div>}
  </div>;
}
