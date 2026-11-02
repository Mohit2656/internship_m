export type LateAttendanceRequest = {
  id: number;
  studentUserId: number;
  internshipId: number;
  studentName: string;
  uid: string | null;
  attendanceDate: string;
  reason: string;
  supportingDetails: string | null;
  requestedAt: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  reviewerId: number | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  resubmissionAllowed: boolean;
  attendanceId: number | null;
  mentorAssigned: boolean;
  attendanceZone: string;
};
export const lateRequestStatusLabel = (status: LateAttendanceRequest["status"]) => status === "PENDING" ? "Pending" : status === "APPROVED" ? "Approved" : "Rejected";
export const lateRequestStatusClass = (status: LateAttendanceRequest["status"]) => status === "PENDING" ? "bg-amber-50 text-amber-800" : status === "APPROVED" ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-700";
export function lateRequestTimestamp(value: string, zone = "Asia/Kolkata") {
  return new Intl.DateTimeFormat("en-IN", { timeZone: zone, dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
