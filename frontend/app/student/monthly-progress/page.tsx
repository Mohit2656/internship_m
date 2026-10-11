"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { AxiosError } from "axios";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { ApiError, api } from "@/lib/api";
import { formatDate, formatDateTime, monthlyStatusLabel, monthlyStatusClass, MonthlyProgressOverview, MonthlyProgressPeriod, openPrivateFile, privateFileErrorMessage } from "@/lib/monthly-progress";

export default function StudentMonthlyProgressPage() {
  const [overview, setOverview] = useState<MonthlyProgressOverview | null>(null);
  const [selected, setSelected] = useState<MonthlyProgressPeriod | null>(null);
  const [description, setDescription] = useState("");
  const [photo, setPhoto] = useState<File | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const maxPhotoBytes = overview?.maxPhotoSizeBytes ?? 20 * 1024 * 1024;
  const maxPhotoMiB = maxPhotoBytes / (1024 * 1024);

  function load() {
    setLoading(true); setError("");
    api.get<MonthlyProgressOverview>("/student/monthly-progress").then(response => setOverview(response.data))
      .catch((cause: AxiosError<ApiError>) => setError(cause.response?.data?.message ?? "Unable to load monthly progress."))
      .finally(() => setLoading(false));
  }
  useEffect(load, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!selected || !photo) { setError("Choose a JPG, JPEG or PNG photograph before submitting."); return; }
    if (submitting) return;
    if (photo.size > maxPhotoBytes) { setError(`Photographs must be ${maxPhotoMiB} MiB or smaller.`); return; }
    setSubmitting(true); setError(""); setNotice("");
    const body = new FormData(); body.append("workDescription", description); body.append("photo", photo);
    try {
      if (selected.report) body.append("revision", String(selected.report.revision));
      const url = selected.report ? `/student/monthly-progress/${selected.report.id}/resubmit` : `/student/monthly-progress/${selected.monthNumber}`;
      await api.post(url, body, { headers: { "Content-Type": "multipart/form-data" }, timeout: 120_000 });
      setNotice("Report uploaded successfully and sent to your faculty mentor for review. It is not yet approved.");
      setSelected(null); setDescription(""); setPhoto(null); load();
    } catch (cause) { setError((cause as AxiosError<ApiError>).response?.data?.message ?? "The monthly progress report could not be submitted."); }
    finally { setSubmitting(false); }
  }
  async function file(id: number, kind: "photo" | "pdf", uid?: string | null, month?: number) {
    setBusy(`${id}-${kind}`); setError("");
    try { await openPrivateFile(`/student/monthly-progress/${id}/${kind}`, kind === "pdf" ? `Monthly_Progress_${uid ?? "Student"}_Month_${month}.pdf` : undefined); }
    catch (cause) { setError(privateFileErrorMessage(cause, kind)); } finally { setBusy(""); }
  }

  return <ProtectedRoute role="STUDENT"><AppFrame><main className="mx-auto max-w-5xl px-5 py-10 sm:px-8">
    <Link href="/student/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link>
    <p className="mt-6 text-sm font-semibold text-blue-700">Internship records</p><h1 className="mt-2 text-3xl font-bold tracking-tight">Monthly Progress</h1><p className="mt-2 text-slate-600">Upload a report for each internship month. Your assigned faculty mentor reviews it before approval.</p>
    {notice && <p role="status" className="mt-6 rounded-xl bg-blue-50 p-4 text-sm text-blue-800">{notice}</p>}
    {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}
    {loading ? <p className="mt-8 text-sm text-slate-500">Loading reporting periods…</p> : overview && <>
      <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6"><p className="text-sm text-slate-500">Registered internship</p><h2 className="mt-1 text-xl font-bold">{overview.companyName}</h2><p className="mt-2 text-sm text-slate-600">{overview.internshipMode} · {formatDate(overview.internshipStart)} – {formatDate(overview.internshipEnd)}</p></section>
      <div className="mt-5 space-y-4">{overview.periods.map(period => <article key={period.monthNumber} className="rounded-2xl border border-slate-200 bg-white p-6">
        <div className="flex flex-wrap items-start justify-between gap-4"><div><h2 className="text-lg font-bold">Month {period.monthNumber}</h2><p className="mt-1 text-sm text-slate-600">{formatDate(period.periodStart)} – {formatDate(period.periodEnd)}</p></div><span className={`rounded-full px-3 py-1.5 text-xs font-bold ${period.report ? monthlyStatusClass(period.report.status) : period.status === "UPCOMING" ? "bg-slate-100 text-slate-600" : "bg-blue-50 text-blue-700"}`}>{period.report ? monthlyStatusLabel(period.report.status) : period.status === "UPCOMING" ? "Upcoming" : "Available"}</span></div>
        {period.report ? <div className="mt-5"><p className="whitespace-pre-wrap text-sm leading-6 text-slate-700">{period.report.workDescription}</p>{period.report.status === "PENDING_MENTOR_REVIEW" && <p className="mt-4 text-sm text-amber-800">This report is not yet approved. It is awaiting faculty mentor review.</p>}{period.report.rejectionReason && <p className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">Rejection reason: {period.report.rejectionReason}</p>}<dl className="mt-4 grid gap-3 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2"><div><dt className="text-slate-500">Uploaded</dt><dd className="mt-1 font-semibold">{formatDateTime(period.report.submittedAt)}</dd></div><div><dt className="text-slate-500">Mentor decision</dt><dd className="mt-1 font-semibold">{period.report.reviewedAt ? `${period.report.reviewerEmail ?? "Faculty Mentor"} · ${formatDateTime(period.report.reviewedAt)}` : "Awaiting review"}</dd></div></dl><div className="mt-4 flex flex-wrap gap-3">{period.report.status === "REJECTED" && <button onClick={() => { setSelected(period); setDescription(period.report!.workDescription); setPhoto(null); setNotice(""); setError(""); }} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-semibold text-white">Correct and Resubmit</button>}<button onClick={() => file(period.report!.id, "photo")} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-semibold">{busy === `${period.report.id}-photo` ? "Opening…" : "View Photograph"}</button><button onClick={() => file(period.report!.id, "pdf", period.report!.uid, period.monthNumber)} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-semibold text-white">{busy === `${period.report.id}-pdf` ? "Preparing…" : "Download PDF"}</button></div></div> : period.status === "AVAILABLE" ? <button onClick={() => { setSelected(period); setDescription(""); setPhoto(null); setNotice(""); setError(""); }} className="mt-5 rounded-xl bg-blue-700 px-5 py-3 text-sm font-semibold text-white">Submit Report</button> : <p className="mt-4 text-sm text-slate-500">Submission becomes available when this reporting period begins.</p>}
      </article>)}</div>
    </>}
    {selected && <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-950/50 p-4"><div className="mx-auto my-8 max-w-xl rounded-2xl bg-white p-6 shadow-2xl sm:p-8"><div className="flex items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-wide text-blue-700">Monthly Progress Report</p><h2 className="mt-2 text-2xl font-bold">Draft — Month {selected.monthNumber}</h2><p className="mt-1 text-sm text-slate-600">{formatDate(selected.periodStart)} – {formatDate(selected.periodEnd)}</p></div><button type="button" onClick={() => setSelected(null)} className="rounded-lg px-3 py-2 text-sm font-semibold text-slate-500">Close</button></div><form onSubmit={submit} className="mt-7 space-y-5">{error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}<label className="block"><span className="mb-2 block text-sm font-semibold">Photograph</span><input type="file" required accept=".jpg,.jpeg,.png,image/jpeg,image/png" onChange={event => {
          const chosen = event.target.files?.[0] ?? null;
          if (chosen && (chosen.size > maxPhotoBytes || !/\.(jpe?g|png)$/i.test(chosen.name))) {
            setPhoto(null); event.target.value = ""; setError(`Choose a JPG, JPEG or PNG photograph of ${maxPhotoMiB} MiB or smaller.`);
          } else { setPhoto(chosen); setError(""); }
        }} className="block w-full rounded-xl border border-slate-300 p-3 text-sm"/><span className="mt-2 block text-xs text-slate-500">JPG, JPEG or PNG, maximum {maxPhotoMiB} MiB. GPS metadata is not required. The server validates image integrity.</span></label><label className="block"><span className="mb-2 block text-sm font-semibold">Work performed during this month</span><textarea required maxLength={3000} rows={7} value={description} onChange={event => setDescription(event.target.value)} className="w-full rounded-xl border border-slate-300 p-4 text-sm outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100"/><span className="mt-1 block text-right text-xs text-slate-500">{description.length}/3000</span></label><p className="rounded-xl bg-amber-50 p-3 text-xs leading-5 text-amber-800">Submitting sends this draft for faculty mentor review. Approved reports are read-only; rejected reports may be corrected and resubmitted.</p><button disabled={submitting} className="w-full rounded-xl bg-blue-700 px-5 py-3 font-semibold text-white disabled:opacity-50">{submitting ? "Submitting…" : "Submit Report"}</button></form></div></div>}
  </main></AppFrame></ProtectedRoute>;
}
