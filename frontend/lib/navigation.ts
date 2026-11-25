/**
 * Shared in-app navigation history tracking and route title mapping.
 */

const ROUTE_TITLES: Array<{ pattern: RegExp; title: string }> = [
  // Admin routes
  { pattern: /^\/admin\/reports(\/.*)?$/, title: "Reports Analysis" },
  { pattern: /^\/admin\/students$/, title: "Student Directory" },
  { pattern: /^\/admin\/attendance(\/.*)?$/, title: "Attendance" },
  { pattern: /^\/admin\/dashboard(\/.*)?$/, title: "Dashboard" },
  { pattern: /^\/admin\/internship-completions(\/.*)?$/, title: "Internship Completions" },
  { pattern: /^\/admin\/second-internship-requests(\/.*)?$/, title: "Second Internship Requests" },
  { pattern: /^\/admin\/opportunities(\/.*)?$/, title: "Opportunities" },
  { pattern: /^\/admin\/weekly-progress(\/.*)?$/, title: "Weekly Progress" },
  { pattern: /^\/admin\/monthly-progress(\/.*)?$/, title: "Monthly Progress" },
  { pattern: /^\/admin\/student-accounts(\/.*)?$/, title: "Student Accounts" },
  { pattern: /^\/admin\/mentor-accounts(\/.*)?$/, title: "Mentor Accounts" },
  { pattern: /^\/admin\/settings(\/.*)?$/, title: "Settings" },

  // Mentor routes
  { pattern: /^\/mentor\/attendance(\/.*)?$/, title: "Attendance" },
  { pattern: /^\/mentor\/students$/, title: "Student Directory" },
  { pattern: /^\/mentor\/dashboard(\/.*)?$/, title: "Dashboard" },
  { pattern: /^\/mentor\/weekly-progress(\/.*)?$/, title: "Weekly Progress" },
  { pattern: /^\/mentor\/monthly-progress(\/.*)?$/, title: "Monthly Progress" },

  // Student routes
  { pattern: /^\/student\/dashboard(\/.*)?$/, title: "Dashboard" },
  { pattern: /^\/student\/opportunities(\/.*)?$/, title: "Opportunities" },
  { pattern: /^\/student\/internship(\/.*)?$/, title: "Internship" },
  { pattern: /^\/student\/attendance(\/.*)?$/, title: "Attendance" },
  { pattern: /^\/student\/weekly-progress(\/.*)?$/, title: "Weekly Progress" },
  { pattern: /^\/student\/monthly-progress(\/.*)?$/, title: "Monthly Progress" },
  { pattern: /^\/student\/progress-reports(\/.*)?$/, title: "Progress Reports" },
  { pattern: /^\/student\/professional-profile(\/.*)?$/, title: "Professional Profile" },
];

/**
 * Resolves a human-readable title for a given application route.
 */
export function getRouteTitle(path?: string | null): string {
  if (!path) return "";
  const [pathname, search] = path.split("?");
  const cleanPath = pathname.split("#")[0].replace(/\/+$/, "") || "/";

  // Check query parameters for specific context (e.g. ?category= on dashboard)
  if (cleanPath === "/admin/dashboard" && search && search.includes("category=")) {
    return "Student Overview";
  }

  for (const { pattern, title } of ROUTE_TITLES) {
    if (pattern.test(cleanPath)) {
      return title;
    }
  }

  return "";
}

/**
 * Formats a clean "Back to <Destination>" label without duplicate prefixes or arrows.
 */
export function formatBackLabel(title?: string, fallbackLabel?: string): string {
  let target = title?.trim();
  if (!target && fallbackLabel) {
    target = fallbackLabel.trim();
  }
  if (!target) {
    return "Back";
  }

  // Strip leading arrows and whitespace (e.g. "← ", "<- ")
  target = target.replace(/^[←\s\-\<\>]+/, "").trim();

  // If already prefixed with "Back to "
  if (/^back\s+to\s+/i.test(target)) {
    return "Back to " + target.replace(/^back\s+to\s+/i, "").trim();
  }
  if (/^back$/i.test(target)) {
    return "Back";
  }

  return `Back to ${target}`;
}

/**
 * Detects whether the current page load is a browser reload/refresh.
 */
export function isReloadNavigation(): boolean {
  if (typeof window === "undefined") return false;
  try {
    const navEntries = performance.getEntriesByType("navigation");
    if (navEntries.length > 0) {
      return (navEntries[0] as PerformanceNavigationTiming).type === "reload";
    }
    return (window.performance as unknown as { navigation?: { type?: number } })?.navigation?.type === 1;
  } catch {
    return false;
  }
}

/**
 * Tracks the current application route in sessionStorage.
 */
export function trackRoute(pathname: string, search: string = ""): void {
  if (typeof window === "undefined" || !pathname) return;
  try {
    const fullUrl = pathname + (search ? (search.startsWith("?") ? search : `?${search}`) : "");
    const raw = sessionStorage.getItem("ims_nav_stack");
    const stack: string[] = raw ? JSON.parse(raw) : [];

    // Avoid duplicate if same as current top
    if (stack.length === 0 || stack[stack.length - 1] !== fullUrl) {
      // If navigating back to previous entry, pop top
      if (stack.length >= 2 && stack[stack.length - 2] === fullUrl) {
        stack.pop();
      } else {
        stack.push(fullUrl);
      }
      if (stack.length > 25) stack.shift();
      sessionStorage.setItem("ims_nav_stack", JSON.stringify(stack));
    }
  } catch {
    // Storage unavailable or quota exceeded
  }
}

/**
 * Retrieves the previous in-app route from the navigation stack, or same-origin referrer.
 * Returns null if direct visit, refresh, or no in-app history exists.
 */
export function getPreviousRoute(): string | null {
  if (typeof window === "undefined") return null;

  // Direct page visit or browser refresh -> fallback
  if (isReloadNavigation()) {
    return null;
  }

  // Check history index (Next.js router history state)
  const historyIdx = typeof window.history.state?.idx === "number" ? window.history.state.idx : null;
  if (historyIdx === 0 && (!document.referrer || !isSameOriginReferrer(document.referrer))) {
    return null;
  }

  // 1. Check in-app navigation stack
  try {
    const raw = sessionStorage.getItem("ims_nav_stack");
    if (raw) {
      const stack: string[] = JSON.parse(raw);
      if (stack.length >= 2) {
        return stack[stack.length - 2];
      }
    }
  } catch {
    // Fall back to referrer
  }

  // 2. Check same-origin document.referrer
  if (document.referrer && isSameOriginReferrer(document.referrer)) {
    try {
      const refUrl = new URL(document.referrer);
      if (refUrl.pathname !== window.location.pathname) {
        return refUrl.pathname + refUrl.search;
      }
    } catch {
      // Ignore URL parse error
    }
  }

  return null;
}

function isSameOriginReferrer(referrer: string): boolean {
  try {
    const refUrl = new URL(referrer);
    return refUrl.origin === window.location.origin;
  } catch {
    return false;
  }
}
