import { api } from "@/lib/api";

export type MonthlyProgressStatus = "PENDING_MENTOR_REVIEW" | "APPROVED" | "REJECTED";
export function monthlyStatusLabel(status: MonthlyProgressStatus | undefined) {
  return status === "APPROVED" ? "Approved" : status === "REJECTED" ? "Rejected" : "Pending faculty mentor review";
}
export function monthlyStatusClass(status: MonthlyProgressStatus | undefined) {
  return status === "APPROVED" ? "bg-emerald-50 text-emerald-700" : status === "REJECTED" ? "bg-red-50 text-red-700" : "bg-amber-50 text-amber-800";
}

export type MonthlyProgressReport = {
  id: number;
  studentUserId: number;
  studentName: string | null;
  uid: string | null;
  branch: string | null;
  semester: number | null;
  internshipId: number;
  companyName: string;
  internshipMode: string;
  reportMonthNumber: number;
  periodStart: string;
  periodEnd: string;
  workDescription: string;
  photoOriginalName: string;
  photoMimeType: string;
  photoSize: number;
  status: MonthlyProgressStatus;
  revision: number;
  reviewerId: number | null;
  reviewerEmail: string | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  imageWidth: number | null;
  imageHeight: number | null;
  imageSha256: string | null;
  submittedAt: string;
};

export type MonthlyProgressPeriod = {
  monthNumber: number;
  periodStart: string;
  periodEnd: string;
  status: "AVAILABLE" | "UPCOMING" | MonthlyProgressStatus;
  report: MonthlyProgressReport | null;
};

export type MonthlyProgressOverview = {
  maxPhotoSizeBytes: number;
  internshipId: number;
  companyName: string;
  internshipMode: string;
  internshipStart: string;
  internshipEnd: string;
  periods: MonthlyProgressPeriod[];
};

export const formatDate = (value: string) => new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(`${value}T00:00:00`));
export const formatDateTime = (value: string | null) => {
  const date = value ? new Date(value) : null;
  return date && !Number.isNaN(date.getTime())
    ? `${new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Kolkata" }).format(date)} IST`
    : "Not available";
};

export async function openPrivateFile(url: string, filename?: string) {
  // Open during the click gesture, before the authenticated fetch loses user activation.
  const preview = filename ? null : window.open("about:blank", "_blank");
  if (!filename && !preview) throw new Error("PHOTO_WINDOW_BLOCKED");
  if (preview) { preview.opener = null; preview.document.title = "Loading photograph"; preview.document.body.textContent = "Loading photograph..."; }
  let objectUrl: string | null = null;
  try {
    const response = await api.get<Blob>(url, { responseType: "blob" });
    objectUrl = URL.createObjectURL(response.data);
    if (preview) {
      if (preview.closed) { URL.revokeObjectURL(objectUrl); return; }
      preview.location.replace(objectUrl);
    } else {
      const link = document.createElement("a");
      link.href = objectUrl; link.download = filename!;
      document.body.appendChild(link); link.click(); link.remove();
    }
    const storedUrl = objectUrl;
    window.setTimeout(() => URL.revokeObjectURL(storedUrl), 60_000);
  } catch (error) {
    preview?.close();
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    throw error;
  }
}

export function privateFileErrorMessage(cause: unknown, kind: "photo" | "pdf") {
  if (cause instanceof Error && cause.message === "PHOTO_WINDOW_BLOCKED") return "Allow pop-ups for this site, then select View Photograph again.";
  const status = (cause as { response?: { status?: number } } | null)?.response?.status;
  if (status === 404) return "The report or its photograph is no longer available. Refresh this page.";
  if (status === 401) return "Your session has expired. Sign in again to view the photograph or PDF.";
  if (status === 403) return "You do not have permission to view this report.";
  return `Unable to open the report ${kind}.`;
}
