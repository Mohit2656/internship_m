"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { api, ApiError } from "@/lib/api";
import { EligibilityStatus, OpportunityMatch, SkillExplanation } from "@/lib/opportunities";

const eligibilityTone = { ELIGIBLE: "bg-emerald-50 text-emerald-800", INELIGIBLE: "bg-rose-50 text-rose-800", UNKNOWN: "bg-amber-50 text-amber-800" };
const criterion: Record<EligibilityStatus, { symbol: string; label: string; tone: string }> = {
  PASS: { symbol: "✓", label: "Meets requirement", tone: "text-emerald-700" },
  FAIL: { symbol: "✕", label: "Does not meet listed requirement", tone: "text-rose-700" },
  UNKNOWN: { symbol: "?", label: "Information unavailable", tone: "text-amber-700" },
  NOT_APPLICABLE: { symbol: "—", label: "No requirement specified", tone: "text-slate-600" },
};

export function OpportunityMatchPanel({ opportunityId }: { opportunityId: number }) {
  const [match, setMatch] = useState<OpportunityMatch | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    api.get<OpportunityMatch>(`/student/opportunities/${opportunityId}/match`).then(response => setMatch(response.data))
      .catch((cause: AxiosError<ApiError>) => setError(cause.response?.data?.message || "Profile alignment could not be loaded."));
  }, [opportunityId]);

  if (error) return <section className="rounded-2xl border border-slate-200 bg-slate-50 p-5"><h2 className="text-xl font-bold">Your profile alignment</h2><p className="mt-2 text-sm text-slate-600">{error}</p></section>;
  if (!match) return <section className="h-36 animate-pulse rounded-2xl bg-slate-100" aria-label="Loading profile alignment" />;

  return <section className="rounded-2xl border border-slate-200 p-5 sm:p-6">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-xl font-bold">Your profile alignment</h2><p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">{match.explanation.summary}</p></div><span className={`rounded-full px-3 py-1 text-xs font-bold ${eligibilityTone[match.eligibility.overall]}`}>{match.eligibility.overall === "UNKNOWN" ? "Eligibility needs more data" : match.eligibility.overall === "ELIGIBLE" ? "Eligible" : "Not eligible"}</span></div>

    <ExplanationSection title="Eligibility"><div className="grid gap-3 sm:grid-cols-3">{match.eligibility.criteria.map(item => { const presentation = criterion[item.status]; return <div key={item.type} className="rounded-xl bg-slate-50 p-4"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">{item.type}</p><p className={`mt-2 text-sm font-bold ${presentation.tone}`}><span aria-hidden>{presentation.symbol}</span> {presentation.label}</p><p className="mt-1 text-xs text-slate-600">Listed: {item.requirement}</p>{item.studentValue && <p className="mt-1 text-xs text-slate-600">Your profile: {item.studentValue}</p>}</div>; })}</div></ExplanationSection>

    {match.profileNotice && <div className="mt-6 rounded-xl bg-blue-50 p-4 text-sm text-blue-950"><p>{match.profileNotice}</p><Link href="/student/professional-profile" className="mt-2 inline-block font-bold text-blue-700">Review Professional Profile →</Link></div>}

    {match.semanticStatus === "UNAVAILABLE" || !match.evidence ? <div className="mt-6 rounded-xl bg-amber-50 p-4"><h3 className="font-bold text-amber-950">Professional alignment is temporarily unavailable</h3><p className="mt-1 text-sm text-amber-900">{match.semanticMessage}</p></div> : <>
      <ExplanationSection title="Why this opportunity aligns with your profile"><SkillExplanations values={match.explanation.alignedSkills} empty="No confirmed skill alignment was found." /></ExplanationSection>
      <ExplanationSection title="Skills to strengthen"><SkillExplanations values={match.explanation.skillsToStrengthen} empty="No missing required skills were identified from your confirmed profile." /></ExplanationSection>
      <ExplanationSection title="Additional preferred skills"><SkillExplanations values={match.explanation.additionalPreferredSkills} empty="No additional preferred skills are listed." /></ExplanationSection>
      {match.evidence.roleEvidence.length > 0 && <ExplanationSection title="Professional signals"><EvidenceList values={match.evidence.roleEvidence} kind="role" /></ExplanationSection>}
      {match.evidence.experienceEvidence.length > 0 && <ExplanationSection title="Relevant experience"><EvidenceList values={match.evidence.experienceEvidence} kind="experience" /></ExplanationSection>}
      <p className="mt-6 text-xs leading-5 text-slate-500">This assessment explains profile alignment only. Related evidence is not equivalent to an exact match and does not predict hiring or selection.</p>
    </>}
  </section>;
}

function ExplanationSection({ title, children }: { title: string; children: React.ReactNode }) {
  return <div className="mt-7 border-t border-slate-200 pt-5"><h3 className="text-base font-bold text-slate-900">{title}</h3><div className="mt-3">{children}</div></div>;
}

function SkillExplanations({ values, empty }: { values: SkillExplanation[]; empty: string }) {
  if (!values.length) return <p className="text-sm text-slate-500">{empty}</p>;
  return <ul className="grid gap-3 sm:grid-cols-2">{values.map((value, index) => <li key={`${value.skill}-${index}`} className="rounded-xl bg-slate-50 p-4"><div className="flex items-start gap-3"><span className={`font-bold ${value.matchType === "EXACT" ? "text-emerald-700" : value.matchType === "RELATED" ? "text-blue-700" : "text-rose-700"}`} aria-hidden>{value.matchType === "EXACT" ? "✓" : value.matchType === "RELATED" ? "~" : "✕"}</span><div><p className="text-sm font-bold">{value.skill}</p><p className="mt-1 text-xs leading-5 text-slate-600">{value.detail}</p></div></div></li>)}</ul>;
}

function EvidenceList({ values, kind }: { values: { studentSignal: string; opportunitySignal: string }[]; kind: "role" | "experience" }) {
  return <ul className="grid gap-3 sm:grid-cols-2">{values.map((value, index) => <li key={index} className="rounded-xl bg-slate-50 p-4 text-sm text-slate-700"><p className="font-semibold text-slate-900">{value.studentSignal}</p><p className="mt-1 text-xs leading-5 text-slate-600">{kind === "role" ? "Your profile signal aligns with" : "Your experience relates to"} {value.opportunitySignal}.</p></li>)}</ul>;
}
