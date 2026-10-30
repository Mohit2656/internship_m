export type CompletionEvent = { revision: number; previousStatus: string; finalStatus: string; reviewerId: number; reviewer: string; performedAt: string; remarks: string | null };
export type InternshipCompletion = {
  internshipId: number; studentId: number; studentName: string; uid: string | null; companyName: string; startDate: string; endDate: string; totalWeeks: number;
  sessionId: number | null; sessionName: string | null; status: string; revision: number; completedAt: string | null; reviewerId: number | null; reviewer: string | null; remarks: string | null;
  requiredReports: number; submittedReports: number; pendingReports: number; approvedReports: number; weeklyRequired: number; monthlyRequired: number;
  mentorApprovalStatus: string; finalProjectReportStatus: string; eligible: boolean; blockers: string[]; history: CompletionEvent[];
};
export type CompletionStats = { totalStudents: number; totalInternships: number; completedInternships: number; activeInternships: number; completionPending: number; completionPercentage: number };
export type CompletionPage = { sessionId: number; stats: CompletionStats; content: { studentId: number; student_name: string; uid: string | null; company: string; mentor: string | null; startDate: string; endDate: string; completionDate: string | null; reviewer: string | null; status: string }[]; totalElements: number; page: number };
export const completionLabel = (status: string) => status.replaceAll("_", " ");
export const completionReportFields = ["studentName", "uid", "academicSession", "companyName", "mentorName", "startDate", "endDate", "completionStatus", "completionDate", "completionReviewer"];
export function completionReportFilters(sessionId: number, status: string, search: string): Record<string, string> {
  return { academicSessionId: String(sessionId), completionStatus: status || "ALL", ...(search.trim() ? { completionSearch: search.trim() } : {}) };
}
