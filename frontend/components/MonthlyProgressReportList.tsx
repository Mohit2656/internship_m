"use client";

import { ProgressPhotoLocation } from "@/components/ProgressPhotoLocation";
import { ProgressPhotoVerification } from "@/components/ProgressPhotoVerification";
import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { ApiError, api } from "@/lib/api";
import { formatDate, formatDateTime, MonthlyProgressReport, openPrivateFile } from "@/lib/monthly-progress";

export function MonthlyProgressReportList({ scope }: { scope: "mentor" | "admin" }) {
  const [reports, setReports] = useState<MonthlyProgressReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");

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
    } catch { setError(`Unable to open the report ${kind}.`); } finally { setBusy(""); }
  }

  if (loading) return <p className="mt-8 text-sm text-slate-500">Loading monthly progress reports…</p>;
  return <>
    {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}
    {reports.length === 0 ? <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-8 text-sm text-slate-500">No monthly progress reports are available.</section> :
      <div className="mt-8 space-y-5">{reports.map(report => <article key={report.id} className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-7">
        <div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.16em] text-blue-700">Month {report.reportMonthNumber}</p><h2 className="mt-2 text-xl font-bold text-slate-950">{report.studentName ?? "Student profile incomplete"}</h2><p className="mt-1 text-sm text-slate-500">{report.uid ?? "UID unavailable"} · {report.companyName}</p></div><span className="rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-bold text-emerald-700">Submitted</span></div>
        <ProgressPhotoVerification report={report}/><ProgressPhotoLocation report={report}/><dl className="mt-5 grid gap-4 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2 lg:grid-cols-3"><div><dt className="text-slate-500">Reporting period</dt><dd className="mt-1 font-semibold">{formatDate(report.periodStart)} – {formatDate(report.periodEnd)}</dd></div><div><dt className="text-slate-500">Submitted</dt><dd className="mt-1 font-semibold">{formatDateTime(report.submittedAt)}</dd></div><div><dt className="text-slate-500">Capture date</dt><dd className="mt-1 font-semibold">{formatDateTime(report.exifCapturedAt)}</dd></div></dl>
        <div className="mt-5"><h3 className="text-sm font-bold text-slate-900">Work performed during the month</h3><p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-slate-700">{report.workDescription}</p></div>
        <div className="mt-6 flex flex-wrap gap-3"><button onClick={() => file(report, "photo")} disabled={busy === `${report.id}-photo`} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-semibold disabled:opacity-50">{busy === `${report.id}-photo` ? "Opening…" : "View Photograph"}</button><button onClick={() => file(report, "pdf")} disabled={busy === `${report.id}-pdf`} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50">{busy === `${report.id}-pdf` ? "Preparing…" : "Download PDF"}</button></div>
      </article>)}</div>}
  </>;
}
