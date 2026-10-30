export const STUDENT_ACCOUNTS_CHANGED = "ims:student-accounts-changed";
export type StudentDeletionPreview = { studentId: number; name: string; uid: string | null; email: string; active: boolean; dependencies: Record<string, number>; policy: string; applicationsNote: string };
export type StudentDeletionResult = { studentId: number; active: false; initiatedBy: number; deactivatedAt: string; reason: string };
export function publishStudentAccountsChanged() {
  window.dispatchEvent(new Event(STUDENT_ACCOUNTS_CHANGED));
  try { window.localStorage.setItem(STUDENT_ACCOUNTS_CHANGED, `${Date.now()}-${Math.random()}`); } catch { /* Focus refresh remains available when browser storage is disabled. */ }
}
