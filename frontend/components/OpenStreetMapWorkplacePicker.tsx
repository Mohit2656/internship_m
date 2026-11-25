"use client";

import { useEffect, useState } from "react";
import { LocationMapLink } from "@/components/LocationMapLink";
import type { RegisteredWorkplace } from "@/lib/internship-details";
import { confirmedWorkplaceMatches, validWorkplaceCoordinates, type WorkplaceCoordinates } from "@/lib/workplace-location";

type Props = { value: RegisteredWorkplace | null; onSelect: (workplace: RegisteredWorkplace | null) => void; onPendingChange?: (pending: boolean) => void; address?: string; onAddressChange?: (address: string) => void; name?: string; geofenceRadiusMeters?: number | null; disabled?: boolean };

export function OpenStreetMapWorkplacePicker({ value, onSelect, onPendingChange, address, name, geofenceRadiusMeters, disabled = false }: Props) {
  const [draft, setDraft] = useState<WorkplaceCoordinates | null>(() => validWorkplaceCoordinates(value) ? { latitude: value.latitude, longitude: value.longitude } : null);
  const [latitudeText, setLatitudeText] = useState(value ? String(value.latitude) : "");
  const [longitudeText, setLongitudeText] = useState(value ? String(value.longitude) : "");
  const [manualAddress, setManualAddress] = useState(value?.address ?? "");
  const [error, setError] = useState("");
  const workplaceAddress = (address ?? manualAddress).trim();
  const confirmed = confirmedWorkplaceMatches(value, draft, workplaceAddress);
  const pending = draft !== null && !confirmed;

  useEffect(() => { if (validWorkplaceCoordinates(value)) { setDraft({ latitude: value.latitude, longitude: value.longitude }); setLatitudeText(String(value.latitude)); setLongitudeText(String(value.longitude)); setManualAddress(value.address); } }, [value?.latitude, value?.longitude, value?.address]);
  useEffect(() => { onPendingChange?.(pending); }, [pending, onPendingChange]);

  function edit(setter: (value: string) => void, text: string) { if (!disabled) { setter(text); setDraft(null); setError(""); onSelect(null); } }
  function useCoordinates() {
    if (disabled) return;
    const coordinates = { latitude: Number(latitudeText.trim()), longitude: Number(longitudeText.trim()) };
    if (!latitudeText.trim() || !longitudeText.trim() || !validWorkplaceCoordinates(coordinates)) { setError("Enter a valid latitude between -90 and 90 and longitude between -180 and 180."); return; }
    setDraft(coordinates); setError(""); onSelect(null);
  }
  function confirm() {
    if (disabled || !validWorkplaceCoordinates(draft)) return;
    if (!workplaceAddress) { setError("Enter the company address before confirming the workplace location."); return; }
    onSelect(confirmed && value ? value : { name: name?.trim() || value?.name || null, placeId: null, address: workplaceAddress, ...draft }); setError("");
  }

  return <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4" aria-label="Workplace coordinate entry">
    <div><h3 className="text-sm font-semibold">Workplace coordinates</h3><p className="mt-1 text-xs text-slate-600">Enter the exact office latitude and longitude, open the point in Google Maps to verify it, then confirm.</p></div>
    <fieldset className="space-y-3" disabled={disabled}><div className="grid gap-3 sm:grid-cols-2"><label className="text-sm font-semibold">Latitude<input required className="input mt-1" inputMode="decimal" value={latitudeText} onChange={event => edit(setLatitudeText, event.target.value)} /></label><label className="text-sm font-semibold">Longitude<input required className="input mt-1" inputMode="decimal" value={longitudeText} onChange={event => edit(setLongitudeText, event.target.value)} /></label></div><button type="button" data-button-variant="outline" onClick={useCoordinates}>Use coordinates</button></fieldset>
    {address === undefined && <label className="block text-sm font-semibold">Workplace address<input className="input mt-1" value={manualAddress} maxLength={1000} disabled={disabled} onChange={event => { setManualAddress(event.target.value); onSelect(null); }} /></label>}
    {draft ? <div className="rounded-lg bg-slate-50 p-3 text-sm"><p><span className="font-semibold">Workplace:</span> {name?.trim() || value?.name || "Not provided"}</p><p className="mt-1"><span className="font-semibold">Address:</span> {workplaceAddress || "Not provided"}</p><p className="mt-1">Latitude: <strong>{draft.latitude.toFixed(6)}</strong> · longitude: <strong>{draft.longitude.toFixed(6)}</strong></p><p className="mt-1"><span className="font-semibold">Source:</span> Student-entered coordinates</p><p className="mt-1"><span className="font-semibold">Geofence radius:</span> {typeof geofenceRadiusMeters === "number" && Number.isFinite(geofenceRadiusMeters) ? `${geofenceRadiusMeters} metres` : "Assigned by the server when saved."}</p><LocationMapLink latitude={draft.latitude} longitude={draft.longitude} label="Open in Google Maps" /></div> : <p className="text-sm text-slate-600">Enter both coordinates to verify the workplace externally.</p>}
    <div className="flex flex-wrap items-center gap-3"><button type="button" onClick={confirm} disabled={disabled || !draft || !workplaceAddress} className="text-sm">Confirm workplace location</button><span role="status" className={`text-xs ${confirmed ? "text-emerald-700" : "text-slate-600"}`}>{confirmed ? "Location confirmed. Save the form to submit it for mentor approval." : pending ? "Verify the coordinates externally, then confirm them." : "Enter the workplace coordinates."}</span></div>
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
  </section>;
}
