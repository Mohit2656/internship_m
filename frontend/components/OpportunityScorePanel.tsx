"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { ScoredCandidate } from "@/lib/opportunities";

export function OpportunityScorePanel({ opportunityId }: { opportunityId: number }) {
  const [candidate, setCandidate] = useState<ScoredCandidate | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    const abort = new AbortController(); setCandidate(null); setError("");
    api.get<ScoredCandidate>(`/student/opportunities/${opportunityId}/matching-score`, { signal: abort.signal })
      .then(({ data }) => { if (!abort.signal.aborted) setCandidate(data); })
      .catch(() => { if (!abort.signal.aborted) setError("Your matching score could not be loaded."); });
    return () => abort.abort();
  }, [opportunityId]);
  return <section className="rounded-2xl border border-blue-200 bg-blue-50/40 p-5"><h2 className="text-xl font-bold">Your opportunity match</h2>{error ? <p className="mt-3 text-sm text-slate-600">{error}</p> : !candidate ? <p role="status" className="mt-3 text-sm text-slate-500">Calculating your matching score...</p> : <>
    <div className="mt-3 flex flex-wrap items-center gap-3"><p className="text-2xl font-bold text-blue-800">{candidate.matchScore}/100</p><span className="text-xs font-bold">{candidate.eligibility}</span>{candidate.strongMatch && <span className="rounded-full bg-emerald-100 px-3 py-1 text-xs font-bold text-emerald-800">Strong match</span>}</div>
    <p className="mt-3 text-sm text-slate-700">Matched skills: {[...candidate.matchedSkills, ...candidate.matchedPreferredSkills].join(", ") || "None evidenced"}</p><p className="mt-2 text-sm text-slate-700">Missing required skills: {candidate.missingRequiredSkills.join(", ") || "None"}</p><p className="mt-2 text-xs text-slate-600">{candidate.eligibilityReasons.join(". ")}</p>
    <p className="mt-3 text-xs leading-5 text-slate-500">Required skills contribute 80%, preferred skills 20%; unspecified groups are excluded and remaining weights rescaled. Only exact normalized skills from your current resume and manually confirmed profile count. Only configured branch, semester, CGPA and skill criteria are evaluated; review other listed requirements manually. This recommendation does not imply company selection. Review the opportunity details and application instructions.</p>
  </>}</section>;
}
