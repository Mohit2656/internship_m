"use client";

const STORAGE_KEY = "ims_student_selected_internship";

export function getSelectedInternship(): 1 | 2 {
  if (typeof window === "undefined") return 1;
  try {
    const val = sessionStorage.getItem(STORAGE_KEY);
    return val === "2" ? 2 : 1;
  } catch {
    return 1;
  }
}

export function setSelectedInternship(tab: 1 | 2): void {
  if (typeof window === "undefined") return;
  try {
    sessionStorage.setItem(STORAGE_KEY, String(tab));
  } catch {}
}

export type InternshipStatusBadge = "Active" | "Upcoming" | "Pending Approval" | "Approved" | "Rejected" | "Completed" | "Not Requested";

export function getStatusBadgeClass(status: InternshipStatusBadge): string {
  switch (status) {
    case "Active":
      return "bg-emerald-50 text-emerald-800 border-emerald-300";
    case "Upcoming":
      return "bg-blue-50 text-blue-800 border-blue-300";
    case "Pending Approval":
      return "bg-amber-50 text-amber-800 border-amber-300";
    case "Approved":
      return "bg-teal-50 text-teal-800 border-teal-300";
    case "Rejected":
      return "bg-rose-50 text-rose-800 border-rose-300";
    case "Completed":
      return "bg-slate-100 text-slate-800 border-slate-300";
    default:
      return "bg-slate-100 text-slate-600 border-slate-200";
  }
}

export function deriveInternshipStatus(
  details: { startDate?: string; endDate?: string } | null,
  locationStatus?: string | null,
  requestStatus?: string | null
): InternshipStatusBadge {
  if (details) {
    const today = new Date().toISOString().slice(0, 10);
    if (details.endDate && details.endDate < today) {
      return "Completed";
    }
    if (locationStatus === "REJECTED") {
      return "Rejected";
    }
    if (locationStatus === "PENDING" || locationStatus === "PENDING_MENTOR_APPROVAL") {
      return "Pending Approval";
    }
    if (details.startDate && details.startDate > today) {
      return "Upcoming";
    }
    return "Active";
  }

  if (requestStatus === "PENDING") return "Pending Approval";
  if (requestStatus === "REJECTED") return "Rejected";
  if (requestStatus === "APPROVED") return "Approved";
  return "Not Requested";
}

