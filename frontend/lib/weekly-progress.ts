export type WeeklyStatus = "AVAILABLE" | "UPCOMING" | "ARCHIVED" | "PENDING_MENTOR_REVIEW" | "APPROVED" | "REJECTED";
export type WeeklyEvent = { revision: number; eventType: string; actorId: number; actorEmail: string; performedAt: string; workDescription: string; reason: string | null };
export type InternshipWeek = {
  id: number; internshipId: number; studentUserId: number; studentName: string; uid: string | null;
  companyName: string; weekNumber: number; periodStart: string; periodEnd: string; active: boolean;
  submissionStatus: "SUBMITTED" | "NOT_SUBMITTED"; status: WeeklyStatus; workDescription: string | null;
  revision: number; submittedAt: string | null; reviewerId: number | null; reviewerEmail: string | null;
  reviewedAt: string | null; rejectionReason: string | null; attendanceSubmitted: number;
  attendancePresent: number; attendanceGpsVerified: number; history: WeeklyEvent[];
};
export type WeeklyOverview = { internshipId: number; companyName: string; internshipStart: string; internshipEnd: string; totalWeeks: number; calculationRule: string; weeks: InternshipWeek[] };
export type WeeklyWarning = { internshipId: number; studentUserId: number; studentName: string; message: string };
export type WeeklyMonitoring = { weeks: InternshipWeek[]; warnings: WeeklyWarning[] };
export const weeklyStatusLabel = (status: WeeklyStatus) => ({ AVAILABLE: "Not submitted", UPCOMING: "Upcoming", ARCHIVED: "Archived period", PENDING_MENTOR_REVIEW: "Pending faculty mentor review", APPROVED: "Approved", REJECTED: "Rejected" })[status];

export function groupWeeklyStudents(weeks: InternshipWeek[]) {
  const students = new Map<number, InternshipWeek[]>();
  for (const week of weeks) students.set(week.studentUserId, [...(students.get(week.studentUserId) ?? []), week]);
  return [...students.entries()];
}
