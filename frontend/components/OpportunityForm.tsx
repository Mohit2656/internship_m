"use client";

import { FormEvent, useState } from "react";
import { Branch, branchLabels, OpportunityInput, OpportunityStatus } from "@/lib/opportunities";

type Props = {
  initial: OpportunityInput;
  status?: OpportunityStatus;
  saving: boolean;
  error: string;
  onCancel: () => void;
  onSave: (input: OpportunityInput, publish: boolean) => Promise<void>;
};

const branches = Object.entries(branchLabels) as Array<[Branch, string]>;
const inputClass = "w-full rounded-xl border border-slate-300 px-4 py-3 outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100";

function SkillEditor({ label, values, onChange }: { label: string; values: string[]; onChange: (skills: string[]) => void }) {
  const [draft, setDraft] = useState("");
  function add() {
    const value = draft.trim(); if (!value) return;
    if (values.some(skill => skill.toLocaleLowerCase() === value.toLocaleLowerCase())) return;
    onChange([...values, value]); setDraft("");
  }
  return <div><span className="mb-2 block text-sm font-semibold">{label}</span>
    <div className="flex gap-2"><input value={draft} maxLength={100} onChange={event => setDraft(event.target.value)} onKeyDown={event => { if (event.key === "Enter") { event.preventDefault(); add(); } }} placeholder="Type a skill" className={inputClass} /><button data-button-variant="outline" type="button" onClick={add} className="text-sm">Add</button></div>
    {values.length > 0 && <div className="mt-3 flex flex-wrap gap-2">{values.map(skill => <span key={skill} className="inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1.5 text-sm font-medium text-blue-800">{skill}<button data-button-variant="text" type="button" aria-label={`Remove ${skill}`} onClick={() => onChange(values.filter(value => value !== skill))} className="">×</button></span>)}</div>}
  </div>;
}

export function OpportunityForm({ initial, status, saving, error, onCancel, onSave }: Props) {
  const [form, setForm] = useState<OpportunityInput>(initial);
  const update = <K extends keyof OpportunityInput>(field: K, value: OpportunityInput[K]) => setForm(current => ({ ...current, [field]: value }));
  const toggleBranch = (branch: Branch) => update("eligibleBranches", form.eligibleBranches.includes(branch) ? form.eligibleBranches.filter(value => value !== branch) : [...form.eligibleBranches, branch]);
  const toggleSemester = (semester: number) => update("eligibleSemesters", form.eligibleSemesters.includes(semester) ? form.eligibleSemesters.filter(value => value !== semester) : [...form.eligibleSemesters, semester].sort());
  async function submit(event: FormEvent) { event.preventDefault(); await onSave(form, false); }

  return <form onSubmit={event => void submit(event)} className="space-y-7">
    <section><h3 className="text-sm font-bold uppercase tracking-wide text-slate-500">Basic information</h3><div className="mt-4 grid gap-4 md:grid-cols-2">
      <label className="block"><span className="mb-1.5 block text-sm font-semibold">Company Name</span><input required maxLength={200} value={form.companyName} onChange={event => update("companyName", event.target.value)} className={inputClass} /></label>
      <label className="block"><span className="mb-1.5 block text-sm font-semibold">Opportunity Title</span><input required maxLength={200} value={form.title} onChange={event => update("title", event.target.value)} className={inputClass} /></label>
      <label className="block md:col-span-2"><span className="mb-1.5 block text-sm font-semibold">Company Website <span className="font-normal text-slate-400">(optional)</span></span><input type="url" maxLength={500} placeholder="https://company.example" value={form.companyWebsite} onChange={event => update("companyWebsite", event.target.value)} className={inputClass} /></label>
      <label className="block md:col-span-2"><span className="mb-1.5 block text-sm font-semibold">Description</span><textarea rows={7} maxLength={20000} value={form.description} onChange={event => update("description", event.target.value)} className={inputClass} placeholder="Responsibilities, requirements, technologies and eligibility" /></label>
    </div></section>

    <section><h3 className="text-sm font-bold uppercase tracking-wide text-slate-500">Internship details</h3><div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <label><span className="mb-1.5 block text-sm font-semibold">Mode</span><select value={form.internshipMode} onChange={event => update("internshipMode", event.target.value as OpportunityInput["internshipMode"])} className={inputClass}><option value="">Select mode</option><option value="ONSITE">Onsite</option><option value="HYBRID">Hybrid</option><option value="REMOTE">Remote</option></select></label>
      <label><span className="mb-1.5 block text-sm font-semibold">Location</span><input maxLength={200} value={form.location} onChange={event => update("location", event.target.value)} className={inputClass} /></label>
      <label><span className="mb-1.5 block text-sm font-semibold">Duration (months)</span><input type="number" min={1} max={60} value={form.durationMonths} onChange={event => update("durationMonths", event.target.value)} className={inputClass} /></label>
      <label><span className="mb-1.5 block text-sm font-semibold">Stipend amount</span><input required type="number" min={0} step="0.01" value={form.stipendAmount} onChange={event => update("stipendAmount", event.target.value)} className={inputClass} /></label>
      <label><span className="mb-1.5 block text-sm font-semibold">Currency</span><input required pattern="[A-Z]{3}" maxLength={3} value={form.currency} onChange={event => update("currency", event.target.value.toUpperCase())} className={inputClass} /></label>
      <label><span className="mb-1.5 block text-sm font-semibold">Application Deadline</span><input type="date" value={form.applicationDeadline} onChange={event => update("applicationDeadline", event.target.value)} className={inputClass} /></label>
    </div></section>

    <section><h3 className="text-sm font-bold uppercase tracking-wide text-slate-500">Eligibility</h3><div className="mt-4 space-y-5">
      <div><span className="mb-2 block text-sm font-semibold">Eligible Branches</span><div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{branches.map(([branch, label]) => <label key={branch} className="flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2.5 text-sm"><input type="checkbox" checked={form.eligibleBranches.includes(branch)} onChange={() => toggleBranch(branch)} />{label}</label>)}</div></div>
      <div><span className="mb-2 block text-sm font-semibold">Eligible Semesters</span><div className="flex flex-wrap gap-2">{[1,2,3,4,5,6,7,8].map(semester => <label key={semester} className="flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2 text-sm"><input type="checkbox" checked={form.eligibleSemesters.includes(semester)} onChange={() => toggleSemester(semester)} />{semester}</label>)}</div></div>
      <label className="block max-w-xs"><span className="mb-1.5 block text-sm font-semibold">Minimum CGPA <span className="font-normal text-slate-400">(optional)</span></span><input type="number" min={0} max={10} step="0.01" value={form.minimumCgpa} onChange={event => update("minimumCgpa", event.target.value)} className={inputClass} /></label>
    </div></section>

    <section><h3 className="text-sm font-bold uppercase tracking-wide text-slate-500">Skills</h3><div className="mt-4 grid gap-6 lg:grid-cols-2"><SkillEditor label="Required Skills" values={form.requiredSkills} onChange={values => update("requiredSkills", values)} /><SkillEditor label="Preferred Skills" values={form.preferredSkills} onChange={values => update("preferredSkills", values)} /></div></section>

    {error && <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
    <div className="flex flex-wrap justify-end gap-3 border-t border-slate-200 pt-5"><button data-button-variant="outline" type="button" onClick={onCancel} className="text-sm">Cancel</button><button data-button-variant="outline" disabled={saving} className="text-sm">{saving ? "Saving…" : status === "PUBLISHED" ? "Save Corrections" : "Save Draft"}</button>{status !== "PUBLISHED" && <button type="button" disabled={saving} onClick={event => { if (event.currentTarget.form?.reportValidity()) void onSave(form, true); }} className="text-sm">Save &amp; Publish</button>}</div>
  </form>;
}
