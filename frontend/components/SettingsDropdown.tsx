"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";

export function SettingsDropdown() {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const currentTab = pathname === "/student/settings" ? (searchParams?.get("tab") ?? "profile") : "";

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    function handleClickOutside(event: MouseEvent | TouchEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("touchstart", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("touchstart", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  // Focus management when menu opens
  useEffect(() => {
    if (open) {
      const firstLink = menuRef.current?.querySelector<HTMLAnchorElement>("a[role='menuitem']");
      firstLink?.focus();
    }
  }, [open]);

  const items = [
    {
      tab: "profile",
      href: "/student/settings?tab=profile",
      label: "Profile",
      description: "Personal & professional details",
      icon: (
        <svg
          className="h-5 w-5 text-slate-500"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          strokeWidth="1.8"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
          />
        </svg>
      ),
    },
    {
      tab: "documents",
      href: "/student/settings?tab=documents",
      label: "Documents",
      description: "Templates & downloadable forms",
      icon: (
        <svg
          className="h-5 w-5 text-slate-500"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          strokeWidth="1.8"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
          />
        </svg>
      ),
    },
    {
      tab: "security",
      href: "/student/settings?tab=security",
      label: "Security",
      description: "Password & account protection",
      icon: (
        <svg
          className="h-5 w-5 text-slate-500"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          strokeWidth="1.8"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
          />
        </svg>
      ),
    },
  ];

  function handleMenuKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      const links = Array.from(
        menuRef.current?.querySelectorAll<HTMLAnchorElement>("a[role='menuitem']") ?? []
      );
      const currentIndex = links.indexOf(document.activeElement as HTMLAnchorElement);
      if (currentIndex === -1) {
        links[0]?.focus();
      } else {
        const nextIndex =
          event.key === "ArrowDown"
            ? (currentIndex + 1) % links.length
            : (currentIndex - 1 + links.length) % links.length;
        links[nextIndex]?.focus();
      }
    }
  }

  return (
    <div ref={containerRef} className="relative inline-block text-left">
      <button
        ref={buttonRef}
        type="button"
        id="settings-menu-button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls="settings-dropdown-menu"
        aria-label="Settings menu"
        title="Settings"
        onClick={() => setOpen((prev) => !prev)}
        className={`ui-button flex items-center justify-center rounded-xl p-2 transition-colors ${
          open ? "bg-slate-100 text-blue-700" : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
        }`}
        data-button-variant="text"
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          className="h-5 w-5"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
          />
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      </button>

      {open && (
        <div
          ref={menuRef}
          id="settings-dropdown-menu"
          role="menu"
          aria-orientation="vertical"
          aria-labelledby="settings-menu-button"
          onKeyDown={handleMenuKeyDown}
          className="absolute right-0 mt-2 w-64 origin-top-right rounded-2xl border border-slate-200 bg-white p-2 shadow-xl ring-1 ring-black/5 z-50 focus:outline-none"
        >
          <div className="border-b border-slate-100 px-3 py-2">
            <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Settings</p>
          </div>
          <div className="mt-1 space-y-1">
            {items.map((item) => {
              const isActive = currentTab === item.tab;
              return (
                <Link
                  key={item.tab}
                  href={item.href}
                  role="menuitem"
                  tabIndex={0}
                  onClick={() => setOpen(false)}
                  className={`group flex items-start gap-3 rounded-xl p-2.5 text-left transition-colors ${
                    isActive
                      ? "bg-blue-50 text-blue-900 font-semibold"
                      : "text-slate-700 hover:bg-slate-50 hover:text-slate-900"
                  }`}
                >
                  <span className="mt-0.5">{item.icon}</span>
                  <div>
                    <p className={`text-sm ${isActive ? "font-bold text-blue-700" : "font-medium"}`}>
                      {item.label}
                    </p>
                    <p className="text-xs text-slate-500">{item.description}</p>
                  </div>
                </Link>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
