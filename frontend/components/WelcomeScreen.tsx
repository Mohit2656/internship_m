"use client";

import Image from "next/image";
import { useEffect, useState } from "react";

export function WelcomeScreen() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    // The root layout persists during internal navigation; full reloads mount it anew.
    // Authentication and page initialization run immediately behind this overlay.
    setVisible(true);
    const timer = window.setTimeout(() => setVisible(false), 3_000);
    return () => window.clearTimeout(timer);
  }, []);

  if (!visible) return null;

  return <div className="college-welcome" role="status" aria-live="polite">
    <div className="college-welcome-content">
      <Image src="/branding/college-logo.png" alt="St. Vincent Pallotti College logo" width={150} height={150} priority className="college-welcome-logo" />
      <p className="college-welcome-heading">Welcome to the Department of Computer Engineering.</p>
      <p className="college-welcome-brand">IIIC CELL</p>
    </div>
  </div>;
}
