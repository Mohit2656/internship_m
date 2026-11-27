"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { MouseEvent, useCallback, useEffect, useState } from "react";
import { formatBackLabel, getPreviousRoute, getRouteTitle, trackRoute, isReloadNavigation } from "@/lib/navigation";

export interface BackButtonProps {
  fallbackHref: string;
  fallbackLabel?: string;
  label?: string;
  fixedFallback?: boolean;
  className?: string;
  children?: React.ReactNode;
}

/**
 * Context-aware back navigation button.
 * - Navigates to the actual previous page in browser history when valid within the IMS application.
 * - Dynamically derives the button label from the actual in-app navigation origin or route-to-title mapping.
 * - Preserves previous filters, query params, active tabs, and pagination state.
 * - Safely falls back to `fallbackHref` and `fallbackLabel` if direct visit, refresh, `fixedFallback`, or no in-app history exists.
 */
export function BackButton({
  fallbackHref,
  fallbackLabel,
  label,
  fixedFallback = false,
  className = "back-pill",
  children,
}: BackButtonProps) {
  const router = useRouter();
  const pathname = usePathname();

  const [displayText, setDisplayText] = useState<string>(() => {
    return formatBackLabel(label || fallbackLabel || getRouteTitle(fallbackHref));
  });
  const [canGoBack, setCanGoBack] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined") return;

    // Track current route in the application navigation stack
    if (pathname) {
      trackRoute(pathname, window.location.search);
    }

    // Fixed fallback (top-level workspace pages), direct visit, or browser refresh -> fallback
    if (fixedFallback || isReloadNavigation()) {
      setDisplayText(formatBackLabel(fallbackLabel || getRouteTitle(fallbackHref) || label));
      setCanGoBack(false);
      return;
    }

    const prevRoute = getPreviousRoute();
    if (prevRoute) {
      const derivedTitle = getRouteTitle(prevRoute);
      if (derivedTitle) {
        setDisplayText(formatBackLabel(derivedTitle));
        setCanGoBack(true);
        return;
      }
    }

    // Direct visit or no mapped previous route -> use fallback
    setDisplayText(formatBackLabel(fallbackLabel || getRouteTitle(fallbackHref) || label));
    setCanGoBack(false);
  }, [pathname, fallbackHref, fallbackLabel, label, fixedFallback]);

  const handleClick = useCallback(
    (e: MouseEvent<HTMLAnchorElement>) => {
      // Allow user to use modifier keys to open fallback in new tab/window
      if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || e.button !== 0) {
        return;
      }

      e.preventDefault();

      if (typeof window !== "undefined") {
        const hasHistoryLength = window.history.length > 1;
        const historyIdx = typeof window.history.state?.idx === "number" ? window.history.state.idx : null;

        // If we can go back and are not on a refreshed/direct page
        if (canGoBack && ((historyIdx !== null && historyIdx > 0) || hasHistoryLength)) {
          router.back();
          return;
        }
      }

      router.push(fallbackHref);
    },
    [router, fallbackHref, canGoBack]
  );

  return (
    <Link
      href={fallbackHref}
      onClick={handleClick}
      className={className}
      aria-label={typeof displayText === "string" ? displayText : "Back"}
    >
      {children || (
        <>
          <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            className="h-4 w-4 shrink-0"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          <span>{displayText}</span>
        </>
      )}
    </Link>
  );
}
