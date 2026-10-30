"use client";
import { FormEvent, useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { AcademicSession } from "@/lib/academic-sessions";

export default function AcademicSessionsPage() {
  const [sessions, setSessions] = useState<AcademicSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [historical, setHistorical] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [form, setForm] = useState({ name: "", startDate: "", endDate: "", description: "", activate: true });
  async function load() { const { data } = await api.get<AcademicSession[]>("/admin/academic-sessions"); setSessions(data); }
  useEffect(() => { let current = true; api.get<AcademicSession[]>("/admin/academic-sessions").then(r => { if (current) setSessions(r.data); }).catch(() => { if (current) setError("Academic sessions could not be loaded."); }).finally(() => { if (current) setLoading(false); }); return () => { current = false; }; }, []);
  async function create(event: FormEvent) {
    event.preventDefault(); if (saving) return; setSaving(true); setError(""); setSuccess("");
    try { if (historical) await api.put(`/admin/academic-sessions/${historical}/dates`, form); else await api.post("/admin/academic-sessions", form); await load(); setOpen(false); setForm({ name: "", startDate: "", endDate: "", description: "", activate: true }); setSuccess("Academic session created. Existing student assignments are unchanged."); }
    catch (failure) { setError((failure as { response?: { data?: { message?: string } } }).response?.data?.message || "Session could not be created."); }
    finally { setSaving(false); }
  }
  async function activate(session: AcademicSession) {
    if (saving || !window.confirm(`Activate ${session.name}? New student accounts will use this session. Existing students will keep their original sessions.`)) return;
    setSaving(true); setError(""); setSuccess("");
    try { await api.post(`/admin/academic-sessions/${session.id}/activate`); await load(); setSuccess(`${session.name} is now active and is the dashboard default for new visits.`); }
    catch (failure) { setError((failure as { response?: { data?: { message?: string } } }).response?.data?.message || "Session could not be activated."); }
    finally { setSaving(false); }
  }
  const active = sessions.find(s => s.active);
  return <ProtectedRoute role="ADMIN"><AppFrame><main className="mx-auto max-w-6xl px-5 py-10 sm:px-8"><p className="text-sm font-semibold text-blue-700">Settings</p><h1 className="mt-2 text-3xl font-bold">Academic Sessions</h1><p className="mt-3 text-slate-600">Manage the default session for new student accounts. Historical student assignments and records are preserved.</p>
    <section className="mt-6 rounded-2xl border border-blue-200 bg-white p-6"><h2 className="font-bold">Current active academic session</h2><p className="mt-2 text-xl font-semibold">{loading ? "Loading…" : active?.name ?? "No active session"}</p>{active && <p className="mt-1 text-sm text-slate-600">{active.startDate} to {active.endDate}</p>}<button className="mt-5 text-sm" disabled={saving} onClick={() => { setHistorical(null); setForm({ name: "", startDate: "", endDate: "", description: "", activate: true }); setOpen(true); }}>Start New Academic Session</button></section>
    {error && <p role="alert" className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}{success && <p role="status" className="mt-4 rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800">{success}</p>}
    {open && <form onSubmit={create} className="mt-6 space-y-4 rounded-2xl border border-slate-200 bg-white p-6"><h2 className="text-lg font-bold">{historical ? "Record Historical Session Dates" : "Start New Academic Session"}</h2><fieldset disabled={saving} className="grid gap-4 sm:grid-cols-2"><label className="text-sm font-semibold">Session name<input disabled={historical !== null} required maxLength={60} placeholder="2027–28" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label><div className="grid grid-cols-2 gap-3"><label className="text-sm font-semibold">Start date<input type="date" required value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label><label className="text-sm font-semibold">End date<input type="date" required min={form.startDate || undefined} value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label></div><label className="text-sm font-semibold sm:col-span-2">Description (optional)<textarea maxLength={1000} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.activate} onChange={e => setForm({ ...form, activate: e.target.checked })} />Activate this session</label></fieldset><p className="text-xs text-slate-500">Dated sessions cannot overlap. Session names such as 2027–28 and 2027-2028 refer to the same session.</p><div className="flex gap-3"><button disabled={saving || !form.startDate || form.endDate <= form.startDate}>{saving ? "Saving…" : "Create Session"}</button><button type="button" data-button-variant="outline" disabled={saving} onClick={() => setOpen(false)}>Cancel</button></div></form>}
    <section className="mt-6 overflow-x-auto rounded-2xl border border-slate-200 bg-white p-5"><h2 className="mb-4 text-lg font-bold">Existing sessions</h2><table className="w-full text-left text-sm"><thead><tr className="border-b"><th className="p-3">Session</th><th className="p-3">Dates</th><th className="p-3">Status</th><th className="p-3">Students</th><th className="p-3">Action</th></tr></thead><tbody>{sessions.map(s => <tr key={s.id} className="border-b border-slate-100"><td className="p-3 font-semibold">{s.name}<p className="mt-1 max-w-md text-xs font-normal text-slate-500">{s.description}</p></td><td className="p-3">{s.startDate ? `${s.startDate} to ${s.endDate}` : "Historical dates not recorded"}</td><td className="p-3">{s.active ? "Active" : "Inactive / historical"}</td><td className="p-3">{s.studentCount}</td><td className="p-3">{!s.startDate ? <button data-button-variant="outline" className="text-xs" disabled={saving} onClick={() => { setHistorical(s.id); setForm({ name: s.name, startDate: "", endDate: "", description: s.description ?? "", activate: true }); setOpen(true); }}>Set Dates</button> : !s.active && <button data-button-variant="outline" className="text-xs" disabled={saving} onClick={() => activate(s)}>Activate</button>}</td></tr>)}{!loading && !sessions.length && <tr><td colSpan={5} className="p-6 text-slate-500">No academic sessions yet. Create and activate the first session before importing students.</td></tr>}</tbody></table></section>
  </main></AppFrame></ProtectedRoute>;
}
