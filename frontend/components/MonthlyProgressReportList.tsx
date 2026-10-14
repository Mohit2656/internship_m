"use client";

import { MentorRecordDelete } from "@/components/MentorRecordDelete";
import { LastStudentUpdate } from "@/components/LastStudentUpdate";
import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { ApiError, api } from "@/lib/api";
import { formatDate, formatDateTime, monthlyStatusLabel, monthlyStatusClass, MonthlyProgressReport, openPrivateFile, privateFileErrorMessage } from "@/lib/monthly-progress";

export function MonthlyProgressReportList({ scope }: { scope: "mentor" | "admin" }) {
  const [reports, setReports] = useState<MonthlyProgressReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [rejecting, setRejecting] = useState<MonthlyProgressReport | null>(null);
  const [reason, setReason] = useState("");

  useEffect(() => {
    api.get<MonthlyProgressReport[]>(`/${scope}/monthly-progress`).then(response => setReports(response.data))
      .catch((cause: AxiosError<ApiError>) => setError(cause.response?.data?.message ?? "Unable to load monthly progress reports."))
      .finally(() => setLoading(false));
  }, [scope]);

  async function file(report: MonthlyProgressReport, kind: "photo" | "pdf") {
    const key = `${report.id}-${kind}`; setBusy(key); setError("");
    try {
      await openPrivateFile(`/${scope}/monthly-progress/${report.id}/${kind}`,
        kind === "pdf" ? `Monthly_Progress_${report.uid ?? "Student"}_Month_${report.reportMonthNumber}.pdf` : undefined);
    } catch (cause) { setError(privateFileErrorMessage(cause, kind)); } finally { setBusy(""); }
  }

  async function review(report: MonthlyProgressReport, approved: boolean) {
    if (busy) return;
    if (!approved && !reason.trim()) { setError("A rejection reason is required."); return; }
    if (approved && !window.confirm(`Approve Month ${report.reportMonthNumber} for ${report.studentName ?? "this student"}? This records your faculty mentor approval.`)) return;
    setBusy(`${report.id}-review`); setError(""); setNotice("");
    try {
      const response = await api.post<MonthlyProgressReport>(`/mentor/monthly-progress/${report.id}/${approved ? "approve" : "reject"}`,
        { revision: report.revision, ...(!approved ? { reason: reason.trim() } : {}) });
      setReports(items => items.map(item => item.id === report.id ? response.data : item));
      setRejecting(null); setReason("");
      setNotice(approved ? "Report approved." : "Report rejected. The student can correct and resubmit it.");
    } catch (cause) { setError((cause as AxiosError<ApiError>).response?.data?.message ?? "The review could not be saved. Refresh the report and try again."); }
    finally { setBusy(""); }
  }

  if (loading) return <p className="mt-8 text-sm text-slate-500">Loading monthly progress reports…</p>;
  return <>
    {notice && <p role="status" className="mt-6 text-sm text-emerald-700">{notice}</p>}
    {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}
    {reports.length === 0 ? <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-8 text-sm text-slate-500">No monthly progress reports are available.</section> :
      <div className="mt-8 space-y-5">{reports.map(report => <article key={report.id} className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-7">
        <div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.16em] text-blue-700">Month {report.reportMonthNumber}</p><h2 className="mt-2 text-xl font-bold text-slate-950">{report.studentName ?? "Student profile incomplete"}</h2><p className="mt-1 text-sm text-slate-500">{report.uid ?? "UID unavailable"} · {report.companyName}</p></div><span className={`rounded-full px-3 py-1.5 text-xs font-bold ${monthlyStatusClass(report.status)}`}>{monthlyStatusLabel(report.status)}</span></div>
        {report.status === "PENDING_MENTOR_REVIEW" && <p className="mt-4 text-sm text-amber-800">Uploaded and awaiting faculty mentor approval. This report is not yet approved.</p>}
        {report.rejectionReason && <p className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">Rejection reason: {report.rejectionReason}</p>}<dl className="report-document mt-5 grid gap-4 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2 lg:grid-cols-3"><div><dt className="text-slate-500">Reporting period</dt><dd className="mt-1 font-semibold">{formatDate(report.periodStart)} – {formatDate(report.periodEnd)}</dd></div><div><dt className="text-slate-500">Submitted</dt><dd className="mt-1 font-semibold">{formatDateTime(report.submittedAt)}</dd></div><div><dt className="text-slate-500">Mentor decision</dt><dd className="mt-1 font-semibold">{report.reviewedAt ? `${report.reviewerEmail ?? "Faculty Mentor"} · ${formatDateTime(report.reviewedAt)}` : "Awaiting review"}</dd></div></dl>
        <LastStudentUpdate value={report.submittedAt}/>
        <div className="report-document mt-5"><h3 className="text-sm font-bold text-slate-900">Work performed during the month</h3><p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-slate-700">{report.workDescription}</p></div>
        <div className="mt-6 flex flex-wrap gap-3">{scope === "mentor" && report.status === "PENDING_MENTOR_REVIEW" && <><button disabled={!!busy} onClick={() => void review(report, true)} className="text-sm">Approve</button><button data-button-variant="outline" disabled={!!busy} onClick={() => { setRejecting(report); setReason(""); setError(""); }} className="text-sm">Reject</button></>}{scope === "mentor" && <MentorRecordDelete kind="monthly-progress" id={report.id} description={`${report.studentName ?? "Student"} (${report.uid ?? report.studentUserId}) - reporting month ${report.reportMonthNumber}, ${formatDate(report.periodStart)} to ${formatDate(report.periodEnd)}`} onDeleted={async () => { setNotice("Monthly progress report deleted."); const response = await api.get<MonthlyProgressReport[]>(`/${scope}/monthly-progress`); setReports(response.data); }} />}<button data-button-variant="outline" onClick={() => file(report, "photo")} disabled={busy === `${report.id}-photo`} className="text-sm">{busy === `${report.id}-photo` ? "Opening…" : "View Photograph"}</button><button onClick={() => file(report, "pdf")} disabled={busy === `${report.id}-pdf`} className="text-sm">{busy === `${report.id}-pdf` ? "Preparing…" : "Download PDF"}</button></div>
      </article>)}</div>}
    {rejecting && <div className="fixed inset-0 z-50 bg-slate-950/50 p-4"><form onSubmit={event => { event.preventDefault(); void review(rejecting, false); }} className="mx-auto my-8 max-w-xl rounded-2xl bg-white p-6 shadow-2xl"><h2 className="text-xl font-bold">Reject Month {rejecting.reportMonthNumber} — {rejecting.studentName ?? "Student"}</h2><p className="mt-2 text-sm text-slate-600">Explain the required corrections. The student will see this reason.</p><label className="mt-5 block text-sm font-semibold">Rejection reason<textarea required maxLength={1000} value={reason} onChange={event => setReason(event.target.value)} rows={4} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label>{error && <p role="alert" className="mt-3 text-sm text-red-700">{error}</p>}<div className="mt-5 flex gap-3"><button disabled={!!busy || !reason.trim()} className="text-sm">{busy ? "Saving…" : "Confirm Rejection"}</button><button data-button-variant="outline" type="button" disabled={!!busy} onClick={() => setRejecting(null)} className="text-sm">Cancel</button></div></form></div>}
  </>;
}
