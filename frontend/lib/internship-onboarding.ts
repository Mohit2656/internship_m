import { api } from "@/lib/api";

export type WorkflowStatus = "NOT_SECURED" | "INTERNSHIP_SECURED" | "DOCUMENTS_PENDING" | "DOCUMENTS_COMPLETE" | "DIARY_ISSUED" | "INTERNSHIP_DETAILS_COMPLETE";
export type InternshipSource = "DEPARTMENT" | "CDC" | "SELF";
export type DocumentType = "OFFER_LETTER" | "UNDERTAKING" | "DEPARTMENT_NOC" | "STUDENT_NOC";

export type InternshipDocument = {
  id: number | null; documentType: DocumentType; documentTypeLabel: string; uploaded: boolean;
  originalFileName: string | null; contentType: string | null; fileSize: number | null; uploadedAt: string | null;
};

export type InternshipOnboarding = {
  id: number | null; workflowStatus: WorkflowStatus; source: InternshipSource | null; sourceLabel: string | null;
  documentsCompleted: number; documentsRequired: number; documents: InternshipDocument[]; diaryIssued: boolean;
  securedAt: string | null; documentsCompletedAt: string | null; diaryIssuedAt: string | null;
  createdAt: string | null; updatedAt: string | null;
};

export const sourceOptions: { value: InternshipSource; label: string }[] = [
  { value: "DEPARTMENT", label: "Department" }, { value: "CDC", label: "CDC" }, { value: "SELF", label: "Self" },
];

export const workflowLabels: Record<WorkflowStatus, string> = {
  NOT_SECURED: "Not Secured", INTERNSHIP_SECURED: "Internship Secured", DOCUMENTS_PENDING: "Documents Pending",
  DOCUMENTS_COMPLETE: "Documents Complete", DIARY_ISSUED: "Diary Issued", INTERNSHIP_DETAILS_COMPLETE: "Internship Details Complete",
};

export async function downloadDocument(url: string, fileName: string) {
  const response = await api.get<Blob>(url, { responseType: "blob" });
  const objectUrl = URL.createObjectURL(response.data);
  const anchor = document.createElement("a"); anchor.href = objectUrl; anchor.download = fileName; anchor.click();
  URL.revokeObjectURL(objectUrl);
}

export function fileSize(bytes: number | null) { return bytes == null ? "" : bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`; }
