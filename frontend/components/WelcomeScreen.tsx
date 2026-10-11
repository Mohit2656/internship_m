"use client";

import Image from "next/image";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";

const SESSION_KEY = "ims-college-welcome-shown";

export function WelcomeScreen() {
  const pathname = usePathname();
  const entryPath = useRef(pathname);
  const showForSession = useRef<boolean | null>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    // Decide once, including React's development effect replay. Internal routes never restart it.
    if (showForSession.current === null) {
      showForSession.current = entryPath.current === "/" || entryPath.current === "/login";
      if (showForSession.current) {
        try {
          showForSession.current = sessionStorage.getItem(SESSION_KEY) !== "1";
          if (showForSession.current) sessionStorage.setItem(SESSION_KEY, "1");
        } catch {
          // Storage restrictions still allow a single welcome during this layout's lifetime.
        }
      }
    }
    if (!showForSession.current) return;
    setVisible(true);
    const timer = window.setTimeout(() => setVisible(false), 1_000);
    return () => window.clearTimeout(timer);
  }, []);

  if (!visible || (pathname !== "/" && pathname !== "/login")) return null;

  return <div className="college-welcome" role="status" aria-live="polite">
    <div className="college-welcome-content">
      <Image src="/branding/college-logo.png" alt="St. Vincent Pallotti College logo" width={150} height={150} priority className="college-welcome-logo" />
      <p>Welcome to the Department of Computer Engineering</p>
    </div>
  </div>;
}
