/**
 * Institutional Student UID Formatter:
 * - Accepts numeric-only UID values (e.g. "00123", "2024001").
 * - Preserves leading zeros exactly.
 * - Returns an empty string ("") if UID is missing, invalid, or contains letters/email characters.
 * - Never returns email addresses, account IDs, or non-numeric strings.
 */
export function formatNumericUid(uid: unknown): string {
  if (uid === null || uid === undefined) {
    return "";
  }
  const str = String(uid).trim();
  if (/^\d+$/.test(str)) {
    return str;
  }
  return "";
}
