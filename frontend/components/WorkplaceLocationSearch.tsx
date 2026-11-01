"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { validWorkplaceCoordinates } from "@/lib/workplace-location";

export type WorkplaceMatchQuality = "EXACT_BUSINESS" | "APPROXIMATE_ADDRESS" | "STREET_OR_POSTCODE" | "LOCALITY_OR_CITY";
export type WorkplaceSearchResult = { name: string | null; address: string; latitude: number; longitude: number; matchQuality?: WorkplaceMatchQuality };

const qualityLabels: Record<WorkplaceMatchQuality, { label: string; detail: string; tone: string }> = {
  EXACT_BUSINESS: { label: "Provider-listed business", detail: "Not IMS verified. Confirm the real office entrance on the map.", tone: "text-blue-700" },
  APPROXIMATE_ADDRESS: { label: "Approximate address", detail: "Check the address and place the pin at the real entrance.", tone: "text-amber-700" },
  STREET_OR_POSTCODE: { label: "Street or postcode match", detail: "Low precision. Use this only to navigate closer to the office.", tone: "text-amber-700" },
  LOCALITY_OR_CITY: { label: "City or locality match", detail: "Low precision. This is not a workplace location.", tone: "text-red-700" },
};

function validResult(value: unknown): value is WorkplaceSearchResult {
  if (!value || typeof value !== "object") return false;
  const result = value as WorkplaceSearchResult;
  return validWorkplaceCoordinates(result) && typeof result.address === "string"
    && result.address.trim().length > 0 && result.address.length <= 1000
    && (result.name === null || (typeof result.name === "string" && result.name.length <= 200))
    && (result.matchQuality === undefined || Object.hasOwn(qualityLabels, result.matchQuality));
}

export function WorkplaceLocationSearch({ disabled = false, focus, onSelect }: { disabled?: boolean; focus?: { latitude: number; longitude: number } | null; onSelect: (result: WorkplaceSearchResult) => void }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<WorkplaceSearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState("");
  const request = useRef<AbortController | null>(null);

  function cancel() { request.current?.abort(); request.current = null; }
  useEffect(() => () => cancel(), []);
  useEffect(() => {
    if (disabled) { cancel(); setSearching(false); setResults([]); setSearched(false); setError(""); }
  }, [disabled]);

  async function search() {
    if (disabled || searching) return;
    const term = query.trim();
    if (term.length < 3 || term.length > 200) { setError("Enter between 3 and 200 characters to search for a workplace."); return; }
    cancel();
    const controller = new AbortController(); request.current = controller;
    setSearching(true); setSearched(false); setResults([]); setError("");
    try {
      const params = validWorkplaceCoordinates(focus) ? { q: term, lat: focus.latitude, lon: focus.longitude } : { q: term };
      const { data } = await api.get<unknown>("/workplace-locations/search", { params, signal: controller.signal });
      if (controller.signal.aborted || request.current !== controller) return;
      if (!Array.isArray(data)) throw new Error("Invalid search response");
      setResults(data.filter(validResult).slice(0, 5)); setSearched(true);
    } catch (cause) {
      if (controller.signal.aborted || request.current !== controller) return;
      const response = (cause as { response?: { status?: number } } | null)?.response;
      setError(response?.status === 429 ? "Search is temporarily busy. Wait a moment and try again, or select your workplace manually on the map."
        : "Workplace search is temporarily unavailable. Retry, or select your workplace manually on the map.");
    } finally {
      if (!controller.signal.aborted && request.current === controller) { setSearching(false); request.current = null; }
    }
  }

  function select(result: WorkplaceSearchResult) {
    if (disabled) return;
    cancel(); setSearching(false); setResults([]); setSearched(false); setError("");
    onSelect(result);
  }

  return <div className="space-y-2">
    <label className="block text-sm font-semibold">Search for a workplace
      <input type="search" className="input mt-1" value={query} maxLength={200} disabled={disabled} placeholder="Company name, street, or city" onChange={event => {
        cancel(); setQuery(event.target.value); setSearching(false); setResults([]); setSearched(false); setError("");
      }} onKeyDown={event => {
        if (event.key === "Enter" && !event.nativeEvent.isComposing) { event.preventDefault(); event.stopPropagation(); void search(); }
      }} />
    </label>
    <button type="button" disabled={disabled || searching || query.trim().length < 3} onClick={() => void search()} data-button-variant="outline" className="text-sm">{searching ? "Searching…" : "Search"}</button>
    {searching && <p role="status" className="text-xs text-slate-600">Searching workplace addresses…</p>}
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    {searched && !results.length && <div role="status" className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900"><p className="font-semibold">No matching workplace was found.</p><p className="mt-1">Use the map below to locate the real office manually: zoom in, click the office entrance, and confirm the pin.</p></div>}
    {results.length > 0 && <><p role="status" className="rounded-lg bg-blue-50 p-3 text-xs text-blue-900"><strong>Search suggestions are navigation aids, not IMS-verified workplaces.</strong> Show a suggestion on the map, then click the real office entrance to place the pin.</p><ul aria-label="Workplace search results" className="divide-y divide-slate-200 overflow-hidden rounded-lg border border-slate-200">{results.map((result, index) => { const quality = qualityLabels[result.matchQuality ?? "APPROXIMATE_ADDRESS"]; return <li key={`${result.latitude}:${result.longitude}:${index}`}><button type="button" disabled={disabled} onClick={() => select(result)} data-button-variant="text" className="w-full px-3 py-2 text-left text-sm hover:bg-blue-50"><span className="block font-semibold">{result.name || result.address}</span>{result.name && <span className="mt-1 block text-xs text-slate-600">{result.address}</span>}<span className={`mt-1 block text-xs font-semibold ${quality.tone}`}>{quality.label}</span><span className="mt-0.5 block text-xs text-slate-600">{quality.detail}</span><span className="mt-1 block text-xs font-semibold text-blue-700">Show on map — does not select the workplace</span></button></li>; })}</ul></>}
    <p className="text-xs text-slate-500">Search: <a href="https://photon.komoot.io/" target="_blank" rel="noopener noreferrer" className="underline">Photon</a> · © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer" className="underline">OpenStreetMap contributors</a>.</p>
  </div>;
}
