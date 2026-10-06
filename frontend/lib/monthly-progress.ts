import { api } from "@/lib/api";

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
  geoVerificationStatus: "VERIFIED" | "MISSING_GPS" | "INVALID_GPS" | "METADATA_READ_FAILED" | null;
  geoVerificationMessage: string | null;
  captureTimeStatus: "PRESENT" | "MISSING_CAPTURE_TIME" | "INVALID_METADATA" | null;
  gpsAltitude: number | null;
  gpsTimestamp: string | null;
  cameraMake: string | null;
  cameraModel: string | null;
  imageWidth: number | null;
  imageHeight: number | null;
  exifPresent: boolean | null;
  gpsPresent: boolean | null;
  imageSha256: string | null;
  exifCapturedAt: string | null;
  exifLatitude: number | null;
  exifLongitude: number | null;
  locationName: string | null;
  submittedAt: string;
};

export type MonthlyProgressPeriod = {
  monthNumber: number;
  periodStart: string;
  periodEnd: string;
  status: "AVAILABLE" | "UPCOMING" | "SUBMITTED";
  report: MonthlyProgressReport | null;
};

export type MonthlyProgressOverview = {
  internshipId: number;
  companyName: string;
  internshipMode: string;
  internshipStart: string;
  internshipEnd: string;
  periods: MonthlyProgressPeriod[];
};

export const formatDate = (value: string) => new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(`${value}T00:00:00`));
export const formatDateTime = (value: string | null) => value ? new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "Not available";

export async function openPrivateFile(url: string, filename?: string) {
  const response = await api.get<Blob>(url, { responseType: "blob" });
  const objectUrl = URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = objectUrl;
  if (filename) link.download = filename;
  else link.target = "_blank";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 30_000);
}
