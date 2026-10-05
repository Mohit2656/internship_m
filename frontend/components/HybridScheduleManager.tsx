"use client";

import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { api, ApiError } from "@/lib/api";
import { HybridSchedule, HybridScheduleEntry, HybridWorkMode } from "@/lib/attendance";

const weekdays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];

export function HybridScheduleManager({ studentId }: { studentId: number }) {
  const [entries, setEntries] = useState<HybridScheduleEntry[]>(weekdays.map(dayOfWeek => ({ dayOfWeek, workMode: "OFFICE" })));
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    api.get<HybridSchedule>(`/admin/students/${studentId}/internship/hybrid-schedule`)
      .then(response => {
        const existing = new Map(response.data.entries.map(entry => [entry.dayOfWeek, entry.workMode]));
        setEntries(weekdays.map(dayOfWeek => ({ dayOfWeek, workMode: existing.get(dayOfWeek) ?? "OFFICE" })));
      })
      .catch(() => setError("Hybrid schedule could not be loaded."))
      .finally(() => setLoading(false));
  }, [studentId]);

  function change(dayOfWeek: string, workMode: HybridWorkMode) {
    setSaved(false);
    setEntries(current => current.map(entry => entry.dayOfWeek === dayOfWeek ? { ...entry, workMode } : entry));
  }

  async function save() {
    setSaving(true); setError(""); setSaved(false);
    try {
      const { data } = await api.put<HybridSchedule>(`/admin/students/${studentId}/internship/hybrid-schedule`, { entries });
      setEntries(data.entries); setSaved(true);
    } catch (cause) {
      setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Hybrid schedule could not be saved.");
    } finally { setSaving(false); }
  }

  return <section className="rounded-2xl border border-slate-200 bg-white p-7">
    <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Hybrid Weekly Schedule</p>
    <h2 className="mt-2 text-xl font-bold">Expected work mode</h2>
    <p className="mt-2 text-sm text-slate-600">This institution-controlled schedule determines which verification policy applies each day.</p>
    {loading ? <p className="mt-5 text-sm text-slate-500">Loading schedule…</p> : <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">{entries.map(entry => <label key={entry.dayOfWeek} className="text-sm font-semibold text-slate-700">{entry.dayOfWeek[0] + entry.dayOfWeek.slice(1).toLowerCase()}<select value={entry.workMode} onChange={event => change(entry.dayOfWeek, event.target.value as HybridWorkMode)} className="mt-2 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 font-normal"><option value="OFFICE">Office</option><option value="REMOTE">Remote</option></select></label>)}</div>}
    {!loading && <button type="button" disabled={saving} onClick={() => void save()} className="mt-5 rounded-xl bg-blue-700 px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-50">{saving ? "Saving…" : "Save Schedule"}</button>}
    {saved && <p className="mt-4 text-sm font-semibold text-emerald-700">Schedule saved.</p>}
    {error && <p role="alert" className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
  </section>;
}
