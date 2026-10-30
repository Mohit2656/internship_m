"use client";

import { useRef, useState } from "react";
import { api } from "@/lib/api";
import { Attendance, AttendanceReviewAudit, AttendanceReviewStatus, attendanceReviewLabel, isAutomaticAbsence } from "@/lib/attendance";
import { formatDateTime } from "@/lib/monthly-progress";
import { resumeError } from "@/lib/professional-profile";

export function AttendanceReviewFeedback({ record }: { record: Attendance }) {
  return <div className="mt-2 text-sm"><p className={`font-semibold ${record.review?.status === "REJECTED" ? "text-red-700" : "text-slate-800"}`}>{attendanceReviewLabel(isAutomaticAbsence(record) ? "NOT_APPLICABLE" : record.review?.status)}</p>{record.review?.reviewedAt && <p className="mt-1 text-xs text-slate-500">{record.review.reviewerName || "Faculty mentor"} · {formatDateTime(record.review.reviewedAt)}</p>}{record.review?.reason && <p className="mt-2 whitespace-pre-wrap text-xs text-slate-700">Reason: {record.review.reason}</p>}</div>;
}

export function AttendanceReviewPanel({ record, role, onChanged }: { record: Attendance; role: "ADMIN" | "MENTOR"; onChanged: () => void }) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [history, setHistory] = useState<AttendanceReviewAudit[] | null>(null);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const historyBusy = useRef(false);
  const terminal = isAutomaticAbsence(record) || record.review?.status === "APPROVED" || record.review?.status === "REJECTED";

  async function decide(status: AttendanceReviewStatus) {
    if (busy || terminal) return;
    setError("");
    if (status !== "APPROVED" && reason.trim().length < 5) { setError("Provide a reason of at least 5 characters."); return; }
    setBusy(true);
    try {
      await api.post(`/mentor/attendance/${record.id}/review`, { status, revision: record.review?.revision ?? 0, reason: reason.trim() || null });
      onChanged();
    } catch (cause) { setError(resumeError(cause, "Attendance review could not be saved. Reload the history before retrying.")); }
    finally { setBusy(false); }
  }
  async function loadHistory(open: boolean) {
    if (!open || history || historyBusy.current) return;
    historyBusy.current = true; setLoadingHistory(true); setError("");
    try { setHistory((await api.get<AttendanceReviewAudit[]>(`/${role.toLowerCase()}/attendance/${record.id}/review/history`)).data); }
    catch (cause) { setError(resumeError(cause, "Attendance review history could not be loaded.")); }
    finally { historyBusy.current = false; setLoadingHistory(false); }
  }
  return <div className="min-w-60"><AttendanceReviewFeedback record={record} />{error && <p role="alert" className="mt-2 text-xs text-red-700">{error}</p>}
    {role === "MENTOR" && !terminal && <details className="mt-3"><summary className="cursor-pointer font-semibold text-blue-700">Review attendance</summary><p className="mt-2 text-xs text-slate-500">GPS and liveness outcomes remain separate from the mentor decision.</p>{!record.review?.approvalAllowed && <p className="mt-2 text-xs text-amber-800">The submitted verification does not permit approval. Reject or request manual review.</p>}<label className="mt-3 block text-xs font-semibold">Review reason<textarea maxLength={1000} disabled={busy} value={reason} onChange={event => setReason(event.target.value)} className="input mt-1 min-h-20" placeholder="Required for rejection or manual review" /></label><div className="mt-3 flex flex-wrap gap-2"><button type="button" className="text-xs" disabled={busy || !record.review?.approvalAllowed} onClick={() => void decide("APPROVED")}>Approve</button><button type="button" className="text-xs" disabled={busy} onClick={() => void decide("REJECTED")}>Reject</button><button data-button-variant="outline" type="button" className="text-xs" disabled={busy || record.review?.status === "MANUAL_REVIEW"} onClick={() => void decide("MANUAL_REVIEW")}>Manual review</button></div></details>}
    {!isAutomaticAbsence(record) && <details className="mt-3" onToggle={event => void loadHistory(event.currentTarget.open)}><summary className="cursor-pointer text-xs font-semibold text-blue-700">Review audit history</summary>{loadingHistory ? <p role="status" className="mt-2 text-xs">Loading history…</p> : history?.length ? <ol className="mt-2 space-y-2">{history.map(entry => <li key={entry.revision} className="rounded bg-slate-50 p-2 text-xs"><p>{attendanceReviewLabel(entry.previousStatus)} → {attendanceReviewLabel(entry.status)}</p><p className="mt-1">{entry.reviewerName} · {formatDateTime(entry.reviewedAt)}</p>{entry.reason && <p className="mt-1 whitespace-pre-wrap">{entry.reason}</p>}</li>)}</ol> : history && <p className="mt-2 text-xs text-slate-500">No mentor decisions recorded.</p>}</details>}
  </div>;
}
