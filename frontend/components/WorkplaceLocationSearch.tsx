"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { validWorkplaceCoordinates } from "@/lib/workplace-location";

export type WorkplaceSearchResult = { name: string | null; address: string; latitude: number; longitude: number };

function validResult(value: unknown): value is WorkplaceSearchResult {
  if (!value || typeof value !== "object") return false;
  const result = value as WorkplaceSearchResult;
  return validWorkplaceCoordinates(result) && typeof result.address === "string"
    && result.address.trim().length > 0 && result.address.length <= 1000
    && (result.name === null || (typeof result.name === "string" && result.name.length <= 200));
}

export function WorkplaceLocationSearch({ disabled = false, onSelect }: { disabled?: boolean; onSelect: (result: WorkplaceSearchResult) => void }) {
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
      const { data } = await api.get<unknown>("/workplace-locations/search", { params: { q: term }, signal: controller.signal });
      if (controller.signal.aborted || request.current !== controller) return;
      if (!Array.isArray(data)) throw new Error("Invalid search response");
      setResults(data.filter(validResult).slice(0, 5)); setSearched(true);
    } catch (cause) {
      if (controller.signal.aborted || request.current !== controller) return;
      const response = (cause as { response?: { status?: number } } | null)?.response;
      setError(response?.status === 429 ? "Search is busy. Wait a moment and try again."
        : "Workplace search is unavailable. Retry, or select your workplace on the map.");
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
    {searched && !results.length && <p role="status" className="text-sm text-slate-600">No matching workplaces found. Try a fuller address, or select the location on the map.</p>}
    {results.length > 0 && <><p role="status" className="text-xs text-slate-600">Choose a result, adjust the pin to the actual entrance, then confirm the workplace location.</p><ul aria-label="Workplace search results" className="divide-y divide-slate-200 overflow-hidden rounded-lg border border-slate-200">{results.map((result, index) => <li key={`${result.latitude}:${result.longitude}:${index}`}><button type="button" disabled={disabled} onClick={() => select(result)} data-button-variant="text" className="w-full px-3 py-2 text-left text-sm hover:bg-blue-50"><span className="block font-semibold">{result.name || result.address}</span>{result.name && <span className="mt-1 block text-xs text-slate-600">{result.address}</span>}</button></li>)}</ul></>}
    <p className="text-xs text-slate-500">Search: <a href="https://photon.komoot.io/" target="_blank" rel="noopener noreferrer" className="underline">Photon</a> · © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer" className="underline">OpenStreetMap contributors</a>.</p>
  </div>;
}
