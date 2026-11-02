"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import type { AxiosError } from "axios";
import { api, type ApiError } from "@/lib/api";
import { DashboardSection } from "@/components/DashboardLayout";
import { lateRequestStatusClass, lateRequestStatusLabel, lateRequestTimestamp, type LateAttendanceRequest } from "@/lib/late-attendance";

export function LateAttendancePermissions({ role = "MENTOR" }: { role?: "MENTOR" | "ADMIN" }) {
  const [requests, setRequests] = useState<LateAttendanceRequest[]>([]);
  const [view, setView] = useState<"pending" | "history">("pending");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [review, setReview] = useState<{ request: LateAttendanceRequest; status: "APPROVED" | "REJECTED" } | null>(null);
  const [reason, setReason] = useState("");
  const [allowResubmission, setAllowResubmission] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);
  const busy = useRef(false);
  const generation = useRef(0);
  const base = `/${role.toLowerCase()}/attendance/late-requests`;

  async function loadRequests() {
    const current = ++generation.current;
    setLoading(true); setError("");
    try {
      const { data } = await api.get<LateAttendanceRequest[]>(base, { params: { view } });
      if (current === generation.current) setRequests(data.filter(item => view === "pending" ? item.status === "PENDING" : item.status !== "PENDING"));
    } catch (cause) {
      if (current === generation.current) setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Late attendance requests could not be loaded. Please try again.");
    } finally { if (current === generation.current) setLoading(false); }
  }
  useEffect(() => { setRequests([]); void loadRequests(); return () => { generation.current++; }; }, [view, base]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (review && !dialog.current?.open) dialog.current?.showModal(); }, [review]);

  function openReview(request: LateAttendanceRequest, status: "APPROVED" | "REJECTED") {
    setReason(""); setAllowResubmission(false); setError(""); setReview({ request, status });
  }
  function closeReview() { if (!busy.current) { dialog.current?.close(); setReview(null); } }
  async function decide(event: FormEvent) {
    event.preventDefault();
    if (!review || busy.current) return;
    busy.current = true; setSaving(true); setError(""); setNotice("");
    try {
      const { data } = await api.post<LateAttendanceRequest>(`${base}/${review.request.id}/decision`, {
        status: review.status, rejectionReason: review.status === "REJECTED" ? reason.trim() || null : null,
        allowResubmission: review.status === "REJECTED" && allowResubmission,
      });
      dialog.current?.close(); setReview(null);
      setRequests(current => current.filter(item => item.id !== data.id));
      setNotice(`Request for ${data.studentName} on ${data.attendanceDate} ${lateRequestStatusLabel(data.status).toLowerCase()}.`);
      await loadRequests();
    } catch (cause) {
      setError((cause as AxiosError<ApiError>).response?.data?.message ?? "The decision could not be saved. Please refresh and try again.");
    } finally { busy.current = false; setSaving(false); }
  }

  return <DashboardSection title="Late Attendance Requests" className="mt-6"
    description={role === "ADMIN" ? "Review requests from students without an active faculty mentor. Assigned students are reviewed by their own mentor." : "Review only late attendance requests submitted by your assigned students. Completed decisions are kept in request history."}>
    <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
      <div className="flex flex-wrap gap-2" aria-label="Late attendance request views">
        <button type="button" data-button-variant={view === "pending" ? undefined : "outline"} aria-pressed={view === "pending"} disabled={saving} onClick={() => setView("pending")}>Pending requests</button>
        <button type="button" data-button-variant={view === "history" ? undefined : "outline"} aria-pressed={view === "history"} disabled={saving} onClick={() => setView("history")}>Request history</button>
      </div>
      <button type="button" data-button-variant="text" disabled={loading || saving} onClick={() => void loadRequests()}>Refresh requests</button>
    </div>
    {notice && <p role="status" className="mt-4 rounded-xl bg-blue-50 p-3 text-sm text-blue-800">{notice}</p>}
    {error && !review && <p role="alert" className="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {loading ? <p role="status" className="mt-5 text-sm text-slate-500">Loading late attendance requests…</p> : error && !requests.length ? null : requests.length === 0 ?
      <p className="mt-5 text-sm text-slate-500">{view === "pending" ? "No pending late attendance requests." : "No reviewed late attendance requests yet."}</p> :
      <div className="mt-5 overflow-x-auto"><table className="w-full min-w-[800px] text-left text-sm">
        <thead><tr className="border-b text-xs uppercase tracking-wide text-slate-500"><th className="p-3">Student / UID</th><th className="p-3">Attendance date</th><th className="p-3">Reason and details</th><th className="p-3">Requested at</th><th className="p-3">Status</th><th className="p-3">{view === "pending" ? "Actions" : "Decision"}</th></tr></thead>
        <tbody className="divide-y divide-slate-100">{requests.map(request => <tr key={request.id} className="align-top">
          <td className="p-3"><p className="font-semibold">{request.studentName}</p><p className="mt-1 text-xs text-slate-500">{request.uid || "UID unavailable"}</p></td>
          <td className="whitespace-nowrap p-3">{request.attendanceDate}<p className="mt-1 text-xs text-slate-500">Internship #{request.internshipId}</p></td>
          <td className="min-w-56 max-w-sm break-words p-3"><p className="whitespace-pre-wrap">{request.reason}</p>{request.supportingDetails && <details className="mt-2 text-xs text-slate-600"><summary className="cursor-pointer font-semibold">Supporting details</summary><p className="mt-1 whitespace-pre-wrap">{request.supportingDetails}</p></details>}</td>
          <td className="p-3 text-xs">{lateRequestTimestamp(request.requestedAt, request.attendanceZone)}<p className="mt-1 text-slate-500">{request.attendanceZone}</p></td>
          <td className="p-3"><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${lateRequestStatusClass(request.status)}`}>{lateRequestStatusLabel(request.status)}</span></td>
          <td className="p-3">{view === "pending" ? <div className="flex flex-wrap gap-2">
            <button type="button" disabled={saving} onClick={() => openReview(request, "APPROVED")}>Approve</button>
            <button type="button" data-button-variant="outline" disabled={saving} onClick={() => openReview(request, "REJECTED")}>Reject</button>
          </div> : <div className="max-w-56 text-xs text-slate-600">{request.reviewedAt && <p>{lateRequestTimestamp(request.reviewedAt, request.attendanceZone)}</p>}{request.rejectionReason && <p className="mt-1 whitespace-pre-wrap break-words">{request.rejectionReason}</p>}{request.resubmissionAllowed && <p className="mt-1 font-medium">Resubmission allowed</p>}</div>}</td>
        </tr>)}</tbody>
      </table></div>}
    <dialog ref={dialog} onCancel={event => { if (busy.current) event.preventDefault(); else setReview(null); }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-2xl border border-slate-200 bg-white p-6 text-slate-900 shadow-xl backdrop:bg-slate-950/40" aria-labelledby="late-request-review-title">
      {review && <form onSubmit={decide}>
        <h3 id="late-request-review-title" className="text-lg font-bold">{review.status === "APPROVED" ? "Approve" : "Reject"} late attendance request</h3>
        <p className="mt-2 text-sm text-slate-600">{review.request.studentName} · {review.request.uid || "UID unavailable"} · {review.request.attendanceDate}</p>
        {review.status === "APPROVED" ? <p className="mt-4 text-sm text-slate-600">Approval records attendance for this date as manually approved. GPS and liveness remain unverified; existing student submissions are protected.</p> : <div className="mt-4 space-y-4">
          <label className="block text-sm font-semibold" htmlFor="late-request-rejection">Rejection reason <span className="font-normal text-slate-500">(optional)</span><textarea id="late-request-rejection" value={reason} disabled={saving} maxLength={1000} rows={3} onChange={event => setReason(event.target.value)} className="mt-2 block w-full rounded-xl border border-slate-300 p-3 font-normal" /></label>
          <label className="flex items-start gap-2 text-sm"><input type="checkbox" className="mt-1" checked={allowResubmission} disabled={saving} onChange={event => setAllowResubmission(event.target.checked)} />Allow the student to submit a new request for this date</label>
        </div>}
        {error && <p role="alert" className="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
        <div className="mt-6 flex flex-wrap justify-end gap-3"><button type="button" data-button-variant="outline" disabled={saving} onClick={closeReview}>Cancel</button><button type="submit" disabled={saving}>{saving ? "Saving decision…" : review.status === "APPROVED" ? "Approve request" : "Reject request"}</button></div>
      </form>}
    </dialog>
  </DashboardSection>;
}
