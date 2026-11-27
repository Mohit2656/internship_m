"use client";

import { FormEvent, useEffect, useState } from "react";
import { notifyStudentCompletionChanged } from "@/components/StudentCompletionGate";
import { api } from "@/lib/api";
import { resumeError, SkillProfile } from "@/lib/professional-profile";

export function SkillProfileCard({ refreshKey, onSkillsUpdated }: { refreshKey: number; onSkillsUpdated?: () => void }) {
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
    try { const { data } = await request(); setProfile(data); setNotice(message); onSkillsUpdated?.(); notifyStudentCompletionChanged(); return true; }
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

  return <section id="skills" className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
    <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Skills</p>
    <h2 className="mt-2 text-xl font-bold">Student-controlled skill profile</h2>
    <p className="mt-2 text-sm text-slate-600">Recognized resume skills are synchronized automatically. You can also add and manage skills yourself.</p>
    {(error || notice) && <p role={error ? "alert" : "status"} className={`mt-5 rounded-xl px-4 py-3 text-sm ${error ? "bg-red-50 text-red-700" : "bg-emerald-50 text-emerald-800"}`}>{error || notice}</p>}

    <div className="mt-8"><h3 className="text-sm font-bold uppercase tracking-wide text-slate-500">Skills</h3>{busy === "load" ? <p className="mt-3 text-sm text-slate-500">Loading skills…</p> : profile.skills.length === 0 ? <p className="mt-3 text-sm text-slate-500">No skills added yet.</p> : <div className="mt-3 grid gap-3 sm:grid-cols-2">{profile.skills.map(skill => <div key={skill.id} className="rounded-xl border border-slate-200 p-4">{editingId === skill.id ? <form onSubmit={rename} className="flex gap-2"><input aria-label="Edit skill name" required maxLength={100} value={editingName} onChange={event => setEditingName(event.target.value)} className="input min-w-0 flex-1" /><button disabled={!!busy} className="text-xs">Save</button><button data-button-variant="text" type="button" onClick={() => setEditingId(null)} className="text-xs">Cancel</button></form> : <><div className="flex items-start justify-between gap-3"><div className="min-w-0"><p className="font-semibold text-slate-900">{skill.displayName}</p><span className={`mt-2 inline-block whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ${skill.source === "AI_EXTRACTED" ? "bg-violet-50 text-violet-700" : "bg-emerald-50 text-emerald-700"}`}>{skill.source === "AI_EXTRACTED" ? "Resume Extracted" : "Student Added"}</span></div><div className="flex shrink-0 gap-2"><button data-button-variant="text" disabled={!!busy} onClick={() => { setEditingId(skill.id); setEditingName(skill.displayName); }} className="text-xs">Edit</button><button data-button-variant="text" disabled={!!busy} onClick={() => run(`remove-${skill.id}`, () => api.delete<SkillProfile>(`/student/professional-profile/skills/${skill.id}`), `${skill.displayName} removed.`)} className="text-xs">Remove</button></div></div></>}</div>)}</div>}</div>

    <form onSubmit={add} className="mt-8"><label className="block text-sm font-semibold">Add Skill<span className="mt-1 block text-xs font-normal text-slate-500">Add a professional skill that is missing from your resume.</span><div className="mt-2 flex gap-3"><input required maxLength={100} value={name} onChange={event => setName(event.target.value)} placeholder="Docker" className="input min-w-0 flex-1" /><button disabled={!!busy} className="text-sm">{busy === "add" ? "Adding…" : "Add Skill"}</button></div></label></form>
  </section>;
}
