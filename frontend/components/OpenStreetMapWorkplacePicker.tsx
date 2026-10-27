"use client";

import dynamic from "next/dynamic";
import { useEffect, useRef, useState } from "react";
import type { RegisteredWorkplace } from "@/lib/internship-details";
import { confirmedWorkplaceMatches, validWorkplaceCoordinates, workplaceGeolocationError, type WorkplaceCoordinates } from "@/lib/workplace-location";
import { WorkplaceLocationSearch, type WorkplaceSearchResult } from "@/components/WorkplaceLocationSearch";

const WorkplaceMap = dynamic(() => import("./OpenStreetMapWorkplaceMap"), {
  ssr: false,
  loading: ({ error, retry }) => error
    ? <div role="alert" className="rounded-lg bg-red-50 p-4 text-sm text-red-700">The workplace map could not be loaded. <button type="button" onClick={retry}>Retry map</button></div>
    : <div role="status" className="flex h-72 items-center justify-center rounded-lg bg-slate-50 text-sm text-slate-600">Loading workplace map…</div>,
});

type Props = {
  value: RegisteredWorkplace | null;
  onSelect: (workplace: RegisteredWorkplace | null) => void;
  onPendingChange?: (pending: boolean) => void;
  address?: string;
  onAddressChange?: (address: string) => void;
  name?: string;
  disabled?: boolean;
};

/** Independent of Google Places. A moved pin is a draft until explicitly confirmed. */
export function OpenStreetMapWorkplacePicker({ value, onSelect, onPendingChange, address, onAddressChange, name, disabled = false }: Props) {
  const [draft, setDraft] = useState<WorkplaceCoordinates | null>(() => validWorkplaceCoordinates(value) ? { latitude: value.latitude, longitude: value.longitude } : null);
  const [manualAddress, setManualAddress] = useState(value?.address ?? "");
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState("");
  const [mapError, setMapError] = useState(false);
  const [mapRevision, setMapRevision] = useState(0);
  const requestEpoch = useRef(0);
  const workplaceAddress = (address ?? manualAddress).trim();
  const confirmed = confirmedWorkplaceMatches(value, draft, workplaceAddress);
  const pending = locating || (draft !== null && !confirmed);

  useEffect(() => {
    if (validWorkplaceCoordinates(value)) {
      setDraft({ latitude: value.latitude, longitude: value.longitude });
      setManualAddress(value.address);
    }
  }, [value?.latitude, value?.longitude, value?.address]);
  useEffect(() => { onPendingChange?.(pending); }, [pending, onPendingChange]);
  useEffect(() => () => { requestEpoch.current++; }, []);
  useEffect(() => { if (disabled) { requestEpoch.current++; setLocating(false); } }, [disabled]);

  function choose(coordinates: WorkplaceCoordinates) {
    if (disabled || !validWorkplaceCoordinates(coordinates)) return;
    requestEpoch.current++;
    setLocating(false);
    setDraft(coordinates);
    setError("");
    onSelect(null);
  }

  function locate() {
    if (disabled || locating) return;
    setError("");
    const epoch = ++requestEpoch.current;
    setLocating(true);
    try {
      if (typeof navigator === "undefined" || !navigator.geolocation) { setLocating(false); setError("This browser does not support device location. Select your workplace on the map."); return; }
      navigator.geolocation.getCurrentPosition(position => {
        if (epoch !== requestEpoch.current) return;
        const coordinates = { latitude: position.coords.latitude, longitude: position.coords.longitude };
        if (!validWorkplaceCoordinates(coordinates)) { setLocating(false); setError("Your device returned invalid coordinates. Retry, or select your workplace on the map."); return; }
        choose(coordinates);
      }, failure => {
        if (epoch !== requestEpoch.current) return;
        setLocating(false);
        setError(workplaceGeolocationError(failure));
      }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
    } catch {
      if (epoch !== requestEpoch.current) return;
      setLocating(false);
      setError("Your browser could not request location access. Check your location permissions and retry, or select your workplace on the map.");
    }
  }

  function selectSearchResult(result: WorkplaceSearchResult) {
    if (disabled || !validWorkplaceCoordinates(result)) return;
    choose({ latitude: result.latitude, longitude: result.longitude });
    if (address === undefined) setManualAddress(result.address);
    else onAddressChange?.(result.address);
  }

  function confirm() {
    if (disabled || locating || !validWorkplaceCoordinates(draft)) return;
    if (!workplaceAddress) { setError("Enter the company address before confirming the workplace location."); return; }
    // Reconfirming saved evidence must retain its provider metadata and approvals.
    onSelect(confirmed && value ? value : { name: name?.trim() || value?.name || null, placeId: null, address: workplaceAddress, ...draft });
    setError("");
  }

  return <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4" aria-label="OpenStreetMap workplace location picker">
    <div><h3 className="text-sm font-semibold">Workplace location on OpenStreetMap</h3><p className="mt-1 text-xs text-slate-600">Click the map or drag the pin to the actual workplace entrance, then confirm. Map centre alone does not select a location.</p></div>
    <WorkplaceLocationSearch disabled={disabled} onSelect={selectSearchResult} />
    <div className="flex flex-wrap items-center gap-3"><button type="button" disabled={disabled || locating} onClick={locate} data-button-variant="outline" className="text-sm">{locating ? "Finding your location…" : error ? "Retry current location" : "Use my current location"}</button>{locating && <span role="status" className="text-xs text-slate-600">Waiting for device location permission and GPS…</span>}</div>
    <WorkplaceMap key={mapRevision} position={draft} disabled={disabled} onSelect={choose} onTileError={() => setMapError(true)} />
    {mapError && <p role="alert" className="text-sm text-amber-800">Some map tiles could not load. Check your connection. <button type="button" disabled={disabled} onClick={() => { setMapError(false); setMapRevision(revision => revision + 1); }} data-button-variant="outline">Retry map</button></p>}
    {address === undefined && <label className="block text-sm font-semibold">Workplace address<input className="input mt-1" value={manualAddress} maxLength={1000} disabled={disabled} onChange={event => { setManualAddress(event.target.value); onSelect(null); }} /></label>}
    {draft ? <p className="rounded-lg bg-slate-50 p-3 text-sm">Selected latitude: <strong>{draft.latitude.toFixed(6)}</strong> · longitude: <strong>{draft.longitude.toFixed(6)}</strong></p> : <p className="text-sm text-slate-600">No workplace coordinates selected.</p>}
    <div className="flex flex-wrap items-center gap-3"><button type="button" onClick={confirm} disabled={disabled || locating || !draft || !workplaceAddress} className="text-sm">Confirm workplace location</button><span role="status" className={`text-xs ${confirmed ? "text-emerald-700" : "text-slate-600"}`}>{confirmed ? "Location confirmed. Save the form to submit it for workplace approval." : pending ? "Location is not confirmed. Confirm the pin before saving." : "Choose your workplace on the map."}</span></div>
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    <p className="text-xs text-slate-500">Search runs only when you press Search. Device location is requested only when you use the current location button. <a href="https://www.openstreetmap.org/fixthemap" target="_blank" rel="noopener noreferrer" className="underline">Report a map issue</a>.</p>
  </section>;
}
