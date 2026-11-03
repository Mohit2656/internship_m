"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { AxiosError } from "axios";
import { api, ApiError } from "@/lib/api";
import { OpenStreetMapWorkplacePicker } from "@/components/OpenStreetMapWorkplacePicker";
import { LocationMapLink } from "@/components/LocationMapLink";
import type { RegisteredWorkplace } from "@/lib/internship-details";
import { InternshipLocation } from "@/lib/attendance";

export type InternshipTabItem = {
  id: number;
  internshipNumber: number;
  companyName?: string;
  status?: string;
};

export function InternshipLocationManager({
  studentId,
  scope = "admin",
  internshipId,
  internships,
  initialInternshipId,
}: ({
  studentId: number;
  scope?: "admin" | "mentor";
} | {
  scope: "student";
  studentId?: never;
}) & {
  internshipId?: number;
  internships?: InternshipTabItem[];
  initialInternshipId?: number;
}) {
  const [correction, setCorrection] = useState(false);
  const [selection, setSelection] = useState<RegisteredWorkplace | null>(null);
  const [reason, setReason] = useState("");
  const [location, setLocation] = useState<InternshipLocation | null>(null);
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [radius, setRadius] = useState("200");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState("");
  const [correctionPending, setCorrectionPending] = useState(false);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [selectedInternshipId, setSelectedInternshipId] = useState<number | undefined>(
    internshipId ?? initialInternshipId ?? (internships && internships.length > 0 ? internships[0].id : undefined)
  );
  const requestVersion = useRef(0);
  const mutationPending = useRef(false);

  useEffect(() => {
    if (initialInternshipId) {
      setSelectedInternshipId(initialInternshipId);
    }
  }, [initialInternshipId]);

  const activeInternshipId = internshipId ?? selectedInternshipId ?? (internships && internships.length > 0 ? internships[0].id : undefined);
  const selectedInternship = internships?.find((i) => i.id === activeInternshipId);

  const radiusChanged = Boolean(location && Number(radius) !== Number(location.geofenceRadiusMeters));
  const workplaceApproved = location?.status === "APPROVED";
  const missingMentor = location?.mentorDecision !== "APPROVE";
  const statusLabel = !location
    ? error
      ? "Unavailable"
      : scope === "student"
      ? "Not submitted"
      : "Not registered"
    : location.status === "APPROVED"
    ? "Approved"
    : location.status === "REJECTED"
    ? (scope === "student" ? "Rejected / Correction required" : "Rejected")
    : "Pending mentor approval";
  const approvalMessage = !location?.workplaceAddress
    ? ""
    : location.status === "APPROVED"
    ? "The assigned mentor has approved this workplace for attendance geofence verification."
    : location.status === "REJECTED"
    ? "This workplace was rejected. Review the decisions and remarks below before submitting a correction or reviewing it again."
    : missingMentor
    ? "Waiting for the assigned mentor to approve or reject this workplace."
    : "The stored mentor approval is no longer valid for the currently assigned mentor. Fresh mentor verification is required.";

  function useLocation(value: InternshipLocation) {
    setLocation(value);
    setLatitude(String(value.latitude));
    setLongitude(String(value.longitude));
    setRadius(String(value.geofenceRadiusMeters));
  }

  useEffect(() => {
    const abort = new AbortController();
    const version = ++requestVersion.current;
    mutationPending.current = false;
    setLoading(true); setLocation(null); setLatitude(""); setLongitude(""); setRadius("200"); setCorrection(false); setSelection(null); setReason(""); setError(""); setCorrectionPending(false); setSaving(false); setConfirming(false);
    api.get<InternshipLocation>((scope === "student" ? "/student/internship/location" : `/${scope}/students/${studentId}/internship/location`), { signal: abort.signal, params: activeInternshipId ? { internshipId: activeInternshipId } : undefined })
      .then(response => { if (!abort.signal.aborted && version === requestVersion.current) useLocation(response.data); })
      .catch((cause: AxiosError) => {
        if (!abort.signal.aborted && version === requestVersion.current && cause.response?.status !== 404) setError("Internship location could not be loaded.");
      })
      .finally(() => { if (!abort.signal.aborted && version === requestVersion.current) setLoading(false); });
    return () => { abort.abort(); requestVersion.current++; };
  }, [studentId, scope, activeInternshipId, refreshVersion]);

  useEffect(() => {
    if (typeof window === "undefined" || loading || saving || confirming || correction || radiusChanged || reason.trim()) return;
    let active = true;
    let pending: AbortController | null = null;
    function refresh() {
      if (!active || pending || mutationPending.current || (typeof document !== "undefined" && document.visibilityState === "hidden")) return;
      const abort = new AbortController(); pending = abort;
      const version = ++requestVersion.current;
      api.get<InternshipLocation>(scope === "student" ? "/student/internship/location" : `/${scope}/students/${studentId}/internship/location`, { signal: abort.signal, params: activeInternshipId ? { internshipId: activeInternshipId } : undefined })
        .then(({ data }) => { if (active && !abort.signal.aborted && version === requestVersion.current) { useLocation(data); setError(""); } })
        .catch((cause: AxiosError) => {
          if (!active || abort.signal.aborted || version !== requestVersion.current) return;
          if (cause.response?.status === 404) {
            setLocation(null);
            setError("");
          } else {
            setError("Workplace status could not be refreshed. Showing the last loaded status; retry Refresh status.");
          }
        })
        .finally(() => { if (pending === abort) pending = null; });
    }
    window.addEventListener("focus", refresh);
    if (typeof document !== "undefined") document.addEventListener("visibilitychange", refresh);
    const timer = window.setInterval(refresh, 30_000);
    return () => {
      active = false; pending?.abort();
      window.removeEventListener("focus", refresh);
      if (typeof document !== "undefined") document.removeEventListener("visibilitychange", refresh);
      window.clearInterval(timer);
    };
  }, [studentId, scope, activeInternshipId, refreshVersion, loading, saving, confirming, correction, radiusChanged, reason]);

  async function save() {
    if (mutationPending.current || (correction && (correctionPending || !selection?.address || reason.trim().length < 5))) return;
    mutationPending.current = true;
    const version = ++requestVersion.current;
    setSaving(true); setError("");
    try {
      const { data } = await api.put<InternshipLocation>(`/admin/students/${studentId}/internship/location/${correction ? "correction" : "radius"}`, correction ? {
        workplace: selection, radiusMeters: Number(radius), reason: reason.trim()
      } : { radiusMeters: Number(radius) });
      if (version !== requestVersion.current) return;
      setCorrection(false); setSelection(null); setReason(""); setCorrectionPending(false);
      useLocation(data);
    } catch (cause) {
      if (version === requestVersion.current) setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Location could not be saved.");
    } finally { if (version === requestVersion.current) { mutationPending.current = false; setSaving(false); } }
  }

  async function confirm(decision: "APPROVE" | "REJECT" = "APPROVE") {
    if (mutationPending.current || !location || !location.latitude || !location.longitude || correction || radiusChanged || (decision === "APPROVE" && workplaceApproved)) return;
    mutationPending.current = true;
    const version = ++requestVersion.current;
    setConfirming(true); setError("");
    try {
      const { data } = await api.post<InternshipLocation>(`/${scope}/students/${studentId}/internship/location/review`, { revision: location?.revision, decision, remarks: reason.trim() || null }, { params: activeInternshipId ? { internshipId: activeInternshipId } : undefined });
      if (version !== requestVersion.current) return;
      useLocation(data);
      setReason("");
    } catch (cause) {
      if (version === requestVersion.current) setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Location could not be confirmed.");
    } finally { if (version === requestVersion.current) { mutationPending.current = false; setConfirming(false); } }
  }

  return (
    <section
      className="rounded-2xl border border-slate-200 bg-white p-7"
      data-workplace-scope={scope}
      {...(studentId ? { "data-student-id": studentId } : {})}
    >
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Physical Attendance Location</p>
          <h2 className="mt-2 text-xl font-bold">Registered workplace</h2>
          {scope === "student" ? (
            <p className="mt-2 text-sm text-slate-600">
              Workplace coordinates saved during internship details submission are used for attendance geofence verification.
            </p>
          ) : (
            <p className="mt-2 text-sm text-slate-600">
              The assigned mentor verifies the registered branch and coordinates using the external map link. III Cell access is administrative and read-only for workplace approval.
            </p>
          )}
        </div>
        {!loading && (
          <span
            className={`rounded-full px-3 py-1.5 text-xs font-semibold ${
              location?.status === "APPROVED"
                ? "bg-emerald-50 text-emerald-800"
                : location?.status === "REJECTED"
                ? "bg-rose-50 text-rose-800"
                : "bg-amber-50 text-amber-800"
            }`}
          >
            {statusLabel}
          </span>
        )}
      </div>

      {/* Tabs for multiple internships (student scope) */}
      {scope === "student" && internships && internships.length > 1 && (
        <div className="mt-4 flex flex-wrap items-center gap-1 rounded-lg bg-slate-100/90 p-1 border border-slate-200/80 shadow-xs" role="tablist" aria-label="Internship workplace selection">
          {internships.map((item) => {
            const isSelected = item.id === activeInternshipId;
            return (
              <button
                key={item.id}
                type="button"
                role="tab"
                aria-selected={isSelected}
                onClick={() => setSelectedInternshipId(item.id)}
                className={`segmented-tab flex items-center gap-2 rounded-md px-3.5 py-1.5 text-xs font-semibold transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
                  isSelected
                    ? "bg-blue-50 text-blue-700 border border-blue-200/90 shadow-xs font-semibold"
                    : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
                }`}
              >
                <span>Internship {item.internshipNumber}</span>
                {item.companyName && (
                  <span className={isSelected ? "text-blue-700/80" : "text-slate-500"}>
                    ({item.companyName})
                  </span>
                )}
                {item.status && (
                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-semibold border ${
                      isSelected
                        ? "border-blue-200 bg-blue-100 text-blue-800"
                        : "bg-white text-slate-700 border-slate-200"
                    }`}
                  >
                    {item.status}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      )}

      {loading ? (
        <p className="mt-5 text-sm text-slate-500">Loading location…</p>
      ) : scope === "student" ? (
        /* STUDENT-FACING READ-ONLY VIEW */
        !location ? (
          <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50/70 p-4 text-sm text-amber-900">
            <p className="font-semibold">Workplace location not submitted</p>
            <p className="mt-1 text-xs text-amber-800">
              Workplace coordinates have not been submitted for this internship. Please submit your workplace location in the internship details form.
            </p>
            <div className="mt-3">
              <Link
                href={selectedInternship?.internshipNumber === 2 ? "/student/internship/details?tab=2" : "/student/internship/details"}
                className="inline-flex items-center gap-1.5 rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-amber-700"
              >
                <span>Submit workplace location</span>
                <span aria-hidden="true">→</span>
              </Link>
            </div>
          </div>
        ) : (
          <div className="mt-5 space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-3.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Company Name</p>
                <p className="mt-1 text-sm font-bold text-slate-900">
                  {location.companyName || selectedInternship?.companyName || "—"}
                </p>
              </div>
              <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-3.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Registered Workplace</p>
                <p className="mt-1 text-sm font-semibold text-slate-900">
                  {location.workplaceName || location.companyName || "Registered workplace"}
                </p>
                {location.workplaceAddress && (
                  <p className="mt-0.5 text-xs text-slate-600">{location.workplaceAddress}</p>
                )}
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-3.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Latitude</p>
                <p className="mt-1 text-sm font-bold text-slate-900 font-mono">
                  {location.latitude != null ? Number(location.latitude).toFixed(6) : "—"}
                </p>
              </div>
              <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-3.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Longitude</p>
                <p className="mt-1 text-sm font-bold text-slate-900 font-mono">
                  {location.longitude != null ? Number(location.longitude).toFixed(6) : "—"}
                </p>
              </div>
              <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-3.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Attendance Geofence Radius</p>
                <p className="mt-1 text-sm font-bold text-slate-900">
                  {location.geofenceRadiusMeters ?? 200} meters
                </p>
              </div>
            </div>

            <div className="pt-1">
              <LocationMapLink latitude={location.latitude} longitude={location.longitude} label="Open in Google Maps" />
            </div>

            {approvalMessage && (
              <p role="status" className="mt-2 rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
                {approvalMessage}
              </p>
            )}

            {location.coordinatesLocked && (
              <p role="status" className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm font-semibold text-emerald-800">
                Location approved by mentor — student coordinates are locked for this workplace revision.
              </p>
            )}
          </div>
        )
      ) : (
        /* MENTOR / ADMIN VIEW */
        <>
          <div className="mt-4 text-sm">
            <p className="font-semibold">{location?.workplaceName || location?.companyName || "Registered workplace unavailable"}</p>
            <p className="mt-1 text-slate-600">
              {location?.workplaceAddress || (location ? "Legacy coordinates: no workplace address is recorded. Use the correction workflow to register the actual branch." : "No workplace coordinates are registered. Ask the student to register the exact coordinates.")}
            </p>
            <LocationMapLink latitude={location?.latitude} longitude={location?.longitude} label="Open in Google Maps" />
          </div>
          {approvalMessage && <p role="status" className="mt-4 rounded-lg bg-slate-50 p-3 text-sm text-slate-700">{approvalMessage}</p>}

          <div className="mt-6 grid gap-4 sm:grid-cols-3">
            <label className="text-sm font-semibold text-slate-700">Latitude<input type="number" step="0.000001" min="-90" max="90" value={latitude} readOnly className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" /></label>
            <label className="text-sm font-semibold text-slate-700">Longitude<input type="number" step="0.000001" min="-180" max="180" value={longitude} readOnly className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" /></label>
            <label className="text-sm font-semibold text-slate-700">Radius (meters)<input type="number" step="1" min="10" max="5000" value={radius} readOnly={scope !== "admin"} onChange={event => setRadius(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" /></label>
          </div>

          {scope === "admin" && correction && (
            <div className="mt-5 space-y-3">
              <OpenStreetMapWorkplacePicker value={selection} name={location?.workplaceName || location?.companyName} geofenceRadiusMeters={location?.geofenceRadiusMeters} disabled={saving || confirming} onSelect={setSelection} onPendingChange={setCorrectionPending}/>
              <label className="block text-sm font-semibold">Correction reason<textarea value={reason} maxLength={500} onChange={e => setReason(e.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label>
              <p className="text-xs text-slate-500">This change is audited, invalidates the previous mentor approval, requires fresh mentor verification, and affects future submissions only.</p>
            </div>
          )}

          {scope === "admin" && (
            <>
              <div className="mt-5 flex flex-wrap gap-3">
                <button data-button-variant="outline" type="button" disabled={saving || confirming} onClick={() => { setCorrection(v => !v); setSelection(!correction && location?.workplaceAddress ? { name: location.workplaceName, placeId: location.placeId, address: location.workplaceAddress, latitude: Number(location.latitude), longitude: Number(location.longitude) } : null); setReason(""); setCorrectionPending(false); }} className="text-sm">{correction ? "Cancel correction" : "Correct registered workplace"}</button>
                <button data-button-variant="outline" type="button" disabled={saving || confirming || !radius || Number(radius) < 10 || Number(radius) > 5000 || (correction ? correctionPending || !selection?.address || reason.trim().length < 5 : !location || !radiusChanged)} onClick={() => void save()} className="text-sm">{saving ? "Saving…" : correction ? "Save audited correction" : "Save radius"}</button>
              </div>
              {radiusChanged && <p className="mt-3 text-sm text-amber-800">Saving a changed radius clears mentor approval and requires a new mentor review. Save the radius or restore its previous value before reviewing.</p>}
            </>
          )}

          {location && (
            <div className="mt-5 space-y-3">
              <div className="text-sm text-slate-600">
                <p>Workplace revision {location.revision}</p>
                <p className="mt-2">Mentor: {location.mentorDecision || "PENDING"}{location.mentorReviewedAt && ` - user ${location.mentorReviewerId}, ${new Date(location.mentorReviewedAt).toLocaleString()}`}</p>
                {location.mentorRemarks && <p>{location.mentorRemarks}</p>}
                {(location.iiicDecision || location.confirmedAt || location.iiicRemarks) && (
                  <div className="mt-2">
                    <p>Historical III Cell decision: {location.iiicDecision || "—"}{location.confirmedAt && ` - user ${location.confirmedBy}, ${new Date(location.confirmedAt).toLocaleString()}`}</p>
                    {location.iiicRemarks && <p>{location.iiicRemarks}</p>}
                    <p className="text-xs">Retained for audit history; it does not control current workplace eligibility.</p>
                  </div>
                )}
              </div>
              {scope === "mentor" && !correction && (
                <>
                  <label className="block text-sm font-semibold">Review remarks (required for rejection)<textarea value={reason} maxLength={500} onChange={e => setReason(e.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label>
                  <div className="flex gap-3">
                    <button data-button-variant={workplaceApproved ? "approved" : undefined} disabled={saving || confirming || radiusChanged || workplaceApproved} onClick={() => void confirm("APPROVE")} className="text-sm">{workplaceApproved ? "Approved" : "Approve workplace"}</button>
                    <button data-button-variant="outline" disabled={saving || confirming || radiusChanged || reason.trim().length < 5 || Boolean(location.coordinatesLocked)} onClick={() => void confirm("REJECT")} className="text-sm">Reject workplace</button>
                  </div>
                </>
              )}
            </div>
          )}

          {location?.coordinatesLocked && (
            <p role="status" className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm font-semibold text-emerald-800">
              Location approved by mentor — student coordinates are locked for this workplace revision.
            </p>
          )}
        </>
      )}

      {!loading && (
        <button
          data-button-variant="outline"
          type="button"
          disabled={saving || confirming || correction || radiusChanged}
          onClick={() => setRefreshVersion(value => value + 1)}
          className="mt-4 text-sm"
        >
          Refresh status
        </button>
      )}
      {location?.status === "APPROVED" && location.confirmedAt && (
        <p className="mt-4 text-xs text-slate-500">Confirmed {new Date(location.confirmedAt).toLocaleString()}.</p>
      )}
      {error && (
        <p role="alert" className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>
      )}
    </section>
  );
}
