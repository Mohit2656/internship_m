"use client";

import { FormEvent, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { resumeError, SkillProfile } from "@/lib/professional-profile";

export function SkillProfileCard({ refreshKey }: { refreshKey: number }) {
  const [profile, setProfile] = useState<SkillProfile>({ skills: [], suggestions: [] });
  const [name, setName] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingName, setEditingName] = useState("");
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    setBusy("load");
    api.get<SkillProfile>("/student/professional-profile/skills").then(({ data }) => setProfile(data))
      .catch(cause => setError(resumeError(cause, "Your skills could not be loaded.")))
      .finally(() => setBusy(""));
  }, [refreshKey]);

  async function run(key: string, request: () => Promise<{ data: SkillProfile }>, message: string) {
    setBusy(key); setError(""); setNotice("");
    try { const { data } = await request(); setProfile(data); setNotice(message); return true; }
    catch (cause) { setError(resumeError(cause, "Your skill profile could not be updated.")); return false; }
    finally { setBusy(""); }
  }

  async function add(event: FormEvent) {
    event.preventDefault();
    if (await run("add", () => api.post<SkillProfile>("/student/professional-profile/skills", { name }), "Skill added.")) setName("");
  }

  async function rename(event: FormEvent) {
    event.preventDefault();
    if (editingId === null) return;
    if (await run(`edit-${editingId}`, () => api.patch<SkillProfile>(`/student/professional-profile/skills/${editingId}`, { name: editingName }), "Skill updated.")) setEditingId(null);
  }

  return <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
    <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Skills</p>
    <h2 className="mt-2 text-xl font-bold">Student-controlled skill profile</h2>
    <p className="mt-2 text-sm text-slate-600">Review AI suggestions or add skills yourself. Only confirmed skills become part of your professional profile.</p>
    {(error || notice) && <p role={error ? "alert" : "status"} className={`mt-5 rounded-xl px-4 py-3 text-sm ${error ? "bg-red-50 text-red-700" : "bg-emerald-50 text-emerald-800"}`}>{error || notice}</p>}

    <div className="mt-7">
      <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="text-sm font-bold uppercase tracking-wide text-slate-500">AI Suggested Skills</h3>{profile.suggestions.length > 0 && <button disabled={!!busy} onClick={() => run("confirm-all", () => api.post<SkillProfile>("/student/professional-profile/skill-suggestions/confirm-all"), "All suggestions confirmed.")} className="rounded-lg border border-blue-700 px-3 py-2 text-xs font-semibold text-blue-700 disabled:opacity-50">Confirm All</button>}</div>
      {busy === "load" ? <p className="mt-3 text-sm text-slate-500">Loading skills…</p> : profile.suggestions.length === 0 ? <p className="mt-3 text-sm text-slate-500">No pending AI skill suggestions.</p> : <div className="mt-3 divide-y divide-slate-100 rounded-xl border border-slate-200">{profile.suggestions.map(suggestion => <div key={suggestion.id} className="flex flex-wrap items-center justify-between gap-3 p-4"><span className="font-semibold text-slate-900">{suggestion.displayName}</span><div className="flex gap-2"><button disabled={!!busy} onClick={() => run(`confirm-${suggestion.id}`, () => api.post<SkillProfile>(`/student/professional-profile/skill-suggestions/${suggestion.id}/confirm`), `${suggestion.displayName} confirmed.`)} className="rounded-lg bg-blue-700 px-3 py-2 text-xs font-semibold text-white disabled:opacity-50">Confirm</button><button disabled={!!busy} onClick={() => run(`reject-${suggestion.id}`, () => api.post<SkillProfile>(`/student/professional-profile/skill-suggestions/${suggestion.id}/reject`), `${suggestion.displayName} removed from suggestions.`)} className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold disabled:opacity-50">Remove</button></div></div>)}</div>}
    </div>

    <div className="mt-8"><h3 className="text-sm font-bold uppercase tracking-wide text-slate-500">Confirmed Skills</h3>{profile.skills.length === 0 ? <p className="mt-3 text-sm text-slate-500">No confirmed skills yet.</p> : <div className="mt-3 grid gap-3 sm:grid-cols-2">{profile.skills.map(skill => <div key={skill.id} className="rounded-xl border border-slate-200 p-4">{editingId === skill.id ? <form onSubmit={rename} className="flex gap-2"><input aria-label="Edit skill name" required maxLength={100} value={editingName} onChange={event => setEditingName(event.target.value)} className="input min-w-0 flex-1" /><button disabled={!!busy} className="rounded-lg bg-blue-700 px-3 text-xs font-semibold text-white">Save</button><button type="button" onClick={() => setEditingId(null)} className="text-xs font-semibold">Cancel</button></form> : <><div className="flex items-start justify-between gap-3"><div><p className="font-semibold text-slate-900">{skill.displayName}</p><span className={`mt-2 inline-block rounded-full px-2 py-1 text-xs font-semibold ${skill.source === "AI_EXTRACTED" ? "bg-violet-50 text-violet-700" : "bg-emerald-50 text-emerald-700"}`}>{skill.source === "AI_EXTRACTED" ? "AI Extracted" : "Student Added"}</span></div><div className="flex gap-2"><button disabled={!!busy} onClick={() => { setEditingId(skill.id); setEditingName(skill.displayName); }} className="text-xs font-semibold text-blue-700">Edit</button><button disabled={!!busy} onClick={() => run(`remove-${skill.id}`, () => api.delete<SkillProfile>(`/student/professional-profile/skills/${skill.id}`), `${skill.displayName} removed.`)} className="text-xs font-semibold text-red-700">Remove</button></div></div></>}</div>)}</div>}</div>

    <form onSubmit={add} className="mt-8"><label className="block text-sm font-semibold">Add Skill<span className="mt-1 block text-xs font-normal text-slate-500">Add a skill that is missing from your resume analysis.</span><div className="mt-2 flex gap-3"><input required maxLength={100} value={name} onChange={event => setName(event.target.value)} placeholder="Docker" className="input min-w-0 flex-1" /><button disabled={!!busy} className="rounded-xl bg-blue-700 px-5 text-sm font-semibold text-white disabled:opacity-50">{busy === "add" ? "Adding…" : "Add Skill"}</button></div></label></form>
  </section>;
}
