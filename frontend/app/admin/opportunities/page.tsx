"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AxiosError } from "axios";
import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { OpportunityForm } from "@/components/OpportunityForm";
import { OpportunityAiPanel } from "@/components/OpportunityAiPanel";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api, ApiError } from "@/lib/api";
import { branchLabels, emptyOpportunity, Opportunity, OpportunityInput, OpportunityMode, OpportunityStatus, toInput, toPayload } from "@/lib/opportunities";

const statusStyles: Record<OpportunityStatus, string> = {
  DRAFT: "bg-amber-50 text-amber-800",
  PUBLISHED: "bg-emerald-50 text-emerald-800",
  CLOSED: "bg-slate-100 text-slate-700",
};

function message(cause: unknown, fallback: string) {
  const data = (cause as AxiosError<ApiError>).response?.data;
  return data?.fieldErrors ? Object.values(data.fieldErrors).join(" ") : data?.message || fallback;
}
function displayDate(value: string | null) {
  return value ? new Intl.DateTimeFormat("en-IN", { dateStyle: "medium" }).format(new Date(`${value}T00:00:00`)) : "No deadline";
}
function displayTime(value: string | null) {
  return value ? new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";
}
function displayStipend(opportunity: Opportunity) {
  if (Number(opportunity.stipendAmount) === 0) return "Unpaid";
  try { return `${new Intl.NumberFormat("en-IN", { style: "currency", currency: opportunity.currency, maximumFractionDigits: 2 }).format(opportunity.stipendAmount)}/month`; }
  catch { return `${opportunity.stipendAmount} ${opportunity.currency}/month`; }
}

function OpportunitiesContent() {
  const [items, setItems] = useState<Opportunity[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [editing, setEditing] = useState<Opportunity | "new" | null>(null);
  const [viewing, setViewing] = useState<Opportunity | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<OpportunityStatus | "">("");
  const [modeFilter, setModeFilter] = useState<OpportunityMode | "">("");
  const [companyFilter, setCompanyFilter] = useState("");

  const load = useCallback(async () => {
    try { const { data } = await api.get<Opportunity[]>("/admin/opportunities"); setItems(data); setError(""); }
    catch (cause) { setError(message(cause, "Unable to load opportunities.")); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const companies = useMemo(() => [...new Set(items.map(item => item.companyName))].sort(), [items]);
  const filtered = useMemo(() => items.filter(item => {
    const query = search.trim().toLocaleLowerCase();
    return (!query || item.companyName.toLocaleLowerCase().includes(query) || item.title.toLocaleLowerCase().includes(query))
      && (!statusFilter || item.status === statusFilter)
      && (!modeFilter || item.internshipMode === modeFilter)
      && (!companyFilter || item.companyName === companyFilter);
  }), [items, search, statusFilter, modeFilter, companyFilter]);

  async function save(input: OpportunityInput, publish: boolean) {
    setSaving(true); setFormError(""); setNotice("");
    try {
      const response = editing === "new"
        ? await api.post<Opportunity>("/admin/opportunities", toPayload(input))
        : await api.put<Opportunity>(`/admin/opportunities/${editing!.id}`, toPayload(input));
      let saved = response.data;
      if (publish && saved.status === "DRAFT") saved = (await api.post<Opportunity>(`/admin/opportunities/${saved.id}/publish`)).data;
      setEditing(null); setNotice(`${saved.companyName} — ${saved.title} ${publish ? "was published." : "was saved."}`);
      await load();
    } catch (cause) { setFormError(message(cause, "Unable to save the opportunity.")); }
    finally { setSaving(false); }
  }

  async function transition(opportunity: Opportunity, action: "publish" | "close") {
    if (!window.confirm(action === "publish" ? `Publish ${opportunity.title}?` : `Close ${opportunity.title}?`)) return;
    setError(""); setNotice("");
    try {
      const { data } = await api.post<Opportunity>(`/admin/opportunities/${opportunity.id}/${action}`);
      setNotice(`${data.companyName} — ${data.title} is now ${data.status.toLocaleLowerCase()}.`); await load();
    } catch (cause) { setError(message(cause, `Unable to ${action} the opportunity.`)); }
  }

  async function refreshViewing() {
    if (!viewing) return;
    const { data } = await api.get<Opportunity>(`/admin/opportunities/${viewing.id}`);
    setViewing(data); await load();
  }

  if (editing) return <main className="mx-auto max-w-6xl px-5 py-10 sm:px-8"><button onClick={() => setEditing(null)} className="text-sm font-semibold text-blue-700">← Back to opportunities</button><div className="mt-5 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8"><p className="text-sm font-semibold text-blue-700">III Cell workspace</p><h1 className="mt-2 text-3xl font-bold">{editing === "new" ? "Create Opportunity" : "Edit Opportunity"}</h1><p className="mt-2 text-slate-600">Save incomplete work as a draft. Publishing is a separate backend-controlled lifecycle action.</p><div className="mt-8"><OpportunityForm key={editing === "new" ? "new" : editing.id} initial={editing === "new" ? emptyOpportunity : toInput(editing)} status={editing === "new" ? undefined : editing.status} saving={saving} error={formError} onCancel={() => setEditing(null)} onSave={save} /></div></div></main>;

  return <main className="mx-auto max-w-7xl px-5 py-10 sm:px-8">
    <div className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-semibold text-blue-700">III Cell workspace</p><h1 className="mt-2 text-3xl font-bold tracking-tight">Opportunity Management</h1><p className="mt-2 text-slate-600">Create, publish and preserve internship opportunities.</p></div><button onClick={() => { setFormError(""); setEditing("new"); }} className="rounded-xl bg-blue-700 px-5 py-3 text-sm font-semibold text-white">+ Create Opportunity</button></div>

    {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
    {notice && <p role="status" className="mt-6 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{notice}</p>}

    <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-5"><div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
      <label><span className="mb-1 block text-xs font-bold uppercase tracking-wide text-slate-500">Search</span><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Company or title" className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm" /></label>
      <label><span className="mb-1 block text-xs font-bold uppercase tracking-wide text-slate-500">Status</span><select value={statusFilter} onChange={event => setStatusFilter(event.target.value as OpportunityStatus | "")} className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm"><option value="">All statuses</option><option value="DRAFT">Draft</option><option value="PUBLISHED">Published</option><option value="CLOSED">Closed</option></select></label>
      <label><span className="mb-1 block text-xs font-bold uppercase tracking-wide text-slate-500">Company</span><select value={companyFilter} onChange={event => setCompanyFilter(event.target.value)} className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm"><option value="">All companies</option>{companies.map(company => <option key={company}>{company}</option>)}</select></label>
      <label><span className="mb-1 block text-xs font-bold uppercase tracking-wide text-slate-500">Mode</span><select value={modeFilter} onChange={event => setModeFilter(event.target.value as OpportunityMode | "")} className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm"><option value="">All modes</option><option value="ONSITE">Onsite</option><option value="HYBRID">Hybrid</option><option value="REMOTE">Remote</option></select></label>
    </div></section>

    <section className="mt-5 overflow-hidden rounded-2xl border border-slate-200 bg-white">
      {loading ? <p className="p-8 text-center text-slate-500">Loading opportunities…</p> : filtered.length === 0 ? <div className="p-10 text-center"><h2 className="text-lg font-bold">No opportunities found</h2><p className="mt-2 text-sm text-slate-500">Create an opportunity or adjust the current filters.</p></div> : <div className="overflow-x-auto"><table className="w-full min-w-[900px] text-left text-sm"><thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr><th className="px-5 py-4">Company / Opportunity</th><th className="px-4 py-4">Mode</th><th className="px-4 py-4">Deadline</th><th className="px-4 py-4">Stipend</th><th className="px-4 py-4">Status</th><th className="px-5 py-4 text-right">Actions</th></tr></thead><tbody className="divide-y divide-slate-100">{filtered.map(opportunity => <tr key={opportunity.id}><td className="px-5 py-4"><p className="font-semibold text-slate-900">{opportunity.title}</p><p className="mt-1 text-slate-500">{opportunity.companyName}</p></td><td className="px-4 py-4">{opportunity.internshipMode ?? "—"}</td><td className="px-4 py-4">{displayDate(opportunity.applicationDeadline)}</td><td className="px-4 py-4">{displayStipend(opportunity)}</td><td className="px-4 py-4"><span className={`rounded-full px-3 py-1 text-xs font-bold ${statusStyles[opportunity.status]}`}>{opportunity.status}</span></td><td className="px-5 py-4"><div className="flex justify-end gap-3"><button onClick={() => setViewing(opportunity)} className="font-semibold text-slate-700">View</button>{opportunity.status !== "CLOSED" && <button onClick={() => { setFormError(""); setEditing(opportunity); }} className="font-semibold text-blue-700">Edit</button>}{opportunity.status === "DRAFT" && <button onClick={() => void transition(opportunity, "publish")} className="font-semibold text-emerald-700">Publish</button>}{opportunity.status === "PUBLISHED" && <button onClick={() => void transition(opportunity, "close")} className="font-semibold text-red-700">Close</button>}</div></td></tr>)}</tbody></table></div>}
    </section>

    {viewing && <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/50 p-4" role="dialog" aria-modal="true"><article className="my-8 w-full max-w-3xl rounded-2xl bg-white p-6 shadow-2xl sm:p-8"><div className="flex items-start justify-between gap-4"><div><span className={`rounded-full px-3 py-1 text-xs font-bold ${statusStyles[viewing.status]}`}>{viewing.status}</span><h2 className="mt-4 text-2xl font-bold">{viewing.title}</h2><p className="mt-1 text-slate-600">{viewing.companyName}</p></div><button onClick={() => setViewing(null)} aria-label="Close detail" className="text-2xl text-slate-500">×</button></div><p className="mt-6 whitespace-pre-wrap text-sm leading-6 text-slate-700">{viewing.description || "No description yet."}</p><dl className="mt-6 grid gap-4 rounded-xl bg-slate-50 p-5 text-sm sm:grid-cols-2"><div><dt className="text-slate-500">Mode / Location</dt><dd className="mt-1 font-semibold">{viewing.internshipMode ?? "—"} · {viewing.location ?? "—"}</dd></div><div><dt className="text-slate-500">Duration / Stipend</dt><dd className="mt-1 font-semibold">{viewing.durationMonths ? `${viewing.durationMonths} months` : "—"} · {displayStipend(viewing)}</dd></div><div><dt className="text-slate-500">Deadline</dt><dd className="mt-1 font-semibold">{displayDate(viewing.applicationDeadline)}</dd></div><div><dt className="text-slate-500">Minimum CGPA</dt><dd className="mt-1 font-semibold">{viewing.minimumCgpa ?? "No restriction"}</dd></div><div><dt className="text-slate-500">Published</dt><dd className="mt-1 font-semibold">{displayTime(viewing.publishedAt)}</dd></div><div><dt className="text-slate-500">Closed</dt><dd className="mt-1 font-semibold">{displayTime(viewing.closedAt)}</dd></div></dl><div className="mt-6 grid gap-5 sm:grid-cols-2"><div><h3 className="text-sm font-bold">Eligible Branches</h3><p className="mt-2 text-sm text-slate-600">{viewing.eligibleBranches.length ? viewing.eligibleBranches.map(branch => branchLabels[branch]).join(", ") : "All branches"}</p></div><div><h3 className="text-sm font-bold">Eligible Semesters</h3><p className="mt-2 text-sm text-slate-600">{viewing.eligibleSemesters.length ? viewing.eligibleSemesters.join(", ") : "All semesters"}</p></div><div><h3 className="text-sm font-bold">Required Skills</h3><p className="mt-2 text-sm text-slate-600">{viewing.requiredSkills.length ? viewing.requiredSkills.map(skill => skill.displayName).join(", ") : "None specified"}</p></div><div><h3 className="text-sm font-bold">Preferred Skills</h3><p className="mt-2 text-sm text-slate-600">{viewing.preferredSkills.length ? viewing.preferredSkills.map(skill => skill.displayName).join(", ") : "None specified"}</p></div></div>{viewing.companyWebsite && <a href={viewing.companyWebsite} target="_blank" rel="noreferrer" className="mt-6 inline-block text-sm font-semibold text-blue-700">Open company website ↗</a>}<OpportunityAiPanel opportunity={viewing} onSkillsChanged={refreshViewing} /></article></div>}
    {items.some(item => item.status !== "CLOSED") && <section className="mt-6 rounded-2xl border border-violet-200 bg-violet-50 p-5"><h2 className="font-bold text-violet-950">Talent Discovery</h2><p className="mt-1 text-sm text-violet-800">Review evidence-based student alignment for a draft or published opportunity.</p><div className="mt-3 flex flex-wrap gap-2">{items.filter(item => item.status !== "CLOSED").map(item => <Link key={item.id} href={`/admin/opportunities/${item.id}/talent`} className="rounded-lg bg-white px-3 py-2 text-sm font-semibold text-violet-700 shadow-sm">{item.companyName} · {item.title}</Link>)}</div></section>}
  </main>;
}

export default function OpportunitiesPage() {
  return <ProtectedRoute role="ADMIN"><AppFrame><OpportunitiesContent /></AppFrame></ProtectedRoute>;
}
