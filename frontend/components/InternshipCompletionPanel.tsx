"use client";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { completionLabel, InternshipCompletion } from "@/lib/internship-completion";
import { formatDateTime } from "@/lib/monthly-progress";

function Confirmation({ completion, reopening, busy, onClose, onConfirm }: { completion: InternshipCompletion; reopening: boolean; busy: boolean; onClose: () => void; onConfirm: (remarks: string) => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [remarks, setRemarks] = useState("");
  useEffect(() => { dialog.current?.showModal(); }, []);
  return <dialog ref={dialog} onCancel={e => { if (busy) e.preventDefault(); }} onClose={onClose} aria-labelledby="completion-confirm-title" className="m-auto w-[min(95vw,34rem)] rounded-2xl border border-slate-200 bg-white p-6 text-slate-900 shadow-xl backdrop:bg-slate-950/50">
    <form onSubmit={e => { e.preventDefault(); if (!busy && (!reopening || remarks.trim().length >= 5)) onConfirm(remarks.trim()); }}>
      <h3 id="completion-confirm-title" className="text-xl font-bold">{reopening ? "Reopen internship?" : "Mark internship as completed?"}</h3>
      <p className="mt-4 font-semibold">{completion.studentName} · {completion.uid ?? "UID unavailable"}</p><p className="mt-1 text-sm">{completion.companyName} · {completion.startDate} to {completion.endDate}</p>
      <p className="mt-4 text-sm text-slate-600">{reopening ? "The official completion will be reopened. The previous approval remains in history and the student will be notified." : "This internship will be officially marked COMPLETED. The student will be notified and internship details will be locked until IIIC reopens it."}</p>
      <label className="mt-4 block text-sm font-semibold">{reopening ? "Reopening reason (required)" : "Remarks (optional)"}<textarea autoFocus value={remarks} onChange={e => setRemarks(e.target.value)} required={reopening} minLength={reopening ? 5 : undefined} maxLength={1000} disabled={busy} className="mt-2 w-full rounded-xl border border-slate-300 p-3 font-normal" /></label>
      <div className="mt-5 flex flex-wrap gap-3"><button disabled={busy || (reopening && remarks.trim().length < 5)}>{busy ? "Saving…" : reopening ? "Confirm Reopening" : "Confirm Completion"}</button><button type="button" data-button-variant="outline" disabled={busy} onClick={onClose}>Cancel</button></div>
    </form>
  </dialog>;
}

export function InternshipCompletionPanel({ studentId }: { studentId: number }) {
  const [completion, setCompletion] = useState<InternshipCompletion | null>(null);
  const [error, setError] = useState("");
  const [action, setAction] = useState<"complete" | "reopen" | null>(null);
  const [saving, setSaving] = useState(false);
  const [success, setSuccess] = useState("");
  useEffect(() => { const controller = new AbortController(); setCompletion(null); api.get<InternshipCompletion>(`/admin/students/${studentId}/internship-completion`, { signal: controller.signal }).then(r => setCompletion(r.data)).catch(() => { if (!controller.signal.aborted) setError("Completion requirements could not be loaded."); }); return () => controller.abort(); }, [studentId]);
  async function decide(remarks: string) {
    if (!completion || !action || saving) return;
    setSaving(true); setError(""); setSuccess("");
    try { const { data } = await api.post<InternshipCompletion>(`/admin/students/${studentId}/internship-completion/${action}`, { revision: completion.revision, remarks }); setCompletion(data); setSuccess(action === "complete" ? "Internship officially completed. The student has been notified." : "Internship reopened. The reason and previous approval are retained in history."); }
    catch (failure) { setError((failure as { response?: { data?: { message?: string } } }).response?.data?.message || "The action could not be saved. Refresh the record and try again."); }
    finally { setSaving(false); setAction(null); }
  }
  return <section id="internship-completion" className="rounded-2xl border border-slate-200 bg-white p-6"><h2 className="text-lg font-bold">Internship Completion</h2>
    {error && <p role="alert" className="mt-3 text-sm text-red-700">{error}</p>}{success && <p role="status" className="mt-3 text-sm text-emerald-700">{success}</p>}
    {!completion ? <p className="mt-4 text-sm text-slate-500">{error ? "Reload this student record to retry." : "Checking mandatory reports and mentor reviews…"}</p> : <>
      <p className="mt-3 font-semibold">{completion.studentName} · {completion.uid ?? "UID unavailable"}</p><p className="mt-1 text-sm text-slate-600">{completion.companyName} · {completion.startDate} to {completion.endDate} · {completion.totalWeeks} weeks · Session {completion.sessionName ?? "Unassigned"}</p>
      <p className="mt-4 inline-block rounded-full bg-blue-50 px-3 py-1 text-xs font-bold">{completionLabel(completion.status)}</p>
      <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">{[["Required reports", `${completion.requiredReports} (${completion.weeklyRequired} weekly + ${completion.monthlyRequired} monthly)`], ["Reports submitted", completion.submittedReports], ["Reports pending", completion.pendingReports], ["Mentor-approved reports", `${completion.approvedReports} / ${completion.requiredReports}`], ["Mentor approval", completion.mentorApprovalStatus], ["Final project report", completion.finalProjectReportStatus], ["Completion date", completion.completedAt ? formatDateTime(completion.completedAt) : "Not completed"], ["IIIC reviewer", completion.reviewer ?? "Pending"]].map(([label, value]) => <div key={label}><dt className="text-xs text-slate-500">{label}</dt><dd className="mt-1 font-semibold">{value}</dd></div>)}</dl>
      {completion.remarks && <p className="mt-4 text-sm">Remarks: {completion.remarks}</p>}
      {completion.status !== "COMPLETED" && <><p className="mt-5 text-sm font-semibold">{completion.eligible ? "All completion requirements are satisfied." : "Completion requirements outstanding"}</p>{completion.blockers.length > 0 && <ul className="mt-2 list-inside list-disc text-sm text-amber-800">{completion.blockers.map(b => <li key={b}>{b}</li>)}</ul>}<button className="mt-4 text-sm" disabled={!completion.eligible || saving} onClick={() => setAction("complete")}>Mark Internship as Completed</button></>}
      {completion.status === "COMPLETED" && <button data-button-variant="outline" className="mt-5 text-sm" onClick={() => setAction("reopen")}>Reopen Internship</button>}
      {completion.history.length > 0 && <details className="mt-5 border-t border-slate-200 pt-4"><summary className="cursor-pointer text-sm font-semibold">Completion audit history ({completion.history.length})</summary>{completion.history.map(event => <div key={event.revision} className="mt-3 rounded-xl bg-slate-50 p-3 text-sm"><p className="font-semibold">{completionLabel(event.previousStatus)} → {completionLabel(event.finalStatus)}</p><p>{event.reviewer} · {formatDateTime(event.performedAt)}</p>{event.remarks && <p className="mt-1">{event.remarks}</p>}</div>)}</details>}
      {action && <Confirmation completion={completion} reopening={action === "reopen"} busy={saving} onClose={() => setAction(null)} onConfirm={decide} />}
    </>}
  </section>;
}

export function StudentCompletionStatus() {
  const [completion, setCompletion] = useState<InternshipCompletion | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { const controller = new AbortController(); api.get<InternshipCompletion>("/student/internship-completion", { signal: controller.signal }).then(r => setCompletion(r.data)).catch(failure => { if (!controller.signal.aborted && failure.response?.status !== 404) setError("Internship completion status could not be loaded."); }); return () => controller.abort(); }, []);
  if (!completion && !error) return null;
  return <section id="internship-completion" className="mt-6 rounded-2xl border border-emerald-200 bg-white p-6"><h2 className="text-lg font-bold">Internship Completion</h2>{error && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}{completion && <><p className="mt-3 font-semibold text-emerald-800">{completionLabel(completion.status)}</p><p className="mt-2 text-sm">{completion.companyName} · Academic session {completion.sessionName ?? "Unassigned"}</p>{completion.completedAt ? <p className="mt-2 text-sm">Officially completed on {formatDateTime(completion.completedAt)}</p> : <p className="mt-2 text-sm text-slate-600">{completion.approvedReports}/{completion.requiredReports} mandatory reports approved. Official completion requires IIIC approval.</p>}{completion.history.at(-1)?.finalStatus === "IN_PROGRESS" && <p className="mt-2 text-sm text-amber-800">Reopened: {completion.history.at(-1)?.remarks}</p>}</>}</section>;
}
