"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { OpportunityMatchPanel } from "@/components/OpportunityMatchPanel";
import { api, ApiError } from "@/lib/api";
import { branchLabels, formatOpportunityDate, formatOpportunityStipend, safeCompanyUrl, StudentOpportunityDetail } from "@/lib/opportunities";

function OpportunityDetailContent() {
  const params = useParams<{ id: string }>();
  const [opportunity, setOpportunity] = useState<StudentOpportunityDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    api.get<StudentOpportunityDetail>(`/student/opportunities/${params.id}`).then(response => setOpportunity(response.data))
      .catch((cause: AxiosError<ApiError>) => setError(cause.response?.status === 404 ? "This opportunity is not currently available." : cause.response?.data?.message || "Opportunity details could not be loaded."))
      .finally(() => setLoading(false));
  }, [params.id]);

  if (loading) return <main className="mx-auto max-w-5xl px-5 py-10 sm:px-8"><div className="h-96 animate-pulse rounded-2xl border border-slate-200 bg-white" /></main>;
  if (error || !opportunity) return <main className="mx-auto max-w-5xl px-5 py-10 sm:px-8"><Link href="/student/opportunities" className="text-sm font-semibold text-blue-700">← Back to opportunities</Link><div className="mt-6 rounded-2xl border border-slate-200 bg-white p-10 text-center"><h1 className="text-xl font-bold">Opportunity unavailable</h1><p className="mt-2 text-sm text-slate-600">{error}</p></div></main>;
  const website = safeCompanyUrl(opportunity.companyWebsite);

  return <main className="mx-auto max-w-5xl px-5 py-10 sm:px-8"><Link href="/student/opportunities" className="text-sm font-semibold text-blue-700">← Back to opportunities</Link>
    <article className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white"><header className="border-b border-slate-200 p-6 sm:p-8"><p className="text-sm font-bold text-blue-700">{opportunity.companyName}</p><h1 className="mt-2 text-3xl font-bold tracking-tight">{opportunity.title}</h1><p className="mt-4 text-slate-600">{opportunity.mode} · {opportunity.location || "Location not specified"}</p>{website && <a href={website} target="_blank" rel="noopener noreferrer" className="mt-4 inline-block text-sm font-semibold text-blue-700">Visit company website ↗</a>}</header>
      <div className="space-y-8 p-6 sm:p-8"><section><h2 className="text-xl font-bold">Opportunity Description</h2><p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-slate-700">{opportunity.description}</p></section>
        <OpportunityMatchPanel opportunityId={opportunity.id} />
        <section className="grid gap-4 rounded-2xl bg-slate-50 p-5 sm:grid-cols-2 lg:grid-cols-4"><div><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Mode</p><p className="mt-2 font-semibold">{opportunity.mode}</p></div><div><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Duration</p><p className="mt-2 font-semibold">{opportunity.durationMonths ? `${opportunity.durationMonths} months` : "Not specified"}</p></div><div><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Stipend</p><p className="mt-2 font-semibold">{formatOpportunityStipend(opportunity.stipendAmount, opportunity.currency)}</p></div><div><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Deadline</p><p className="mt-2 font-semibold">{formatOpportunityDate(opportunity.applicationDeadline)}</p></div></section>
        <section><h2 className="text-xl font-bold">Eligibility</h2><dl className="mt-4 grid gap-4 sm:grid-cols-3"><div className="rounded-xl border border-slate-200 p-4"><dt className="text-xs font-bold uppercase tracking-wide text-slate-500">Branches</dt><dd className="mt-2 text-sm font-semibold">{opportunity.eligibleBranches.length ? opportunity.eligibleBranches.map(branch => branchLabels[branch]).join(", ") : "All branches"}</dd></div><div className="rounded-xl border border-slate-200 p-4"><dt className="text-xs font-bold uppercase tracking-wide text-slate-500">Semesters</dt><dd className="mt-2 text-sm font-semibold">{opportunity.eligibleSemesters.length ? opportunity.eligibleSemesters.join(", ") : "All semesters"}</dd></div><div className="rounded-xl border border-slate-200 p-4"><dt className="text-xs font-bold uppercase tracking-wide text-slate-500">Minimum CGPA</dt><dd className="mt-2 text-sm font-semibold">{opportunity.minimumCgpa ?? "No restriction"}</dd></div></dl></section>
        <section className="grid gap-6 sm:grid-cols-2"><SkillList title="Required Skills" skills={opportunity.requiredSkills} empty="No required skills specified" /><SkillList title="Preferred Skills" skills={opportunity.preferredSkills} empty="No preferred skills specified" /></section>
      </div>
    </article>
  </main>;
}

function SkillList({ title, skills, empty }: { title: string; skills: string[]; empty: string }) {
  return <div><h2 className="text-xl font-bold">{title}</h2>{skills.length ? <div className="mt-3 flex flex-wrap gap-2">{skills.map(skill => <span key={skill} className="rounded-full bg-blue-50 px-3 py-1.5 text-sm font-semibold text-blue-800">{skill}</span>)}</div> : <p className="mt-3 text-sm text-slate-500">{empty}</p>}</div>;
}

export default function StudentOpportunityDetailPage() {
  return <ProtectedRoute role="STUDENT"><AppFrame><OpportunityDetailContent /></AppFrame></ProtectedRoute>;
}
