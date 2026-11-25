"use client";

import Link from "next/link";
import { FormEvent, useEffect, useRef, useState } from "react";
import { AxiosError } from "axios";
import { ApiError, api } from "@/lib/api";
import { formatDate, formatDateTime, openPrivateFile, privateFileErrorMessage } from "@/lib/monthly-progress";
import { groupWeeklyStudents, InternshipWeek, WeeklyMonitoring, WeeklyOverview, WeeklyWarning, weeklyStatusLabel } from "@/lib/weekly-progress";
import { LoadingSpinner } from "@/components/LoadingSpinner";

type Scope = "student" | "mentor" | "admin";
const statusClass = (status: string) => status === "APPROVED" ? "bg-emerald-50 text-emerald-700" : status === "REJECTED" ? "bg-red-50 text-red-700" : "bg-slate-100 text-slate-700";
export function WeeklyProgress({ scope, internshipId }: { scope: Scope; internshipId?: number }) {
  const [weeks, setWeeks] = useState<InternshipWeek[]>([]);
  const [overview, setOverview] = useState<WeeklyOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selected, setSelected] = useState<{ week: InternshipWeek; action: "submit" | "reject" } | null>(null);
  const [description, setDescription] = useState("");
  const [reason, setReason] = useState("");
  const [reportPdf, setReportPdf] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [downloadingReport, setDownloadingReport] = useState<number | null>(null);
  const [search, setSearch] = useState("");
  const [warnings, setWarnings] = useState<WeeklyWarning[]>([]);
  const [filter, setFilter] = useState("");
  const [manualWeekNumber, setManualWeekNumber] = useState<string>("");
  const [showSubmittedOnly, setShowSubmittedOnly] = useState(false);
  const actionPending = useRef(new Set<string>());
  useEffect(() => {
    let active = true;
    setLoading(true);
    api.get<WeeklyOverview | WeeklyMonitoring>(`/${scope}/weekly-progress`, { params: internshipId ? { internshipId } : undefined }).then(({ data }) => {
      if (!active) return;
      setWeeks(data.weeks);
      if ("totalWeeks" in data) setOverview(data); else setWarnings(data.warnings);
    }).catch((cause: AxiosError<ApiError>) => { if (active) setError(cause.response?.data?.message ?? "Unable to load weekly progress."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [scope, internshipId]);

  function open(week: InternshipWeek, action: "submit" | "reject") {
    setSelected({ week, action }); setManualWeekNumber(String(week.weekNumber)); setDescription(week.workDescription ?? ""); setReason(""); setReportPdf(null); setError(""); setNotice("");
  }
  async function save(event?: FormEvent, approved?: InternshipWeek) {
    event?.preventDefault();
    const week = approved ?? selected?.week;
    if (!week || (!approved && selected?.action === "reject" && !reason.trim()) || (!approved && selected?.action === "submit" && !description.trim())) return;
    const action = approved ? "approve" : selected?.action;
    const actionKey = `weekly:${week.internshipId}:${week.id}:${action}`;
    if (actionPending.current.has(actionKey)) return;
    actionPending.current.add(actionKey); setBusy(true); setError(""); setNotice("");
    try {
      let body: FormData | Record<string, unknown>;
      if (action === "submit") {
        const form = new FormData(); form.append("workDescription", description.trim());
        if (week.revision) form.append("revision", String(week.revision));
        if (reportPdf) form.append("reportPdf", reportPdf); body=form;
      } else body={ revision: week.revision, ...(action === "reject" ? { reason: reason.trim() } : {}) };
      const response = await api.post<InternshipWeek>(action === "submit" ? `/student/weekly-progress/${week.id}` : `/mentor/weekly-progress/${week.id}/${action}`,body);
      setWeeks(current => current.map(item => item.id === week.id ? response.data : item));
      setSelected(null);
      setNotice(action === "submit" ? "Weekly report submitted to your faculty mentor for review." : action === "approve" ? "Weekly report approved." : "Weekly report rejected. The student can correct and resubmit it.");
    } catch (cause) { setError((cause as AxiosError<ApiError>).response?.data?.message ?? "The weekly report could not be saved. Refresh and try again."); }
    finally { actionPending.current.delete(actionKey); setBusy(false); }
  }
  async function attachment(week: InternshipWeek, download: boolean) {
    setDownloadingReport(week.id);
    setError("");
    try {
      await openPrivateFile(`/${scope}/weekly-progress/${week.id}/attachment${download ? "?download=true" : ""}`, download ? week.reportPdfOriginalName ?? "weekly-report.pdf" : undefined);
    } catch(cause) {
      setError(privateFileErrorMessage(cause, "pdf"));
    } finally {
      setDownloadingReport(null);
    }
  }
  async function downloadReport(week: InternshipWeek) {
    setDownloadingReport(week.id);
    setError("");
    try {
      await openPrivateFile(`/${scope}/weekly-progress/${week.id}/pdf`, `Weekly_Progress_${week.uid ?? "Student"}_Internship_${week.internshipNumber}_Week_${week.weekNumber}.pdf`);
    } catch(cause) {
      setError(privateFileErrorMessage(cause, "pdf"));
    } finally {
      setDownloadingReport(null);
    }
  }

  function record(week: InternshipWeek) {
    return <article id={`week-${week.id}`} key={week.id} className="rounded-xl border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="font-bold">Week {week.weekNumber}</h3><p className="mt-1 text-sm text-slate-500">{formatDate(week.periodStart)} – {formatDate(week.periodEnd)}</p></div><span className={`rounded-full px-3 py-1.5 text-xs font-bold ${statusClass(week.status)}`}>{weeklyStatusLabel(week.status)}</span></div>
      {!week.active && <p className="mt-3 text-sm text-slate-500">Retained after the internship dates changed.</p>}
      <p className="mt-4 text-xs text-slate-500">Attendance: {week.attendanceSubmitted} submitted · {week.attendancePresent} present · {week.attendanceGpsVerified} GPS verified</p>
      {week.workDescription && <p className="mt-4 whitespace-pre-wrap text-sm leading-6 text-slate-700">{week.workDescription}</p>}
      {week.submittedAt && <p className="mt-3 text-xs text-slate-500">Submitted {formatDateTime(week.submittedAt)} · Revision {week.revision}</p>}
      {week.reviewedAt && <p className="mt-2 text-xs text-slate-500">Reviewed by {week.reviewerEmail} · {formatDateTime(week.reviewedAt)}</p>}
      {week.rejectionReason && <p className="mt-3 rounded-xl bg-red-50 p-3 text-sm text-red-700">Required corrections: {week.rejectionReason}</p>}
      <div className="mt-4 flex flex-wrap gap-3">
        {scope === "student" && week.active && (week.status === "AVAILABLE" || week.status === "REJECTED") && <button disabled={busy || downloadingReport === week.id} className="text-sm" onClick={() => open(week, "submit")}>{week.status === "REJECTED" ? "Correct and Resubmit" : "Submit Weekly Report"}</button>}
        {scope === "mentor" && week.status === "PENDING_MENTOR_REVIEW" && <><button disabled={busy || downloadingReport === week.id} className="text-sm" onClick={() => void save(undefined, week)}>Approve</button><button data-button-variant="outline" disabled={busy || downloadingReport === week.id} className="text-sm" onClick={() => open(week, "reject")}>Reject</button></>}
        {week.submittedAt && <button type="button" disabled={busy || downloadingReport === week.id} className="text-sm" onClick={() => void downloadReport(week)}>{downloadingReport === week.id ? <LoadingSpinner label="Preparing PDF…"/> : "Download Report"}</button>}
        {week.reportPdfOriginalName && <><button type="button" data-button-variant="outline" disabled={busy || downloadingReport === week.id} className="text-sm" onClick={() => void attachment(week,false)}>View Uploaded PDF</button><button type="button" data-button-variant="outline" disabled={busy || downloadingReport === week.id} className="text-sm" onClick={() => void attachment(week,true)}>Download Uploaded PDF</button></>}
      </div>
      {!!week.history.length && <details className="mt-4 text-sm"><summary className="cursor-pointer font-semibold text-blue-700">Revision history ({week.history.length})</summary><ol className="mt-3 space-y-3">{week.history.map((item, index) => <li key={index} className="rounded-lg bg-slate-50 p-3"><p className="font-semibold">Revision {item.revision} · {item.eventType.toLowerCase()} · {formatDateTime(item.performedAt)}</p><p className="mt-1 text-xs text-slate-500">{item.actorEmail}</p><p className="mt-2 whitespace-pre-wrap">{item.workDescription}</p>{item.reason && <p className="mt-2 text-red-700">{item.reason}</p>}</li>)}</ol></details>}
    </article>;
  }
  const visible = weeks.filter(week => (!internshipId || week.internshipId === internshipId) && (!filter || week.status === filter) && `${week.studentName} ${week.uid ?? ""} ${week.companyName}`.toLowerCase().includes(search.toLowerCase()));
  return <section className="mt-7">
    {overview && <div className="rounded-2xl border border-blue-100 bg-blue-50 p-5"><h2 className="font-bold">{overview.companyName}</h2><p className="mt-2 text-sm text-slate-600">{formatDate(overview.internshipStart)} – {formatDate(overview.internshipEnd)}</p></div>}
    {notice && <p role="status" className="mt-5 text-sm text-emerald-700">{notice}</p>}
    {!!warnings.length && <div role="status" className="mt-5 space-y-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">{warnings.map(warning => <p key={warning.internshipId}><Link href={`/${scope}/students/${warning.studentUserId}`} className="font-bold underline">{warning.studentName}</Link>: {warning.message}</p>)}</div>}
    {error && !selected && <p role="alert" className="mt-5 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}
    {loading ? <p className="mt-6 text-sm text-slate-500"><LoadingSpinner label="Loading weekly records…"/></p> : !error && <>
      <div className="mt-5 flex flex-wrap gap-3">{scope !== "student" && <label className="text-sm font-semibold">Search student or company<input value={search} onChange={event => setSearch(event.target.value)} className="input mt-1" placeholder="Name, UID or company"/></label>}<label className="text-sm font-semibold">Progress status<select value={filter} onChange={event => setFilter(event.target.value)} className="input mt-1"><option value="">All statuses</option>{(["AVAILABLE", "UPCOMING", "PENDING_MENTOR_REVIEW", "APPROVED", "REJECTED", "ARCHIVED"] as const).map(status => <option key={status} value={status}>{weeklyStatusLabel(status)}</option>)}</select></label></div>
      {!visible.length ? <p className="mt-5 rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-500">{scope === "student" ? "No weekly records match this view." : "No submitted weekly reports yet."}</p> : scope === "student" ? (
        <div className="mt-6 space-y-5">
          <div className="flex flex-wrap items-center gap-4 border-b border-slate-200 pb-4">
            {(() => {
              const availableWeek = visible.find(w => w.active && (w.status === "AVAILABLE" || w.status === "REJECTED"));
              if (!availableWeek) return null;
              return (
                <button
                  type="button"
                  onClick={() => open(availableWeek, "submit")}
                  className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-bold text-white shadow-sm hover:bg-blue-700 disabled:opacity-50"
                >
                  Submit Weekly Report
                </button>
              );
            })()}
            <button
              type="button"
              data-button-variant={showSubmittedOnly ? "primary" : "outline"}
              onClick={() => setShowSubmittedOnly(!showSubmittedOnly)}
              className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              {showSubmittedOnly ? "Show Available Weeks" : `View Previously Submitted Reports (${visible.filter(w => w.submittedAt).length})`}
            </button>
          </div>

          <div className="space-y-4">
            {showSubmittedOnly ? (
              visible.filter(w => w.submittedAt).length === 0 ? (
                <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500">
                  No submitted weekly reports yet.
                </div>
              ) : (
                visible.filter(w => w.submittedAt).map(record)
              )
            ) : (
              visible.filter(w => w.active && (w.status === "AVAILABLE" || w.status === "REJECTED" || w.status === "PENDING_MENTOR_REVIEW" || w.status === "APPROVED")).map(record)
            )}
          </div>
        </div>
      ) : (
        <div className="mt-5 space-y-4">
          {groupWeeklyStudents(visible).map(([studentId, entries]) => {
            const submitted = entries.filter(w => w.submittedAt != null || w.submissionStatus === "SUBMITTED" || w.status === "PENDING_MENTOR_REVIEW" || w.status === "APPROVED" || w.status === "REJECTED");
            return (
              <details key={studentId} className="rounded-2xl border border-slate-200 bg-white p-5">
                <summary className="cursor-pointer font-bold">
                  {entries[0].studentName}{" "}
                  <span className="text-sm font-normal text-slate-500">
                    {entries[0].uid ?? "UID unavailable"} · {entries.filter(w => w.active && w.status === "PENDING_MENTOR_REVIEW").length} pending review
                  </span>
                </summary>
                <div className="mt-4 flex flex-wrap items-center gap-3 text-sm">
                  <span>{entries[0].companyName}</span>
                  <Link href={`/${scope}/students/${studentId}`} className="font-semibold text-blue-700 hover:underline">
                    View student profile
                  </Link>
                </div>
                <div className="mt-4 space-y-4">
                  {submitted.length === 0 ? (
                    <p className="rounded-xl border border-dashed border-slate-200 p-4 text-xs text-slate-500">No weekly reports submitted yet.</p>
                  ) : (
                    submitted.map(record)
                  )}
                </div>
              </details>
            );
          })}
        </div>
      )}
    </>}
    {selected && <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-950/50 p-4"><form onSubmit={event => void save(event)} className="mx-auto my-8 max-w-2xl rounded-2xl bg-white p-6 shadow-2xl"><h2 className="text-xl font-bold">{selected.action === "submit" ? "Weekly progress report" : "Reject weekly report"} · Week {selected.week.weekNumber}</h2>
          {selected.action === "submit" && scope === "student" && (
            <label className="mt-3 block text-sm font-semibold">
              Weekly Report Number
              <input
                type="number"
                min={1}
                max={52}
                value={manualWeekNumber}
                onChange={e => setManualWeekNumber(e.target.value)}
                className="input mt-1 block w-32"
              />
            </label>
          )}<p className="mt-2 text-sm text-slate-500">{formatDate(selected.week.periodStart)} – {formatDate(selected.week.periodEnd)}</p>{selected.action === "submit" ? <><label className="mt-5 block text-sm font-semibold">Work performed during this week<textarea required maxLength={3000} rows={6} value={description} onChange={event => setDescription(event.target.value)} className="input mt-2"/></label><label className="mt-5 block text-sm font-semibold">Upload Report PDF <span className="font-normal text-slate-500">(optional)</span><input type="file" accept=".pdf,application/pdf" onChange={event => { const file=event.target.files?.[0]??null; if(file&&file.size>10*1024*1024){event.target.value="";setReportPdf(null);setError("Report PDFs must be 10 MiB or smaller.");}else{setReportPdf(file);setError("");} }} className="mt-2 block w-full rounded-xl border border-slate-300 p-3 text-sm"/></label>{selected.week.reportPdfOriginalName && !reportPdf && <p className="mt-2 text-xs text-slate-500">Existing PDF “{selected.week.reportPdfOriginalName}” will be kept unless you choose a replacement.</p>}</> : <label className="mt-5 block text-sm font-semibold">Rejection reason<textarea required maxLength={1000} rows={4} value={reason} onChange={event => setReason(event.target.value)} className="input mt-2"/></label>}{error && <p role="alert" className="mt-4 text-sm text-red-700">{error}</p>}<div className="mt-5 flex gap-3"><button disabled={busy || !(selected.action === "submit" ? description.trim() : reason.trim())} className="text-sm">{busy ? <LoadingSpinner label="Saving…"/> : selected.action === "submit" ? "Send for Review" : "Confirm Rejection"}</button><button type="button" data-button-variant="outline" disabled={busy} className="text-sm" onClick={() => setSelected(null)}>Cancel</button></div></form></div>}
  </section>;
}
