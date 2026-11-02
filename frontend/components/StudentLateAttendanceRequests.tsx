"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import type { AxiosError } from "axios";
import { api, type ApiError } from "@/lib/api";
import { DashboardSection } from "@/components/DashboardLayout";
import { lateRequestStatusClass, lateRequestStatusLabel, lateRequestTimestamp, type LateAttendanceRequest } from "@/lib/late-attendance";

type Props = { internship: { id: number; startDate: string; endDate: string } | null; onAttendanceChanged?: () => void };
export function StudentLateAttendanceRequests({ internship, onAttendanceChanged }: Props) {
  const [requests, setRequests] = useState<LateAttendanceRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [date, setDate] = useState("");
  const [reason, setReason] = useState("");
  const [details, setDetails] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const busy = useRef(false);
  const generation = useRef(0);
  const zone = requests[0]?.attendanceZone ?? "Asia/Kolkata";
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
  const maxDate = internship ? [internship.endDate, today].sort()[0] : today;
  const blockedRequest = requests.find(request => request.attendanceDate === date && (request.status !== "REJECTED" || !request.resubmissionAllowed));

  async function loadRequests(refreshAttendance = false) {
    if (!internship) { setLoading(false); return; }
    const current = ++generation.current;
    setLoading(true); setError("");
    try {
      const { data } = await api.get<LateAttendanceRequest[]>("/student/attendance/late-requests", { params: { internshipId: internship.id } });
      if (current === generation.current) { setRequests(data); if (refreshAttendance) onAttendanceChanged?.(); }
    } catch (cause) {
      if (current === generation.current) setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Your late attendance requests could not be loaded. Please try again.");
    } finally { if (current === generation.current) setLoading(false); }
  }
  useEffect(() => {
    setRequests([]); setDate(""); setReason(""); setDetails(""); setNotice(""); setError("");
    void loadRequests(); return () => { generation.current++; };
  }, [internship?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!internship || busy.current || blockedRequest) return;
    const current = generation.current;
    busy.current = true; setSaving(true); setError(""); setNotice("");
    try {
      const { data } = await api.post<LateAttendanceRequest>("/student/attendance/late-requests", {
        internshipId: internship.id, attendanceDate: date, reason: reason.trim(), supportingDetails: details.trim() || null,
      });
      if (current !== generation.current) return;
      setRequests(current => [data, ...current]); setReason(""); setDetails("");
      setNotice(data.mentorAssigned ? "Your request was submitted and your faculty mentor was notified." : "Your request was submitted to IIIC because no active faculty mentor is assigned.");
    } catch (cause) {
      if (current === generation.current) setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Your request could not be submitted. Please try again.");
    } finally { busy.current = false; setSaving(false); }
  }

  return <DashboardSection id="late-attendance-requests" title="Late Attendance Requests" className="mt-6" description="Request a review for a missed attendance date after its submission deadline. Attendance is credited only after approval.">
    {!internship ? <p className="mt-4 text-sm text-slate-500">Complete internship registration to request late attendance.</p> : <>
      <form className="mt-5 space-y-4" onSubmit={submit}>
        <div className="grid gap-4 md:grid-cols-2">
          <label htmlFor="late-attendance-date" className="block text-sm font-semibold">Attendance date<input id="late-attendance-date" type="date" required min={internship.startDate} max={maxDate} value={date} disabled={saving || loading} onChange={event => setDate(event.target.value)} className="mt-2 block w-full rounded-xl border border-slate-300 p-3 font-normal" /><span className="mt-1 block text-xs font-normal text-slate-500">Attendance time zone: {zone}</span></label>
          <label htmlFor="late-attendance-reason" className="block text-sm font-semibold">Reason<textarea id="late-attendance-reason" required minLength={5} maxLength={1000} rows={3} value={reason} disabled={saving || loading} onChange={event => setReason(event.target.value)} className="mt-2 block w-full rounded-xl border border-slate-300 p-3 font-normal" /></label>
        </div>
        <label htmlFor="late-attendance-details" className="block text-sm font-semibold">Supporting details <span className="font-normal text-slate-500">(optional)</span><textarea id="late-attendance-details" maxLength={4000} rows={3} value={details} disabled={saving || loading} onChange={event => setDetails(event.target.value)} placeholder="Describe the work completed, supporting references, or circumstances for your reviewer." className="mt-2 block w-full rounded-xl border border-slate-300 p-3 font-normal" /></label>
        {blockedRequest && <p className="text-sm text-slate-600">This date already has a {lateRequestStatusLabel(blockedRequest.status).toLowerCase()} request. A new request is allowed only after rejection with resubmission permission.</p>}
        <button type="submit" disabled={saving || loading || Boolean(blockedRequest) || !date || reason.trim().length < 5}>{saving ? "Submitting request…" : "Submit late attendance request"}</button>
      </form>
      {notice && <p role="status" className="mt-4 rounded-xl bg-blue-50 p-3 text-sm text-blue-800">{notice}</p>}
      {error && <p role="alert" className="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      <div className="mt-6 flex flex-wrap items-center justify-between gap-3"><h3 className="font-semibold text-slate-900">Your requests</h3><button type="button" data-button-variant="text" disabled={loading || saving} onClick={() => void loadRequests(true)}>Refresh requests</button></div>
      {loading ? <p role="status" className="mt-3 text-sm text-slate-500">Loading your requests…</p> : requests.length === 0 ? <p className="mt-3 text-sm text-slate-500">No late attendance requests submitted.</p> : <div className="mt-4 overflow-x-auto"><table className="w-full min-w-[650px] text-left text-sm">
        <thead><tr className="border-b text-xs uppercase tracking-wide text-slate-500"><th className="p-3">Attendance date</th><th className="p-3">Reason</th><th className="p-3">Submitted</th><th className="p-3">Status</th><th className="p-3">Review details</th></tr></thead>
        <tbody className="divide-y divide-slate-100">{requests.map(request => <tr key={request.id} className="align-top"><td className="whitespace-nowrap p-3">{request.attendanceDate}</td><td className="max-w-sm break-words p-3"><p className="whitespace-pre-wrap">{request.reason}</p>{request.supportingDetails && <details className="mt-2 text-xs text-slate-600"><summary className="cursor-pointer">Supporting details</summary><p className="mt-1 whitespace-pre-wrap">{request.supportingDetails}</p></details>}</td><td className="p-3 text-xs">{lateRequestTimestamp(request.requestedAt, request.attendanceZone)}</td><td className="p-3"><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${lateRequestStatusClass(request.status)}`}>{lateRequestStatusLabel(request.status)}</span></td><td className="max-w-64 p-3 text-xs text-slate-600">{request.reviewedAt && <p>{lateRequestTimestamp(request.reviewedAt, request.attendanceZone)}</p>}{request.rejectionReason && <p className="mt-1 whitespace-pre-wrap break-words">{request.rejectionReason}</p>}{request.resubmissionAllowed && <p className="mt-1 font-medium">You may submit a new request for this date.</p>}{request.status === "PENDING" && <p>{request.mentorAssigned ? "Awaiting faculty mentor review" : "Awaiting IIIC review; no active mentor assigned"}</p>}</td></tr>)}</tbody>
      </table></div>}
    </>}
  </DashboardSection>;
}
