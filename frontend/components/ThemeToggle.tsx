"use client";

import { useLayoutEffect, useState } from "react";
import { applyTheme, THEME_STORAGE_KEY, Theme } from "@/lib/theme";

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>("light");

  useLayoutEffect(() => {
    const applied = document.documentElement.dataset.theme;
    let current: Theme = applied === "dark" ? "dark" : "light";
    // React's development remount can clear attributes set by the head script.
    if (applied !== "dark" && applied !== "light") {
      try { if (window.localStorage.getItem(THEME_STORAGE_KEY) === "dark") current = "dark"; } catch {}
      applyTheme(current);
    }
    setTheme(current);
    function syncTheme(event: StorageEvent) {
      if (event.key !== THEME_STORAGE_KEY && event.key !== null) return;
      const next = event.newValue === "dark" ? "dark" : "light";
      applyTheme(next);
      setTheme(next);
    }
    window.addEventListener("storage", syncTheme);
    return () => window.removeEventListener("storage", syncTheme);
  }, []);

  function toggleTheme() {
    const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    applyTheme(next);
    setTheme(next);
    // Storage can be unavailable in private or restricted browser sessions.
    try { window.localStorage.setItem(THEME_STORAGE_KEY, next); } catch {}
  }

  return <button type="button" data-button-variant="outline" className="theme-toggle" role="switch" aria-checked={theme === "dark"} aria-label="Dark mode" title={`Switch to ${theme === "dark" ? "light" : "dark"} mode`} onClick={toggleTheme}>
    <svg aria-hidden="true" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      {theme === "dark" ? <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42" /></> : <path d="M20.9 13.3A9 9 0 0 1 10.7 3.1 9 9 0 1 0 20.9 13.3Z" />}
    </svg>
  </button>;
}
