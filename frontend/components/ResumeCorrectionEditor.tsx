"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/monthly-progress";
import { ResumeCorrections, ResumeCorrectionValues, resumeError } from "@/lib/professional-profile";

const sourceLabel = (source: string) => source === "IIIC_REVIEWED" ? "IIIC-reviewed information" : source === "STUDENT_CORRECTED" ? "Student-corrected information" : "AI-extracted information";
const copyValues = (values: ResumeCorrectionValues): ResumeCorrectionValues => ({
  skills: [...values.skills], certifications: [...values.certifications],
  experience: values.experience.map(value => ({ ...value })), education: values.education.map(value => ({ ...value })),
});

function Snapshot({ values }: { values: ResumeCorrectionValues }) {
  return <div className="mt-4 grid gap-5 text-sm sm:grid-cols-2">
    <div><h3 className="font-semibold">Skills</h3><p className="mt-1 whitespace-pre-wrap text-slate-600">{values.skills.join(", ") || "None recorded"}</p></div>
    <div><h3 className="font-semibold">Certifications</h3><p className="mt-1 whitespace-pre-wrap text-slate-600">{values.certifications.join(", ") || "None recorded"}</p></div>
    <div><h3 className="font-semibold">Experience</h3>{values.experience.length ? <ul className="mt-1 space-y-2 text-slate-600">{values.experience.map((value, index) => <li key={index}><p>{[value.title, value.company].filter(Boolean).join(" at ") || "Details not recorded"}</p>{(value.startDate || value.endDate) && <p className="text-xs">{[value.startDate, value.endDate].filter(Boolean).join(" – ")}</p>}</li>)}</ul> : <p className="mt-1 text-slate-600">None recorded</p>}</div>
    <div><h3 className="font-semibold">Education</h3>{values.education.length ? <ul className="mt-1 space-y-2 text-slate-600">{values.education.map((value, index) => <li key={index}><p>{[value.degree, value.field].filter(Boolean).join(" in ")}</p><p>{value.institution || "Institution not recorded"}</p></li>)}</ul> : <p className="mt-1 text-slate-600">None recorded</p>}</div>
  </div>;
}

export function ResumeCorrectionEditor({ endpoint, resumeId, onSaved, readOnly = false }: { endpoint: string; resumeId: number; onSaved?: () => void; readOnly?: boolean }) {
  const [saved, setSaved] = useState<ResumeCorrections | null>(null);
  const [draft, setDraft] = useState<ResumeCorrectionValues | null>(null);
  const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [refresh, setRefresh] = useState(0);
  const generation = useRef(0);

  useEffect(() => {
    const abort = new AbortController();
    generation.current += 1;
    setLoading(true); setSaving(false); setSaved(null); setDraft(null); setEditing(false); setError(""); setNotice("");
    api.get<ResumeCorrections>(endpoint, { signal: abort.signal })
      .then(({ data }) => { if (!abort.signal.aborted && data.resumeId === resumeId) { setSaved(data); setDraft(copyValues(data.values)); } })
      .catch(cause => { if (!abort.signal.aborted) setError(resumeError(cause, "Parsed resume information could not be loaded.")); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => { abort.abort(); generation.current += 1; };
  }, [endpoint, resumeId, refresh]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!saved || !draft || saving || readOnly) return;
    const activeGeneration = generation.current;
    setError(""); setNotice("");
    const values = copyValues(draft);
    values.skills = values.skills.map(value => value.trim());
    values.certifications = values.certifications.map(value => value.trim());
    if ([...values.skills, ...values.certifications].some(value => !value)) { setError("Complete or remove empty skills and certifications before saving."); return; }
    if (values.experience.some(value => !value.title?.trim() && !value.company?.trim()) || values.education.some(value => Object.values(value).every(field => !field?.trim()))) { setError("Complete or remove empty experience and education entries before saving."); return; }
    setSaving(true);
    try {
      const { data } = await api.put<ResumeCorrections>(endpoint, { resumeId: saved.resumeId, revision: saved.revision, values });
      if (generation.current !== activeGeneration) return;
      setSaved(data); setDraft(copyValues(data.values)); setEditing(false); setNotice("Resume corrections saved."); onSaved?.();
    } catch (cause) { if (generation.current === activeGeneration) setError(resumeError(cause, "Resume corrections could not be saved. Your edits are still available.")); }
    finally { if (generation.current === activeGeneration) setSaving(false); }
  }

  function strings(key: "skills" | "certifications", label: string) {
    return <fieldset className="space-y-3"><legend className="font-bold">{label}</legend>{draft![key].map((value, index) => <div key={index} className="flex items-center gap-3"><label className="min-w-0 flex-1"><span className="sr-only">{label} entry {index + 1}</span><input className="input" required maxLength={key === "skills" ? 200 : 400} value={value} onChange={event => setDraft(current => current && ({ ...current, [key]: current[key].map((item, position) => position === index ? event.target.value : item) }))} /></label><button data-button-variant="outline" type="button" aria-label={`Remove ${label.toLowerCase()} entry ${index + 1}`} onClick={() => setDraft(current => current && ({ ...current, [key]: current[key].filter((_, position) => position !== index) }))} className="text-xs">Remove</button></div>)}<button type="button" data-button-variant="outline" disabled={draft![key].length >= (key === "skills" ? 200 : 100)} onClick={() => setDraft(current => current && ({ ...current, [key]: [...current[key], ""] }))} className="text-xs">Add {label === "Skills" ? "skill" : "certification"}</button></fieldset>;
  }

  return <section id="resume-analysis" className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8" aria-label="Editable parsed resume information">
    <div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Resume review</p><h2 className="mt-2 text-xl font-bold">Parsed resume information</h2><p className="mt-2 text-sm text-slate-600">{readOnly ? "Review the corrected fields and preserved original AI values." : "Correct the extracted fields while keeping the uploaded resume and original AI values."}</p></div>{saved && !editing && !readOnly && <button type="button" data-button-variant="outline" onClick={() => { setDraft(copyValues(saved.values)); setEditing(true); setNotice(""); setError(""); }} className="text-sm">Edit parsed information</button>}</div>
    {error && <p role="alert" className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}{notice && <p role="status" className="mt-4 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{notice}</p>}
    {loading ? <p role="status" className="mt-5 text-sm text-slate-500">Loading parsed information…</p> : !saved ? <button type="button" data-button-variant="outline" onClick={() => setRefresh(value => value + 1)} className="mt-4 text-sm">Reload parsed information</button> : <>
      <p className="mt-5 text-sm font-semibold text-blue-800">{sourceLabel(saved.source)} · Revision {saved.revision}</p><p className="mt-1 text-xs text-slate-500">{saved.editedAt ? `Last edited ${formatDateTime(saved.editedAt)} by ${saved.editorName || "Authorized reviewer"}` : "No manual corrections yet"}</p>
      {editing && draft && !readOnly ? <form onSubmit={save} className="mt-6 space-y-7"><fieldset disabled={saving} className="space-y-7">{strings("skills", "Skills")}{strings("certifications", "Certifications")}
        <fieldset className="space-y-3"><legend className="font-bold">Experience</legend>{draft.experience.map((value, index) => <div key={index} className="rounded-xl border border-slate-200 p-4"><div className="grid gap-3 sm:grid-cols-2">{([['title', 'Designation / role'], ['company', 'Company'], ['startDate', 'Start date'], ['endDate', 'End date']] as const).map(([key, label]) => <label key={key} className="text-sm font-semibold">{label}<input aria-label={`Experience ${index + 1} ${label.toLowerCase()}`} className="input mt-1" maxLength={key.endsWith("Date") ? 100 : 300} placeholder={key.endsWith("Date") ? "As written in the resume" : undefined} value={value[key] ?? ""} onChange={event => setDraft(current => current && ({ ...current, experience: current.experience.map((item, position) => position === index ? { ...item, [key]: event.target.value } : item) }))} /></label>)}</div><button data-button-variant="outline" type="button" onClick={() => setDraft(current => current && ({ ...current, experience: current.experience.filter((_, position) => position !== index) }))} className="mt-3 text-xs">Remove experience {index + 1}</button></div>)}<button type="button" data-button-variant="outline" disabled={draft.experience.length >= 100} onClick={() => setDraft(current => current && ({ ...current, experience: [...current.experience, { title: "", company: "", startDate: "", endDate: "" }] }))} className="text-xs">Add experience</button></fieldset>
        <fieldset className="space-y-3"><legend className="font-bold">Education</legend>{draft.education.map((value, index) => <div key={index} className="rounded-xl border border-slate-200 p-4"><div className="grid gap-3 sm:grid-cols-2">{([['degree', 'Degree'], ['field', 'Field of study'], ['institution', 'College / university']] as const).map(([key, label]) => <label key={key} className="text-sm font-semibold">{label}<input aria-label={`Education ${index + 1} ${label.toLowerCase()}`} className="input mt-1" maxLength={key === "institution" ? 400 : 300} value={value[key] ?? ""} onChange={event => setDraft(current => current && ({ ...current, education: current.education.map((item, position) => position === index ? { ...item, [key]: event.target.value } : item) }))} /></label>)}</div><button data-button-variant="outline" type="button" onClick={() => setDraft(current => current && ({ ...current, education: current.education.filter((_, position) => position !== index) }))} className="mt-3 text-xs">Remove education {index + 1}</button></div>)}<button type="button" data-button-variant="outline" disabled={draft.education.length >= 100} onClick={() => setDraft(current => current && ({ ...current, education: [...current.education, { degree: "", field: "", institution: "" }] }))} className="text-xs">Add education</button></fieldset>
        <div className="flex flex-wrap gap-3"><button type="submit" className="text-sm">{saving ? "Saving…" : "Save corrections"}</button><button data-button-variant="outline" type="button" onClick={() => { setEditing(false); setDraft(copyValues(saved.values)); setError(""); }} className="text-sm">Cancel</button><button data-button-variant="text" type="button" onClick={() => setRefresh(value => value + 1)} className="text-sm">Reload saved information</button></div>
      </fieldset></form> : <Snapshot values={saved.values} />}
      <details className="mt-6 border-t border-slate-100 pt-4"><summary className="cursor-pointer text-sm font-semibold text-blue-700">Original AI-extracted information</summary><p className="mt-2 text-xs text-slate-500">Extracted {formatDateTime(saved.original.completedAt)}. This snapshot is preserved.</p><Snapshot values={saved.original} /></details>
      <details className="mt-4 border-t border-slate-100 pt-4"><summary className="cursor-pointer text-sm font-semibold text-blue-700">Correction history ({saved.history.length})</summary>{saved.history.length ? <ol className="mt-4 space-y-4">{saved.history.map(entry => <li key={entry.revision} className="rounded-xl bg-slate-50 p-4"><p className="text-sm font-semibold">Revision {entry.revision} · {sourceLabel(entry.source)}</p><p className="mt-1 text-xs text-slate-500">{formatDateTime(entry.editedAt)} · {entry.editorName || "Authorized reviewer"}</p><Snapshot values={entry.values} /></li>)}</ol> : <p className="mt-3 text-sm text-slate-500">No corrections recorded.</p>}</details>
    </>}
  </section>;
}
