"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";

export type WorkspaceSidebarItem = {
  href: string;
  label: string;
  exact?: boolean;
  section?: string;
  nested?: string;
  aliases?: string[];
  inactiveWithHash?: boolean;
  children?: WorkspaceSidebarItem[];
};

export type WorkspaceSidebarGroup = {
  heading: string;
  items: WorkspaceSidebarItem[];
};

export type WorkspaceSidebarProps = {
  pathname: string;
  hash?: string;
  open: boolean;
  onNavigate: (href?: string) => void;
  onSignOut: () => void;
};

function isCurrent(item: WorkspaceSidebarItem, pathname: string, hash: string) {
  const route = item.href.split("#")[0].split("?")[0];
  if (item.nested && pathname.startsWith(`${item.nested}/`)) return true;
  if (item.section) return pathname === route && hash === item.section;
  if (item.aliases?.includes(pathname)) return true;
  if (item.inactiveWithHash && pathname === route && hash) return false;
  return item.exact ? pathname === route : pathname === route || pathname.startsWith(`${route}/`);
}

function SidebarDropdown({ id, item, pathname, hash, onNavigate }: {
  id: string;
  item: WorkspaceSidebarItem;
  pathname: string;
  hash: string;
  onNavigate: WorkspaceSidebarProps["onNavigate"];
}) {
  const current = isCurrent(item, pathname, hash) || item.children!.some(child => isCurrent(child, pathname, hash));
  const [expanded, setExpanded] = useState(current);
  const toggle = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => { if (current) setExpanded(true); }, [current, pathname]);

  function focusLink(last = false) {
    requestAnimationFrame(() => {
      const links = panel.current?.querySelectorAll<HTMLAnchorElement>("a");
      if (links?.length) links[last ? links.length - 1 : 0].focus();
    });
  }

  return <div className="student-sidebar-dropdown" onKeyDown={event => {
    if (event.key === "Escape" && expanded) {
      event.preventDefault(); event.stopPropagation(); setExpanded(false); toggle.current?.focus();
    }
  }}>
    <button ref={toggle} id={`${id}-toggle`} type="button" data-button-variant="text"
      className="student-sidebar-link student-sidebar-disclosure" data-current={current}
      aria-expanded={expanded} aria-controls={`${id}-options`}
      onClick={() => setExpanded(value => !value)} onKeyDown={event => {
        if (event.key === "ArrowDown" || event.key === "ArrowUp") {
          event.preventDefault(); setExpanded(true); focusLink(event.key === "ArrowUp");
        }
      }}>
      <span>{item.label}</span>
      <svg aria-hidden="true" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="m5 7.5 5 5 5-5" strokeLinecap="round" strokeLinejoin="round"/></svg>
    </button>
    <div ref={panel} id={`${id}-options`} hidden={!expanded} aria-labelledby={`${id}-toggle`}
      className="student-sidebar-submenu" onKeyDown={event => {
        const links = Array.from(panel.current?.querySelectorAll<HTMLAnchorElement>("a") ?? []);
        const index = links.indexOf(document.activeElement as HTMLAnchorElement);
        if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
          event.preventDefault();
          const next = event.key === "Home" ? 0 : event.key === "End" ? links.length - 1 : (index + (event.key === "ArrowDown" ? 1 : -1) + links.length) % links.length;
          links[next]?.focus();
        }
      }}>
      {item.children!.map(child => <Link key={child.href} href={child.href}
        className="student-sidebar-link student-sidebar-sublink"
        aria-current={isCurrent(child, pathname, hash) ? "page" : undefined}
        onNavigate={() => onNavigate(child.href)}>{child.label}</Link>)}
    </div>
  </div>;
}

export function WorkspaceSidebar({
  id,
  label,
  groups,
  pathname,
  hash = "",
  open,
  onNavigate,
  onSignOut,
}: WorkspaceSidebarProps & {
  id: string;
  label: string;
  groups: WorkspaceSidebarGroup[];
}) {
  return (
    <>
      <button
        type="button"
        aria-label="Close navigation"
        tabIndex={open ? 0 : -1}
        className="student-sidebar-backdrop"
        data-open={open}
        onClick={() => onNavigate()}
      />
      <aside
        id={`${id}-navigation`}
        className="student-sidebar"
        data-open={open}
        aria-label={`${label} navigation`}
      >
        <div className="student-sidebar-scroll">
          <div className="student-sidebar-header">
            <p className="student-sidebar-title">{label} workspace</p>
            <button
              type="button"
              data-button-variant="text"
              className="student-sidebar-close"
              aria-label="Close navigation"
              onClick={() => onNavigate()}
            >
              &times;
            </button>
          </div>
          <nav aria-label={`${label} sections`} className="student-sidebar-nav">
            {groups.map((group) => {
              const headingId = `${id}-nav-${group.heading.toLowerCase().replaceAll(" ", "-")}`;
              return (
                <section
                  key={group.heading}
                  className="student-sidebar-group"
                  aria-labelledby={headingId}
                >
                  <h2 id={headingId} className="student-sidebar-heading">
                    {group.heading}
                  </h2>
                  {group.items.map((item) => (
                    item.children?.length ? <SidebarDropdown key={item.href} id={`${id}-${item.label.toLowerCase().replaceAll(" ", "-")}`} item={item} pathname={pathname} hash={hash} onNavigate={onNavigate}/> : <div key={item.href} className="space-y-1">
                      <Link
                        href={item.href}
                        className="student-sidebar-link"
                        aria-current={isCurrent(item, pathname, hash) ? "page" : undefined}
                        onNavigate={() => onNavigate(item.href)}
                      >
                        {item.label}
                      </Link>
                    </div>
                  ))}
                </section>
              );
            })}
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
