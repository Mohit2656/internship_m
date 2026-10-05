"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { AxiosError } from "axios";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api, ApiError } from "@/lib/api";
import { AdminSkillGapAnalysis, Branch, branchLabels, OpportunitySkillMatch, TalentCandidate, TalentDiscoveryPage, TalentEligibilityFilter } from "@/lib/opportunities";

const eligibilityStyle = {
  ELIGIBLE: "bg-emerald-50 text-emerald-800",
  UNKNOWN: "bg-amber-50 text-amber-800",
  INELIGIBLE: "bg-red-50 text-red-800",
} as const;

function errorMessage(cause: unknown) {
  return (cause as AxiosError<ApiError>).response?.data?.message || "Unable to load talent discovery right now.";
}

function Evidence({ title, values }: { title: string; values: OpportunitySkillMatch[] }) {
  return <div><h4 className="text-xs font-bold uppercase tracking-wide text-slate-500">{title}</h4>
    {values.length === 0 ? <p className="mt-2 text-sm text-slate-500">None specified.</p> : <ul className="mt-2 space-y-2">{values.map(value => <li key={value.opportunitySkill} className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-slate-50 px-3 py-2 text-sm"><span className="font-medium">{value.opportunitySkill}</span><span className="text-slate-600">{value.matchType === "MISSING" ? "Not evidenced" : `${value.matchType === "EXACT" ? "Exact" : "Related"}: ${value.studentSkill}`}</span></li>)}</ul>}
  </div>;
}

function CandidateCard({ candidate }: { candidate: TalentCandidate }) {
  const [expanded, setExpanded] = useState(false);
  const overall = candidate.alignment.eligibility.overall;
  return <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
    <div className="flex flex-wrap items-start justify-between gap-4"><div><h2 className="text-lg font-bold text-slate-950">{candidate.studentName}</h2><p className="mt-1 text-sm text-slate-600">UID {candidate.uid} · {branchLabels[candidate.branch]} · Semester {candidate.semester}</p></div><span className={`rounded-full px-3 py-1 text-xs font-bold ${eligibilityStyle[overall]}`}>{overall}</span></div>
    <div className="mt-4 grid grid-cols-3 gap-2 text-center"><div className="rounded-xl bg-blue-50 p-3"><p className="text-xl font-bold text-blue-800">{candidate.exactRequiredSkills}</p><p className="text-xs text-blue-700">Exact required</p></div><div className="rounded-xl bg-violet-50 p-3"><p className="text-xl font-bold text-violet-800">{candidate.relatedRequiredSkills}</p><p className="text-xs text-violet-700">Related required</p></div><div className="rounded-xl bg-slate-100 p-3"><p className="text-xl font-bold text-slate-800">{candidate.exactPreferredSkills}</p><p className="text-xs text-slate-600">Exact preferred</p></div></div>
    <p className="mt-4 text-sm leading-6 text-slate-700">{candidate.alignment.explanation.summary}</p>
    <button onClick={() => setExpanded(value => !value)} className="mt-4 text-sm font-semibold text-blue-700" aria-expanded={expanded}>{expanded ? "Hide alignment" : "View alignment"}</button>
    {expanded && <div className="mt-4 space-y-5 border-t border-slate-200 pt-4">
      <div><h4 className="text-xs font-bold uppercase tracking-wide text-slate-500">Eligibility evidence</h4><ul className="mt-2 space-y-2">{candidate.alignment.eligibility.criteria.map(criterion => <li key={criterion.type} className="rounded-lg bg-slate-50 px-3 py-2 text-sm"><span className="font-semibold">{criterion.type}: {criterion.status}</span><span className="ml-2 text-slate-600">{criterion.requirement}{criterion.studentValue ? ` · Student: ${criterion.studentValue}` : ""}</span></li>)}</ul></div>
      {candidate.alignment.semanticStatus === "UNAVAILABLE" || !candidate.alignment.evidence
        ? <p className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800">{candidate.alignment.semanticMessage || "Professional alignment is temporarily unavailable."}</p>
        : <><div className="grid gap-5 md:grid-cols-2"><Evidence title="Required skills" values={candidate.alignment.evidence.requiredSkills} /><Evidence title="Preferred skills" values={candidate.alignment.evidence.preferredSkills} /></div>
          <div className="grid gap-5 md:grid-cols-2"><div><h4 className="text-xs font-bold uppercase tracking-wide text-slate-500">Role evidence</h4><p className="mt-2 text-sm text-slate-600">{candidate.alignment.evidence.roleEvidence.length ? candidate.alignment.evidence.roleEvidence.map(value => `${value.studentSignal} → ${value.opportunitySignal}`).join("; ") : "No related role evidence found."}</p></div><div><h4 className="text-xs font-bold uppercase tracking-wide text-slate-500">Experience evidence</h4><p className="mt-2 text-sm text-slate-600">{candidate.alignment.evidence.experienceEvidence.length ? candidate.alignment.evidence.experienceEvidence.map(value => `${value.studentSignal} → ${value.opportunitySignal}`).join("; ") : "No related experience evidence found."}</p></div></div></>}
    </div>}
  </article>;
}

function TalentDiscoveryContent() {
  const params = useParams<{ id: string }>();
  const opportunityId = params.id;
  const [data, setData] = useState<TalentDiscoveryPage | null>(null);
  const [skillGaps, setSkillGaps] = useState<AdminSkillGapAnalysis | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [eligibility, setEligibility] = useState<TalentEligibilityFilter>("ELIGIBLE_OR_UNKNOWN");
  const [branch, setBranch] = useState<Branch | "">("");
  const [semester, setSemester] = useState("");
  const [skill, setSkill] = useState("");

  const load = useCallback(async (page = 0) => {
    setLoading(true); setError("");
    try {
      const requestParams = { search: search || undefined, eligibility, branch: branch || undefined, semester: semester || undefined, skill: skill || undefined };
      const [response, gapResponse] = await Promise.all([
        api.get<TalentDiscoveryPage>(`/admin/opportunities/${opportunityId}/talent-discovery`, { params: { ...requestParams, page, size: 20 } }),
        api.get<AdminSkillGapAnalysis>(`/admin/opportunities/${opportunityId}/talent-discovery/skill-gaps`, { params: requestParams }),
      ]);
      setData(response.data); setSkillGaps(gapResponse.data);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setLoading(false); }
  }, [opportunityId, search, eligibility, branch, semester, skill]);

  useEffect(() => { void load(0); }, [opportunityId]); // eslint-disable-line react-hooks/exhaustive-deps
  function submit(event: FormEvent) { event.preventDefault(); void load(0); }

  return <main className="mx-auto max-w-6xl px-5 py-10 sm:px-8">
    <Link href="/admin/opportunities" className="text-sm font-semibold text-blue-700">← Back to opportunities</Link>
    <div className="mt-5"><p className="text-sm font-semibold text-violet-700">III Cell talent discovery</p><h1 className="mt-2 text-3xl font-bold tracking-tight">{data?.opportunity.title || "Opportunity candidates"}</h1><p className="mt-2 text-slate-600">{data ? `${data.opportunity.companyName} · ${data.opportunity.status}` : "Review eligibility and professional alignment evidence."}</p></div>
    <form onSubmit={submit} className="mt-8 rounded-2xl border border-slate-200 bg-white p-5"><div className="grid gap-3 md:grid-cols-2 lg:grid-cols-5">
      <label><span className="mb-1 block text-xs font-bold uppercase text-slate-500">Name or UID</span><input value={search} onChange={event => setSearch(event.target.value)} className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm" /></label>
      <label><span className="mb-1 block text-xs font-bold uppercase text-slate-500">Eligibility</span><select value={eligibility} onChange={event => setEligibility(event.target.value as TalentEligibilityFilter)} className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm"><option value="ELIGIBLE_OR_UNKNOWN">Eligible + unknown</option><option value="ELIGIBLE">Eligible</option><option value="UNKNOWN">Unknown</option><option value="INELIGIBLE">Ineligible</option><option value="ALL">All</option></select></label>
      <label><span className="mb-1 block text-xs font-bold uppercase text-slate-500">Branch</span><select value={branch} onChange={event => setBranch(event.target.value as Branch | "")} className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm"><option value="">All branches</option>{Object.entries(branchLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label><span className="mb-1 block text-xs font-bold uppercase text-slate-500">Semester</span><input value={semester} onChange={event => setSemester(event.target.value)} type="number" min="1" max="12" className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm" /></label>
      <label><span className="mb-1 block text-xs font-bold uppercase text-slate-500">Relevant skill</span><input value={skill} onChange={event => setSkill(event.target.value)} placeholder="e.g. Java" className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm" /></label>
    </div><button type="submit" className="mt-4 rounded-xl bg-blue-700 px-5 py-2.5 text-sm font-semibold text-white">Apply filters</button></form>
    {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
    {skillGaps && <section className="mt-6 rounded-2xl border border-violet-200 bg-white p-6"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-wide text-violet-700">Skill Gap Overview</p><h2 className="mt-2 text-xl font-bold">Candidate population gaps</h2></div><span className="text-sm text-slate-500">{skillGaps.candidatesAnalyzed} eligible/unknown candidate{skillGaps.candidatesAnalyzed === 1 ? "" : "s"}</span></div>{skillGaps.status === "UNAVAILABLE" ? <p className="mt-4 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900">{skillGaps.message}</p> : <div className="mt-5 grid gap-6 md:grid-cols-2"><div><h3 className="font-bold">Required skills</h3>{skillGaps.requiredGaps.length === 0 ? <p className="mt-3 text-sm text-slate-500">No required skill gaps found.</p> : <ul className="mt-3 divide-y divide-slate-100">{skillGaps.requiredGaps.map(value => <li key={value.skill} className="flex justify-between gap-3 py-3 text-sm"><span className="font-semibold">{value.skill}</span><span className="text-slate-600">{value.studentsMissing} missing</span></li>)}</ul>}</div><div><h3 className="font-bold">Preferred skills</h3>{skillGaps.preferredGaps.length === 0 ? <p className="mt-3 text-sm text-slate-500">No preferred skill gaps found.</p> : <ul className="mt-3 divide-y divide-slate-100">{skillGaps.preferredGaps.map(value => <li key={value.skill} className="flex justify-between gap-3 py-3 text-sm"><span className="font-semibold">{value.skill}</span><span className="text-slate-600">{value.studentsMissing} missing</span></li>)}</ul>}</div></div>}</section>}
    {data && <div className="mt-6 flex flex-wrap items-center justify-between gap-3"><p className="text-sm font-semibold text-slate-700">{data.totalElements} candidate{data.totalElements === 1 ? "" : "s"}</p><p className="max-w-3xl text-xs text-slate-500">Ordering: {data.orderingExplanation}</p></div>}
    {loading ? <p className="mt-8 rounded-2xl border border-slate-200 bg-white p-10 text-center text-slate-500">Analyzing candidate evidence…</p> : data?.candidates.length === 0 ? <div className="mt-8 rounded-2xl border border-slate-200 bg-white p-10 text-center"><h2 className="text-lg font-bold">No matching candidates</h2><p className="mt-2 text-sm text-slate-500">Adjust the filters or review the opportunity eligibility rules.</p></div> : <section className="mt-5 grid gap-4">{data?.candidates.map(candidate => <CandidateCard key={candidate.studentId} candidate={candidate} />)}</section>}
    {data && data.totalPages > 1 && <div className="mt-6 flex justify-center gap-3"><button disabled={data.page === 0 || loading} onClick={() => void load(data.page - 1)} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold disabled:opacity-40">Previous</button><span className="px-3 py-2 text-sm text-slate-600">Page {data.page + 1} of {data.totalPages}</span><button disabled={data.page + 1 >= data.totalPages || loading} onClick={() => void load(data.page + 1)} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold disabled:opacity-40">Next</button></div>}
  </main>;
}

export default function TalentDiscoveryPageView() {
  return <ProtectedRoute role="ADMIN"><AppFrame><TalentDiscoveryContent /></AppFrame></ProtectedRoute>;
}
