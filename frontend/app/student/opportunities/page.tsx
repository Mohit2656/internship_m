"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { AxiosError } from "axios";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api, ApiError } from "@/lib/api";
import { formatOpportunityDate, formatOpportunityStipend, OpportunityMode, StudentOpportunityPage } from "@/lib/opportunities";

function OpportunitiesContent() {
  const [result, setResult] = useState<StudentOpportunityPage | null>(null);
  const [searchDraft, setSearchDraft] = useState("");
  const [search, setSearch] = useState("");
  const [mode, setMode] = useState<OpportunityMode | "">("");
  const [sort, setSort] = useState<"NEWEST" | "DEADLINE_SOON">("NEWEST");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const { data } = await api.get<StudentOpportunityPage>("/student/opportunities", { params: { search: search || undefined, mode: mode || undefined, sort, page, size: 12 } });
      setResult(data);
    } catch (cause) {
      setError((cause as AxiosError<ApiError>).response?.data?.message || "Internship opportunities could not be loaded.");
    } finally { setLoading(false); }
  }, [search, mode, sort, page]);
  useEffect(() => { void load(); }, [load]);

  function submitSearch(event: FormEvent) { event.preventDefault(); setPage(0); setSearch(searchDraft.trim()); }

  return <main className="mx-auto max-w-7xl px-5 py-10 sm:px-8">
    <Link href="/student/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link>
    <div className="mt-5"><p className="text-sm font-semibold text-blue-700">Student workspace</p><h1 className="mt-2 text-3xl font-bold tracking-tight">Internship Opportunities</h1><p className="mt-2 text-slate-600">Explore active internship opportunities published by the III Cell.</p></div>

    <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-5"><div className="grid gap-4 lg:grid-cols-[1fr_220px_220px]">
      <form onSubmit={submitSearch}><span className="mb-1.5 block text-xs font-bold uppercase tracking-wide text-slate-500">Search</span><div className="flex gap-2"><input value={searchDraft} onChange={event => setSearchDraft(event.target.value)} placeholder="Company or opportunity title" className="min-w-0 flex-1 rounded-xl border border-slate-300 px-4 py-2.5 text-sm outline-none focus:border-blue-600" /><button className="text-sm">Search</button></div></form>
      <label><span className="mb-1.5 block text-xs font-bold uppercase tracking-wide text-slate-500">Mode</span><select value={mode} onChange={event => { setMode(event.target.value as OpportunityMode | ""); setPage(0); }} className="w-full rounded-xl border border-slate-300 px-4 py-2.5 text-sm"><option value="">All modes</option><option value="ONSITE">Onsite</option><option value="HYBRID">Hybrid</option><option value="REMOTE">Remote</option></select></label>
      <label><span className="mb-1.5 block text-xs font-bold uppercase tracking-wide text-slate-500">Sort</span><select value={sort} onChange={event => { setSort(event.target.value as "NEWEST" | "DEADLINE_SOON"); setPage(0); }} className="w-full rounded-xl border border-slate-300 px-4 py-2.5 text-sm"><option value="NEWEST">Newest</option><option value="DEADLINE_SOON">Deadline Soon</option></select></label>
    </div>{search && <button data-button-variant="text" onClick={() => { setSearch(""); setSearchDraft(""); setPage(0); }} className="mt-3 text-xs">Clear search</button>}</section>

    {error && <div className="mt-6 rounded-xl bg-red-50 px-5 py-4 text-sm text-red-700"><p>{error}</p><button data-button-variant="text" onClick={() => void load()} className="mt-2 underline">Try again</button></div>}
    {loading ? <div className="mt-6 grid gap-5 md:grid-cols-2 lg:grid-cols-3">{[1,2,3].map(item => <div key={item} className="h-72 animate-pulse rounded-2xl border border-slate-200 bg-white p-6"><div className="h-4 w-28 rounded bg-slate-200"/><div className="mt-4 h-6 w-3/4 rounded bg-slate-200"/><div className="mt-8 h-24 rounded bg-slate-100"/></div>)}</div> : !error && result?.content.length === 0 ? <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-12 text-center"><h2 className="text-xl font-bold">No internship opportunities are currently available.</h2><p className="mt-2 text-sm text-slate-500">Please check again after the III Cell publishes new opportunities.</p></section> : !error && result ? <>
      <div className="mt-6 grid gap-5 md:grid-cols-2 lg:grid-cols-3">{result.content.map(opportunity => <article key={opportunity.id} className="flex flex-col rounded-2xl border border-slate-200 bg-white p-6"><p className="text-sm font-bold text-blue-700">{opportunity.companyName}</p><h2 className="mt-2 text-xl font-bold text-slate-950">{opportunity.title}</h2><p className="mt-3 text-sm text-slate-600">{opportunity.mode} · {opportunity.location || "Location not specified"}</p><div className="mt-5 grid grid-cols-2 gap-3 rounded-xl bg-slate-50 p-4 text-sm"><div><p className="text-xs text-slate-500">Stipend</p><p className="mt-1 font-semibold">{formatOpportunityStipend(opportunity.stipendAmount, opportunity.currency)}</p></div><div><p className="text-xs text-slate-500">Deadline</p><p className="mt-1 font-semibold">{formatOpportunityDate(opportunity.applicationDeadline)}</p></div></div>{opportunity.requiredSkills.length > 0 && <div className="mt-5"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Required Skills</p><div className="mt-2 flex flex-wrap gap-2">{opportunity.requiredSkills.slice(0, 4).map(skill => <span key={skill} className="rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-800">{skill}</span>)}</div></div>}<Link href={`/student/opportunities/${opportunity.id}`} className="mt-auto pt-6 text-sm font-bold text-blue-700">View Details →</Link></article>)}</div>
      {result.totalPages > 1 && <div className="mt-7 flex items-center justify-between rounded-xl border border-slate-200 bg-white px-5 py-4 text-sm"><span className="text-slate-500">Page {result.page + 1} of {result.totalPages} · {result.totalElements} opportunities</span><div className="flex gap-2"><button data-button-variant="outline" disabled={result.first} onClick={() => setPage(current => current - 1)} className="">Previous</button><button data-button-variant="outline" disabled={result.last} onClick={() => setPage(current => current + 1)} className="">Next</button></div></div>}
    </> : null}
  </main>;
}

export default function StudentOpportunitiesPage() {
  return <ProtectedRoute role="STUDENT"><AppFrame><OpportunitiesContent /></AppFrame></ProtectedRoute>;
}
