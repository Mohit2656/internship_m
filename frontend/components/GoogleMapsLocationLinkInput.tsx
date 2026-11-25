"use client";

import { useState } from "react";
import { inspectGoogleMapsLink, parseManualCoordinates, type GoogleMapsLinkResult } from "@/lib/google-maps-location-link";
import type { WorkplaceCoordinates } from "@/lib/workplace-location";

type Props = {
  disabled?: boolean;
  onManualCoordinates: (coordinates: WorkplaceCoordinates) => void;
  onManualEdit: () => void;
};

export function GoogleMapsLocationLinkInput({ disabled = false, onManualCoordinates, onManualEdit }: Props) {
  const [link, setLink] = useState("");
  const [inspection, setInspection] = useState<GoogleMapsLinkResult | null>(null);
  const [manualVisible, setManualVisible] = useState(false);
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [errors, setErrors] = useState<{ latitudeError?: string; longitudeError?: string; formError?: string }>({});

  function inspect() {
    if (disabled) return;
    const checked = inspectGoogleMapsLink(link);
    setInspection(checked);
    setManualVisible(true);
    setErrors({});
  }

  function updateLatitude(value: string) {
    setLatitude(value); setErrors({}); onManualEdit();
  }
  function updateLongitude(value: string) {
    setLongitude(value); setErrors({}); onManualEdit();
  }
  function preview() {
    if (disabled) return;
    const parsed = parseManualCoordinates(latitude, longitude);
    if (!parsed.ok) { setErrors(parsed); return; }
    setErrors({});
    onManualCoordinates(parsed.coordinates);
  }

  return <div className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
    <div><h4 className="text-sm font-semibold">Google Maps location link</h4><p className="mt-1 text-xs text-slate-600">Paste the link only as a reference. IMS does not contact Google, follow short links, or automatically save Google-derived coordinates.</p></div>
    <label className="block text-sm font-semibold">Google Maps link<input type="url" className="input mt-1" value={link} disabled={disabled} placeholder="https://www.google.com/maps/..." maxLength={2048} onChange={event => { setLink(event.target.value); setInspection(null); }} /></label>
    <div className="flex flex-wrap gap-2"><button type="button" data-button-variant="outline" disabled={disabled || !link.trim()} onClick={inspect}>Check link</button><button type="button" data-button-variant="text" disabled={disabled} onClick={() => { setManualVisible(true); setInspection(null); }}>Enter coordinates manually</button></div>
    {inspection && <div role={inspection.kind === "INVALID" ? "alert" : "status"} data-link-kind={inspection.kind} className={`rounded-lg p-3 text-sm ${inspection.kind === "INVALID" ? "bg-red-50 text-red-700" : "bg-amber-50 text-amber-900"}`}><p className="font-semibold">{inspection.kind === "EXPLICIT_COORDINATES" ? "Explicit coordinate link detected" : "Manual coordinates required"}</p><p className="mt-1">{inspection.message}</p><button type="button" className="mt-2 text-sm underline" disabled={disabled} onClick={() => setManualVisible(true)}>The location is incorrect or unavailable</button></div>}
    {manualVisible && <fieldset className="space-y-3" disabled={disabled}><legend className="text-sm font-semibold">Manual workplace coordinates</legend><p className="text-xs text-slate-600">Enter the office pin coordinates, then preview them on the map. They remain a draft until you confirm the workplace location below.</p><div className="grid gap-3 sm:grid-cols-2"><label className="text-sm font-semibold">Latitude<input className="input mt-1" inputMode="decimal" value={latitude} onChange={event => updateLatitude(event.target.value)} aria-invalid={Boolean(errors.latitudeError)} />{errors.latitudeError && <span className="mt-1 block text-xs text-red-700">{errors.latitudeError}</span>}</label><label className="text-sm font-semibold">Longitude<input className="input mt-1" inputMode="decimal" value={longitude} onChange={event => updateLongitude(event.target.value)} aria-invalid={Boolean(errors.longitudeError)} />{errors.longitudeError && <span className="mt-1 block text-xs text-red-700">{errors.longitudeError}</span>}</label></div>{errors.formError && <p role="alert" className="text-sm text-red-700">{errors.formError}</p>}<button type="button" data-button-variant="outline" onClick={preview}>Preview manual coordinates</button></fieldset>}
  </div>;
}
