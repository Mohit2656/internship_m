export function internshipDisplayLabel(
  internshipNumber: number | null | undefined,
  hasApprovedSecondInternship: boolean,
): string {
  if (!hasApprovedSecondInternship) return "Internship";
  return `Internship ${internshipNumber ?? 1}`;
}
