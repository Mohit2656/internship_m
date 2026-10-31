"use client";
import { FormEvent, useEffect, useRef, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { SessionDeletionDialog } from "@/components/AcademicSessionDeletionDialog";
import { api } from "@/lib/api";
import type { AcademicSession, SessionDeletionPreview } from "@/lib/academic-sessions";

export default function AcademicSessionsPage() {
  const [sessions, setSessions] = useState<AcademicSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [historical, setHistorical] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [form, setForm] = useState({ name: "", startDate: "", endDate: "", description: "", activate: true });
  const [deleting, setDeleting] = useState<AcademicSession | null>(null);
  const [preview, setPreview] = useState<SessionDeletionPreview | null>(null);
  const [deletionError, setDeletionError] = useState("");
  const deletionRequest = useRef<AbortController | null>(null);
  const mutation = useRef(false);
  useEffect(() => () => deletionRequest.current?.abort(), []);
  async function load() { const { data } = await api.get<AcademicSession[]>("/admin/academic-sessions"); setSessions(data); }
  useEffect(() => { let current = true; api.get<AcademicSession[]>("/admin/academic-sessions").then(r => { if (current) setSessions(r.data); }).catch(() => { if (current) setError("Academic sessions could not be loaded."); }).finally(() => { if (current) setLoading(false); }); return () => { current = false; }; }, []);
  async function create(event: FormEvent) {
    event.preventDefault(); if (mutation.current) return; mutation.current = true; setSaving(true); setError(""); setSuccess("");
    try { if (historical) await api.put(`/admin/academic-sessions/${historical}/dates`, form); else await api.post("/admin/academic-sessions", form); await load(); setOpen(false); setForm({ name: "", startDate: "", endDate: "", description: "", activate: true }); setSuccess(historical ? "Historical session dates recorded. Existing student assignments are unchanged." : "Academic session created. Existing student assignments are unchanged."); }
    catch (failure) { setError((failure as { response?: { data?: { message?: string } } }).response?.data?.message || "Session could not be created."); }
    finally { mutation.current = false; setSaving(false); }
  }
  async function changeSession(session: AcademicSession, action: "activate" | "close" | "reopen") {
    const message = action === "close" ? `Close ${session.name}? IIIC Coordinator confirmation is required. New students will no longer be assigned to this session. Existing students and all academic records will be preserved.${session.active ? " Activate another session before creating new student accounts." : " The current active session will remain unchanged."}` : `${action === "reopen" ? "Explicitly reopen and activate" : "Activate"} ${session.name}? The current active session will be closed. New students will use ${session.name}; existing students will keep their original sessions and records.`;
    if (mutation.current || !window.confirm(message)) return;
    mutation.current = true; setSaving(true); setError(""); setSuccess("");
    try { await api.post(`/admin/academic-sessions/${session.id}/${action}`, action === "activate" ? undefined : { confirmed: true, sessionName: session.name }); await load(); setSuccess(action === "close" ? `${session.name} is closed. Its students and historical records are preserved.` : `${session.name} is now active. The previous active session is closed.`); }
    catch (failure) { setError((failure as { response?: { data?: { message?: string } } }).response?.data?.message || "Session could not be updated. Refresh and try again."); }
    finally { mutation.current = false; setSaving(false); }
  }
  async function reviewDeletion(session: AcademicSession) {
    deletionRequest.current?.abort(); const controller = new AbortController(); deletionRequest.current = controller;
    setDeleting(session); setPreview(null); setDeletionError("");
    try { const { data } = await api.get<SessionDeletionPreview>(`/admin/academic-sessions/${session.id}/deletion-preview`, { signal: controller.signal }); if (!controller.signal.aborted) setPreview(data); }
    catch (failure) { if (!controller.signal.aborted) setDeletionError((failure as { response?: { data?: { message?: string } } }).response?.data?.message || "Associated records could not be checked. No session was deleted."); }
  }
  async function deleteSession() {
    if (mutation.current || !deleting || !preview?.canDelete) return;
    mutation.current = true; setSaving(true); setDeletionError(""); setSuccess("");
    try { await api.delete(`/admin/academic-sessions/${deleting.id}`, { data: { confirmed: true, sessionName: deleting.name } }); setDeleting(null); await load(); setSuccess(`${deleting.name} was permanently deleted. Its audit history is retained.`); }
    catch (failure) { setPreview(null); setDeletionError((failure as { response?: { data?: { message?: string } } }).response?.data?.message || "Session could not be deleted. Reload the dependency review before trying again."); }
    finally { mutation.current = false; setSaving(false); }
  }
  const active = sessions.find(s => s.active);
  return <ProtectedRoute role="ADMIN"><AppFrame><main className="mx-auto max-w-6xl px-5 py-10 sm:px-8"><p className="text-sm font-semibold text-blue-700">Settings</p><h1 className="mt-2 text-3xl font-bold">Academic Sessions</h1><p className="mt-3 text-slate-600">Manage the default session for new student accounts. Historical student assignments and records are preserved.</p>
    <section className="mt-6 rounded-2xl border border-blue-200 bg-white p-6"><h2 className="font-bold">Current active academic session</h2><p className="mt-2 text-xl font-semibold">{loading ? "Loading…" : active?.name ?? "No active session"}</p>{active && <p className="mt-1 text-sm text-slate-600">{active.startDate} to {active.endDate}</p>}<button className="mt-5 text-sm" disabled={saving} onClick={() => { setHistorical(null); setForm({ name: "", startDate: "", endDate: "", description: "", activate: true }); setOpen(true); }}>Start New Academic Session</button></section>
    {error && <p role="alert" className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}{success && <p role="status" className="mt-4 rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800">{success}</p>}
    {open && <form onSubmit={create} className="mt-6 space-y-4 rounded-2xl border border-slate-200 bg-white p-6"><h2 className="text-lg font-bold">{historical ? "Record Historical Session Dates" : "Start New Academic Session"}</h2><fieldset disabled={saving} className="grid gap-4 sm:grid-cols-2"><label className="text-sm font-semibold">Session name<input disabled={historical !== null} required maxLength={60} placeholder="2027–28" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label><div className="grid grid-cols-2 gap-3"><label className="text-sm font-semibold">Start date<input type="date" required value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label><label className="text-sm font-semibold">End date<input type="date" required min={form.startDate || undefined} value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label></div><label className="text-sm font-semibold sm:col-span-2">Description (optional)<textarea maxLength={1000} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} className="mt-2 w-full rounded-xl border border-slate-300 p-3" /></label><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.activate} onChange={e => setForm({ ...form, activate: e.target.checked })} />Activate this session</label></fieldset><p className="text-xs text-slate-500">Dated sessions cannot overlap. Session names such as 2027–28 and 2027-2028 refer to the same session.</p><div className="flex gap-3"><button disabled={saving || !form.startDate || form.endDate <= form.startDate}>{saving ? "Saving…" : "Create Session"}</button><button type="button" data-button-variant="outline" disabled={saving} onClick={() => setOpen(false)}>Cancel</button></div></form>}
    <section className="mt-6 overflow-x-auto rounded-2xl border border-slate-200 bg-white p-5"><h2 className="mb-4 text-lg font-bold">Existing and historical sessions</h2><table className="w-full text-left text-sm"><thead><tr className="border-b"><th className="p-3">Session</th><th className="p-3">Start Date</th><th className="p-3">End Date</th><th className="p-3">Students</th><th className="p-3">Status</th><th className="p-3">Actions</th></tr></thead><tbody>{sessions.map(s => <tr key={s.id} className="border-b border-slate-100 align-top"><td className="p-3 font-semibold">{s.name}<p className="mt-1 max-w-md text-xs font-normal text-slate-500">{s.description}</p></td><td className="whitespace-nowrap p-3">{s.startDate ?? "Not recorded"}</td><td className="whitespace-nowrap p-3">{s.endDate ?? "Not recorded"}</td><td className="p-3">{s.studentCount}</td><td className="p-3"><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${s.active ? "bg-emerald-50 text-emerald-800" : s.status === "CLOSED" ? "bg-slate-100 text-slate-700" : "bg-amber-50 text-amber-800"}`}>{s.active ? "ACTIVE" : s.status === "CLOSED" ? "CLOSED" : "INACTIVE"}</span>{s.closedAt && <p className="mt-2 text-xs text-slate-500">Closed {new Date(s.closedAt).toLocaleString()} by {s.closedBy ?? "IIIC"}</p>}</td><td className="min-w-56 p-3"><div className="flex flex-wrap items-center gap-2">
      {!s.startDate && <button data-button-variant="outline" className="text-xs" disabled={saving} onClick={() => { setHistorical(s.id); setForm({ name: s.name, startDate: "", endDate: "", description: s.description ?? "", activate: s.status !== "CLOSED" }); setOpen(true); }}>Set Dates</button>}
      {s.startDate && !s.active && <button data-button-variant="outline" className="text-xs" disabled={saving} onClick={() => void changeSession(s, s.status === "CLOSED" ? "reopen" : "activate")}>{s.status === "CLOSED" ? "Reopen and Activate" : "Activate"}</button>}
      {s.status !== "CLOSED" && <button data-button-variant="outline" className="text-xs" disabled={saving} onClick={() => void changeSession(s, "close")}>Close Session</button>}
      <button data-button-variant="outline" className="text-xs text-red-700" disabled={saving || s.active} title={s.active ? "Close the active session before considering deletion" : "Review associated records before permanent deletion"} onClick={() => void reviewDeletion(s)}>Delete Session</button>
      <a href={`/admin/students?academicSessionId=${s.id}`} className="text-xs font-semibold text-blue-700">View</a>
    </div>{s.active && <p className="mt-2 text-xs text-slate-500">Close this session before deletion.</p>}</td></tr>)}{!loading && !sessions.length && <tr><td colSpan={6} className="p-6 text-slate-500">No academic sessions yet. Create and activate the first session before importing students.</td></tr>}</tbody></table></section>
    {deleting && <SessionDeletionDialog key={deleting.id} session={deleting} preview={preview} busy={saving} error={deletionError} onClose={() => { if (!saving) { deletionRequest.current?.abort(); setDeleting(null); } }} onConfirm={() => void deleteSession()} />}
  </main></AppFrame></ProtectedRoute>;
}
