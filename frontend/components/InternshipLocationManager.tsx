"use client";

import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { api, ApiError } from "@/lib/api";
import { WorkplacePlaceSelector } from "@/components/WorkplacePlaceSelector";
import { LocationMapLink } from "@/components/LocationMapLink";
import type { RegisteredWorkplace } from "@/lib/internship-details";
import { InternshipLocation } from "@/lib/attendance";

export function InternshipLocationManager({ studentId, scope = "admin" }: { studentId: number; scope?: "admin" | "mentor" } | { scope: "student"; studentId?: never }) {
  const [correction, setCorrection] = useState(false);
  const [selection, setSelection] = useState<RegisteredWorkplace | null>(null);
  const [reason, setReason] = useState("");
  const [location, setLocation] = useState<InternshipLocation | null>(null);
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [radius, setRadius] = useState("100");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState("");

  function useLocation(value: InternshipLocation) {
    setLocation(value);
    setLatitude(String(value.latitude));
    setLongitude(String(value.longitude));
    setRadius(String(value.geofenceRadiusMeters));
  }

  useEffect(() => {
    const abort = new AbortController();
    setLoading(true); setLocation(null); setLatitude(""); setLongitude(""); setCorrection(false);
    api.get<InternshipLocation>((scope === "student" ? "/student/internship/location" : `/${scope}/students/${studentId}/internship/location`), { signal: abort.signal })
      .then(response => useLocation(response.data))
      .catch((cause: AxiosError) => {
        if (!abort.signal.aborted && cause.response?.status !== 404) setError("Internship location could not be loaded.");
      })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [studentId, scope]);

  async function save() {
    setSaving(true); setError("");
    try {
      const { data } = await api.put<InternshipLocation>(`/admin/students/${studentId}/internship/location/${correction ? "correction" : "radius"}`, correction ? {
        workplace: selection, radiusMeters: Number(radius), reason: reason.trim()
      } : { radiusMeters: Number(radius) });
      setCorrection(false); setSelection(null); setReason("");
      useLocation(data);
    } catch (cause) {
      setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Location could not be saved.");
    } finally { setSaving(false); }
  }

  async function confirm(decision: "APPROVE" | "REJECT" = "APPROVE") {
    setConfirming(true); setError("");
    try {
      const { data } = await api.post<InternshipLocation>(`/${scope}/students/${studentId}/internship/location/review`, { revision: location?.revision, decision, remarks: reason.trim() || null });
      useLocation(data);
    } catch (cause) {
      setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Location could not be confirmed.");
    } finally { setConfirming(false); }
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-7">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Physical Attendance Location</p><h2 className="mt-2 text-xl font-bold">Registered workplace</h2><p className="mt-2 text-sm text-slate-600">Review this internship's registered branch and radius. Both mentor and III Cell approval are required.</p></div>
        {!loading && <span className={`rounded-full px-3 py-1.5 text-xs font-semibold ${location?.status === "APPROVED" ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-800"}`}>{location?.status === "APPROVED" ? "Approved" : location?.status === "REJECTED" ? "Rejected" : "Pending"}</span>}
      </div>
      {!loading && <div className="mt-4 text-sm"><p className="font-semibold">{location?.workplaceName || location?.companyName || "Registered workplace unavailable"}</p><p className="mt-1 text-slate-600">{location?.workplaceAddress || (location ? "Legacy coordinates: no Google Places address is recorded. Use the correction workflow to register the actual branch." : "No workplace coordinates are registered. Ask the student to select their branch or register it through the authorized correction workflow.")}</p><LocationMapLink latitude={location?.latitude} longitude={location?.longitude} label="View workplace on Google Maps" /></div>}
      {loading ? <p className="mt-5 text-sm text-slate-500">Loading location…</p> : <div className="mt-6 grid gap-4 sm:grid-cols-3">
        <label className="text-sm font-semibold text-slate-700">Latitude<input type="number" step="0.000001" min="-90" max="90" value={latitude} readOnly className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" /></label>
        <label className="text-sm font-semibold text-slate-700">Longitude<input type="number" step="0.000001" min="-180" max="180" value={longitude} readOnly className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" /></label>
        <label className="text-sm font-semibold text-slate-700">Radius (meters)<input type="number" step="1" min="10" max="5000" value={radius} readOnly={scope !== "admin"} onChange={event => setRadius(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" /></label>
      </div>}
      {!loading && scope === "admin" && correction && <div className="mt-5 space-y-3"><WorkplacePlaceSelector value={selection} onSelect={setSelection}/><label className="block text-sm font-semibold">Correction reason<textarea value={reason} maxLength={500} onChange={e => setReason(e.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label><p className="text-xs text-slate-500">This change is audited, requires both approvals again, and affects future submissions only.</p></div>}
      {!loading && scope === "admin" && <div className="mt-5 flex flex-wrap gap-3"><button data-button-variant="outline" type="button" disabled={saving || confirming} onClick={() => { setCorrection(v => !v); setSelection(null); setReason(""); }} className="text-sm">{correction ? "Cancel correction" : "Correct registered workplace"}</button><button data-button-variant="outline" type="button" disabled={saving || confirming || !radius || Number(radius) < 10 || Number(radius) > 5000 || (correction ? !selection?.address || reason.trim().length < 5 : !location)} onClick={() => void save()} className="text-sm">{saving ? "Saving…" : correction ? "Save audited correction" : "Save radius"}</button></div>}
      {!loading && location?.workplaceAddress && <div className="mt-5 space-y-3"><div className="text-sm text-slate-600"><p>Workplace revision {location.revision}</p><p className="mt-2">Mentor: {location.mentorDecision || "PENDING"}{location.mentorReviewedAt && ` - user ${location.mentorReviewerId}, ${new Date(location.mentorReviewedAt).toLocaleString()}`}</p>{location.mentorRemarks && <p>{location.mentorRemarks}</p>}<p className="mt-2">III Cell: {location.iiicDecision || "PENDING"}{location.confirmedAt && ` - user ${location.confirmedBy}, ${new Date(location.confirmedAt).toLocaleString()}`}</p>{location.iiicRemarks && <p>{location.iiicRemarks}</p>}</div>{scope !== "student" && <><label className="block text-sm font-semibold">Review remarks (required for rejection)<textarea value={reason} maxLength={500} onChange={e => setReason(e.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label><div className="flex gap-3"><button disabled={saving || confirming || correction} onClick={() => void confirm("APPROVE")} className="text-sm">Approve workplace</button><button data-button-variant="outline" disabled={saving || confirming || correction || reason.trim().length<5} onClick={() => void confirm("REJECT")} className="text-sm">Reject workplace</button></div></>}</div>}
      {location?.status === "APPROVED" && location.confirmedAt && <p className="mt-4 text-xs text-slate-500">Confirmed {new Date(location.confirmedAt).toLocaleString()}.</p>}
      {error && <p role="alert" className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
    </section>
  );
}
