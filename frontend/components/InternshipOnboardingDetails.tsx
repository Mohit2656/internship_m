"use client";

import { useState } from "react";
import { DocumentPreviewModal } from "@/components/DocumentPreviewModal";
import { LastStudentUpdate } from "@/components/LastStudentUpdate";
import { WorkflowBadge } from "@/components/WorkflowBadge";
import { downloadDocument, fileSize, InternshipDocument, InternshipOnboarding } from "@/lib/internship-onboarding";

export function InternshipOnboardingDetails({ onboarding, documentBasePath, studentId }: { onboarding: InternshipOnboarding; documentBasePath?: string; studentId?: number }) {
  const [downloadError, setDownloadError] = useState("");
  const [preview, setPreview] = useState<InternshipDocument | null>(null);
  const documentsPath = documentBasePath ?? `/admin/students/${studentId}/internship-onboarding/documents`;
  async function download(documentId: number, fileName: string) {
    setDownloadError("");
    try { await downloadDocument(`${documentsPath}/${documentId}/download`, fileName); }
    catch { setDownloadError("The document could not be downloaded."); }
  }
  return <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Internship Onboarding</p><h2 className="mt-2 text-xl font-bold">Preparation workflow</h2></div><WorkflowBadge status={onboarding.workflowStatus}/></div>
    <LastStudentUpdate value={onboarding.lastStudentUpdatedAt}/>
    <dl className="mt-6 grid gap-4 sm:grid-cols-4"><Summary label="Internship Secured" value={onboarding.id ? "Yes" : "No"}/><Summary label="Source" value={onboarding.sourceLabel ?? "Not selected"}/><Summary label="Documents Completed" value={`${onboarding.documentsCompleted} / ${onboarding.documentsRequired}`}/><Summary label="Internship Diary" value={onboarding.diaryIssued ? "Issued" : "Not issued"}/></dl>
    <div className="mt-7"><h3 className="font-bold">Internship Documents</h3><div className="mt-3 divide-y divide-slate-100 rounded-xl border border-slate-200">{onboarding.documents.map(document => <div key={document.documentType} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"><div><p className="text-sm font-semibold">{document.documentTypeLabel}</p><p className="mt-1 text-xs text-slate-500">{document.uploaded ? `Uploaded · ${document.originalFileName} · ${fileSize(document.fileSize)}` : "Not Uploaded"}</p></div>{document.uploaded && <LastStudentUpdate value={document.uploadedAt} className="text-xs text-slate-500"/>}{document.uploaded && document.id && <div className="flex gap-2"><button data-button-variant="outline" onClick={() => setPreview(document)} className="text-xs">View</button><button data-button-variant="outline" onClick={() => download(document.id!, document.originalFileName!)} className="text-xs">Download</button></div>}</div>)}</div></div>
    {downloadError && <p className="mt-3 text-sm text-red-700">{downloadError}</p>}
    <div className="mt-6 border-t border-slate-200 pt-5"><p className="text-sm font-semibold">Diary Confirmation Date</p><p className="mt-1 text-sm text-slate-600">{onboarding.diaryIssuedAt ? new Date(onboarding.diaryIssuedAt).toLocaleString() : "—"}</p></div>
    {preview?.id && preview.originalFileName && <DocumentPreviewModal contentType={preview.contentType} fileName={preview.originalFileName} viewUrl={`${documentsPath}/${preview.id}/view`} downloadUrl={`${documentsPath}/${preview.id}/download`} onClose={() => setPreview(null)}/>}
  </section>;
}

function Summary({ label, value }: { label: string; value: string }) { return <div><dt className="text-xs font-bold uppercase tracking-wide text-slate-500">{label}</dt><dd className="mt-1 text-sm font-semibold text-slate-900">{value}</dd></div>; }
