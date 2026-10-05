"use client";

import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { api, ApiError } from "@/lib/api";
import { InternshipLocation } from "@/lib/attendance";

export function InternshipLocationManager({ studentId }: { studentId: number }) {
  const [location, setLocation] = useState<InternshipLocation | null>(null);
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [radius, setRadius] = useState("200");
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
    api.get<InternshipLocation>(`/admin/students/${studentId}/internship/location`)
      .then(response => useLocation(response.data))
      .catch((cause: AxiosError) => {
        if (cause.response?.status !== 404) setError("Internship location could not be loaded.");
      })
      .finally(() => setLoading(false));
  }, [studentId]);

  async function save() {
    setSaving(true); setError("");
    try {
      const { data } = await api.put<InternshipLocation>(`/admin/students/${studentId}/internship/location`, {
        latitude: Number(latitude), longitude: Number(longitude), geofenceRadiusMeters: Number(radius)
      });
      useLocation(data);
    } catch (cause) {
      setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Location could not be saved.");
    } finally { setSaving(false); }
  }

  async function confirm() {
    setConfirming(true); setError("");
    try {
      const { data } = await api.post<InternshipLocation>(`/admin/students/${studentId}/internship/location/confirm`);
      useLocation(data);
    } catch (cause) {
      setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Location could not be confirmed.");
    } finally { setConfirming(false); }
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-7">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Physical Attendance Location</p><h2 className="mt-2 text-xl font-bold">Trusted company coordinates</h2><p className="mt-2 text-sm text-slate-600">Review the proposed coordinates and radius before confirming them for automatic attendance checks.</p></div>
        {!loading && <span className={`rounded-full px-3 py-1.5 text-xs font-semibold ${location?.status === "CONFIRMED" ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-800"}`}>{location?.status === "CONFIRMED" ? "Confirmed" : "Pending"}</span>}
      </div>
      {loading ? <p className="mt-5 text-sm text-slate-500">Loading location…</p> : <div className="mt-6 grid gap-4 sm:grid-cols-3">
        <label className="text-sm font-semibold text-slate-700">Latitude<input type="number" step="0.000001" min="-90" max="90" value={latitude} onChange={event => setLatitude(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" /></label>
        <label className="text-sm font-semibold text-slate-700">Longitude<input type="number" step="0.000001" min="-180" max="180" value={longitude} onChange={event => setLongitude(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" /></label>
        <label className="text-sm font-semibold text-slate-700">Radius (meters)<input type="number" step="1" min="10" max="5000" value={radius} onChange={event => setRadius(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" /></label>
      </div>}
      {!loading && <div className="mt-5 flex flex-wrap gap-3"><button type="button" disabled={saving || !latitude || !longitude || !radius} onClick={() => void save()} className="rounded-xl border border-slate-300 px-5 py-2.5 text-sm font-semibold disabled:opacity-50">{saving ? "Saving…" : location ? "Save changes" : "Save proposed location"}</button>{location?.status === "PENDING" && <button type="button" disabled={confirming} onClick={() => void confirm()} className="rounded-xl bg-blue-700 px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-50">{confirming ? "Confirming…" : "Confirm location"}</button>}</div>}
      {location?.status === "CONFIRMED" && location.confirmedAt && <p className="mt-4 text-xs text-slate-500">Confirmed {new Date(location.confirmedAt).toLocaleString()}.</p>}
      {error && <p role="alert" className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
    </section>
  );
}
