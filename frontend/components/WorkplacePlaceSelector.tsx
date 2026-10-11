"use client";

import { useEffect, useRef, useState } from "react";
import Script from "next/script";
import type { RegisteredWorkplace } from "@/lib/internship-details";

type Place = { id: string; displayName?: string; formattedAddress?: string; location?: { lat(): number; lng(): number }; fetchFields(options: { fields: string[] }): Promise<unknown> };
type PlacesApi = { maps: { importLibrary(name: "places"): Promise<{ PlaceAutocompleteElement: new () => HTMLElement }> } };

export function WorkplacePlaceSelector({ value, onSelect }: { value: RegisteredWorkplace | null; onSelect: (place: RegisteredWorkplace) => void }) {
  // Retained for reversibility; no active page mounts workplace registration while attendance is liveness-only.
  const key = process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY;
  const mapHost = useRef<HTMLDivElement>(null);
  const mapKey = process.env.NEXT_PUBLIC_GOOGLE_MAP_ID || "DEMO_MAP_ID";
  const host = useRef<HTMLDivElement>(null);
  const select = useRef(onSelect);
  select.current = onSelect;
  const selectionEpoch = useRef(0);
  const [mapRevision, setMapRevision] = useState(0);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!ready || !host.current) return;
    const container = host.current;
    let disposed = false;
    let widget: HTMLElement | undefined;
    async function initialize() {
      try {
        const google = (window as unknown as { google: PlacesApi }).google;
        const library = await google.maps.importLibrary("places");
        if (disposed) return;
        widget = new library.PlaceAutocompleteElement();
        widget.setAttribute("aria-label", "Search registered workplace on Google Places");
        widget.className = "w-full";
        widget.addEventListener("gmp-error", () => { if (!disposed) setError("Google Places is unavailable. Check the API key and its enabled APIs."); });
        widget.addEventListener("gmp-select", async event => {
          const epoch = ++selectionEpoch.current;
          try {
            const place = (event as Event & { placePrediction: { toPlace(): Place } }).placePrediction.toPlace();
            await place.fetchFields({ fields: ["id", "displayName", "formattedAddress", "location"] });
            if (disposed || epoch !== selectionEpoch.current) return;
            const latitude = place.location?.lat(), longitude = place.location?.lng();
            if (!place.id || !place.formattedAddress || typeof latitude !== "number" || typeof longitude !== "number" || !Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
              setError("Choose a place with a complete address and valid map coordinates."); return;
            }
            select.current({ name: place.displayName ?? null, placeId: place.id, address: place.formattedAddress, latitude, longitude });
            setMapRevision(n => n + 1);
            setError("");
          } catch { if (!disposed && epoch === selectionEpoch.current) setError("Unable to retrieve this workplace. Select it again."); }
        });
        container.replaceChildren(widget);
      } catch { if (!disposed) setError("Google Places could not be initialized."); }
    }
    void initialize();
    return () => { disposed = true; container.replaceChildren(); };
  }, [ready]);
  useEffect(() => {
    if (!ready || !value || !mapHost.current) return;
    let disposed = false;
    type LatLng = { lat(): number; lng(): number };
    type Maps = { google: { maps: { importLibrary(name: string): Promise<unknown> } } };
    type MapInstance = { addListener(event: string, callback: (event: { latLng?: LatLng }) => void): { remove(): void } };
    type Marker = { position: { lat: number; lng: number } | LatLng; map: MapInstance | null; addEventListener(event: string, callback: () => void): void; removeEventListener(event: string, callback: () => void): void };
    let marker: Marker | undefined;
    let listeners: { remove(): void }[] = [];
    let selectionVersion = 0;
    async function initializeMap() {
      try {
        const sdk = (window as unknown as Maps).google.maps;
        const [maps, markers, geocoding] = await Promise.all([sdk.importLibrary("maps"), sdk.importLibrary("marker"), sdk.importLibrary("geocoding")]);
        if (disposed || !mapHost.current || !value) return;
        const Map = (maps as { Map: new (host: HTMLElement, options: object) => MapInstance }).Map;
        const AdvancedMarker = (markers as { AdvancedMarkerElement: new (options: object) => Marker }).AdvancedMarkerElement;
        const geocoder = new (geocoding as { Geocoder: new () => { geocode(options: object): Promise<{ results: { formatted_address: string; place_id?: string }[] }> } }).Geocoder();
        const map = new Map(mapHost.current, { center: { lat: value.latitude, lng: value.longitude }, zoom: 17, mapId: mapKey });
        marker = new AdvancedMarker({ map, position: { lat: value.latitude, lng: value.longitude }, gmpDraggable: true, title: "Drag to your actual workplace entrance" });
        async function choosePin(latitude: number, longitude: number) {
          const version = ++selectionVersion;
          const epoch = ++selectionEpoch.current;
          // Pin evidence must not retain a different company's Places ID or old coordinates.
          select.current({ name: value?.name, placeId: null, address: "", latitude, longitude });
          setError("Resolving the selected pin address. Do not submit until an address is available.");
          try {
            const response = await geocoder.geocode({ location: { lat: latitude, lng: longitude } });
            if (disposed || version !== selectionVersion || epoch !== selectionEpoch.current) return;
            const address = response.results[0];
            if (!address?.formatted_address) { setError("No address was found for this pin. Search for the workplace again."); return; }
            select.current({ name: value?.name, placeId: address.place_id ?? null, address: address.formatted_address, latitude, longitude }); setError("");
          } catch { if (!disposed && version === selectionVersion && epoch === selectionEpoch.current) setError("The pin address could not be retrieved. Search for the workplace again."); }
        }
        listeners = [map.addListener("click", event => { if (event.latLng) { const latitude = event.latLng.lat(), longitude = event.latLng.lng(); if (marker) marker.position = { lat: latitude, lng: longitude }; void choosePin(latitude, longitude); } })];
        const onDrag = () => { if (!marker) return; const p = marker.position; void choosePin(typeof p.lat === "function" ? p.lat() : p.lat, typeof p.lng === "function" ? p.lng() : p.lng); };
        marker.addEventListener("gmp-dragend", onDrag);
        listeners.push({ remove: () => marker?.removeEventListener("gmp-dragend", onDrag) });
      } catch { if (!disposed) setError("Google Maps could not be loaded. Check the Maps API configuration."); }
    }
    void initializeMap();
    return () => { disposed = true; selectionVersion++; listeners.forEach(listener => listener.remove()); if (marker) marker.map = null; };
  }, [ready, Boolean(value), mapRevision, mapKey]);
  return <div className="sm:col-span-2"><p className="mb-2 text-sm font-semibold">Registered workplace location</p>
    {key ? <><Script id="google-workplace-places" src={`https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}&v=weekly&libraries=places`} strategy="afterInteractive" onReady={() => setReady(true)} onError={() => setError("Google Places could not be loaded.")} /><div ref={host} /></> : <p className="text-sm text-amber-800">Google Places is not configured. Your administrator must configure the Maps API key before workplaces can be selected.</p>}
    {key && value && <><div ref={mapHost} aria-label="Workplace branch map" className="mt-3 h-64 w-full rounded-lg border border-slate-200" /><p className="mt-2 text-xs text-slate-500">Select the actual branch. Click the map or drag the pin to adjust the workplace entrance.</p></>}
    {value && <div className="mt-3 rounded-lg bg-slate-50 p-3 text-sm"><p>{value.address}</p><p className="mt-1 text-slate-600">{value.latitude}, {value.longitude}</p><a className="mt-2 inline-block font-semibold text-blue-700" href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${value.latitude},${value.longitude}`)}${value.placeId ? `&query_place_id=${encodeURIComponent(value.placeId)}` : ""}`} target="_blank" rel="noopener noreferrer">View selected workplace</a></div>}
    <p className="mt-2 text-xs text-slate-500">Used separately for daily attendance and monthly photo GPS comparison after approval by your assigned mentor and III Cell. Editing the company address clears an unsaved selection.</p>
    {error && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}
  </div>;
}
