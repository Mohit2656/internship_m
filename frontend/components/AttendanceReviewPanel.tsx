"use client";

import { useRef, useState } from "react";
import { api } from "@/lib/api";
import { Attendance, AttendanceReviewAudit, AttendanceReviewStatus, attendanceReviewLabel, isAutomaticAbsence } from "@/lib/attendance";
import { formatDateTime } from "@/lib/monthly-progress";
import { resumeError } from "@/lib/professional-profile";
import { LoadingSpinner } from "@/components/LoadingSpinner";

export function AttendanceReviewFeedback({ record }: { record: Attendance }) {
  return <div className="mt-2 text-sm"><p className={`font-semibold ${record.review?.status === "REJECTED" ? "text-red-700" : "text-slate-800"}`}>{record.attendanceResult === "PENDING_MENTOR_REVIEW" ? attendanceReviewLabel(record.review?.status) : record.review?.status === "APPROVED" || record.review?.status === "REJECTED" ? attendanceReviewLabel(record.review.status) : "Finalized automatically"}</p>{record.review?.reviewedAt && <p className="mt-1 text-xs text-slate-500">{record.review.reviewerName || "Faculty mentor"} · {formatDateTime(record.review.reviewedAt)}</p>}{record.review?.reason && <p className="mt-2 whitespace-pre-wrap text-xs text-slate-700">Reason: {record.review.reason}</p>}{record.acknowledgement && <p className="mt-1 text-xs text-slate-500">Viewed by mentor · {formatDateTime(record.acknowledgement.seenAt)}</p>}</div>;
}

export function AttendanceReviewPanel({ record, role, onChanged }: { record: Attendance; role: "ADMIN" | "MENTOR"; onChanged: () => void }) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const actionPending = useRef(new Set<string>());
  const reviewKey = `attendance-review:${record.id}`;
  const acknowledgementKey = `attendance-acknowledgement:${record.id}`;
  const [error, setError] = useState("");
  const [history, setHistory] = useState<AttendanceReviewAudit[] | null>(null);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const historyBusy = useRef(false);
  const pending = record.attendanceResult === "PENDING_MENTOR_REVIEW";
  const terminal = !pending || record.review?.status === "APPROVED" || record.review?.status === "REJECTED";
  const hasReviewHistory = !isAutomaticAbsence(record) || (record.review?.revision ?? 0) > 0;

  async function decide(status: AttendanceReviewStatus) {
    setError("");
    if (status !== "APPROVED" && reason.trim().length < 5) { setError("Provide a reason of at least 5 characters."); return; }
    if (actionPending.current.has(reviewKey)) return;
    actionPending.current.add(reviewKey); setBusy(true);
    try {
      await api.post(`/mentor/attendance/${record.id}/review`, { status, revision: record.review?.revision ?? 0, reason: reason.trim() || null });
      setReason("");
      onChanged();
    } catch (cause) { setError(resumeError(cause, "Attendance review could not be saved. Reload the history before retrying.")); }
    finally { actionPending.current.delete(reviewKey); setBusy(false); }
  }
  async function acknowledge() {
    if (pending || record.acknowledgement) return;
    if (actionPending.current.has(acknowledgementKey)) return;
    actionPending.current.add(acknowledgementKey); setBusy(true); setError("");
    try { await api.post(`/mentor/attendance/${record.id}/acknowledgement`); onChanged(); }
    catch (cause) { setError(resumeError(cause, "Attendance acknowledgement could not be saved.")); }
    finally { actionPending.current.delete(acknowledgementKey); setBusy(false); }
  }
  async function loadHistory(open: boolean) {
    if (!open || history || historyBusy.current) return;
    historyBusy.current = true; setLoadingHistory(true); setError("");
    try { setHistory((await api.get<AttendanceReviewAudit[]>(`/${role.toLowerCase()}/attendance/${record.id}/review/history`)).data); }
    catch (cause) { setError(resumeError(cause, "Attendance review history could not be loaded.")); }
    finally { historyBusy.current = false; setLoadingHistory(false); }
  }
  return <div className="min-w-60"><AttendanceReviewFeedback record={record} />{error && <p role="alert" className="mt-2 text-xs text-red-700">{error}</p>}
    {role === "MENTOR" && !pending && !record.acknowledgement && <button data-button-variant="outline" type="button" className="mt-3 text-xs" disabled={busy} onClick={() => void acknowledge()}>{busy ? <LoadingSpinner label="Saving…"/> : "Mark as Seen"}</button>}
    {role === "MENTOR" && !terminal && <details className="mt-3"><summary className="cursor-pointer font-semibold text-blue-700">Review attendance</summary><p className="mt-2 text-xs text-slate-500">Review the original GPS, liveness and security outcomes. A mentor decision records PRESENT or ABSENT without changing the evidence.</p><label className="mt-3 block text-xs font-semibold">Review reason<textarea maxLength={1000} disabled={busy} value={reason} onChange={event => setReason(event.target.value)} className="input mt-1 min-h-20" placeholder="Required for absence or manual review" /></label><div className="mt-3 flex flex-wrap gap-2"><button type="button" className="text-xs" disabled={busy || !record.review?.approvalAllowed} onClick={() => void decide("APPROVED")}>{busy ? <LoadingSpinner label="Saving…"/> : "Mark present"}</button><button type="button" className="text-xs" disabled={busy} onClick={() => void decide("REJECTED")}>Mark absent</button><button data-button-variant="outline" type="button" className="text-xs" disabled={busy || record.review?.status === "MANUAL_REVIEW"} onClick={() => void decide("MANUAL_REVIEW")}>Keep pending</button></div></details>}
    {role === "MENTOR" && terminal && <details className="mt-3"><summary className="cursor-pointer font-semibold text-blue-700">Change attendance</summary><p className="mt-2 text-xs text-slate-500">Mentors can override finalized Present or Absent status when a student has a valid reason or correction is needed.</p><label className="mt-3 block text-xs font-semibold">Change reason<textarea maxLength={1000} disabled={busy} value={reason} onChange={event => setReason(event.target.value)} className="input mt-1 min-h-20" placeholder={record.attendanceResult === "PRESENT" ? "Required when changing Present to Absent" : "Optional note when marking Present"} /></label><div className="mt-3 flex flex-wrap gap-2">{record.attendanceResult !== "PRESENT" && <button type="button" className="text-xs" disabled={busy} onClick={() => void decide("APPROVED")}>{busy ? <LoadingSpinner label="Saving…"/> : "Change to Present"}</button>}{record.attendanceResult !== "ABSENT" && <button type="button" data-button-variant="outline" className="text-xs" disabled={busy} onClick={() => void decide("REJECTED")}>{busy ? <LoadingSpinner label="Saving…"/> : "Change to Absent"}</button>}</div></details>}
    {role !== "MENTOR" && hasReviewHistory && <details className="mt-3" onToggle={event => void loadHistory(event.currentTarget.open)}><summary className="cursor-pointer text-xs font-semibold text-blue-700">Review audit history</summary>{loadingHistory ? <p className="mt-2 text-xs"><LoadingSpinner label="Loading history…"/></p> : history?.length ? <ol className="mt-2 space-y-2">{history.map(entry => <li key={entry.revision} className="rounded bg-slate-50 p-2 text-xs"><p>{attendanceReviewLabel(entry.previousStatus)} → {attendanceReviewLabel(entry.status)}</p><p className="mt-1">{entry.reviewerName} · {formatDateTime(entry.reviewedAt)}</p>{entry.reason && <p className="mt-1 whitespace-pre-wrap">{entry.reason}</p>}</li>)}</ol> : history && <p className="mt-2 text-xs text-slate-500">No mentor decisions recorded.</p>}</details>}
  </div>;
}
