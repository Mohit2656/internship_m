"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { StudentSkillGapAnalysis } from "@/lib/opportunities";

export default function StudentSkillGapsPage() {
  const [analysis, setAnalysis] = useState<StudentSkillGapAnalysis | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { api.get<StudentSkillGapAnalysis>("/student/skill-gaps").then(response => setAnalysis(response.data)).catch(() => setError("Skill-gap analysis could not be loaded.")); }, []);
  return <ProtectedRoute role="STUDENT"><AppFrame><main className="mx-auto max-w-5xl px-5 py-10 sm:px-8">
    <Link href="/student/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link>
    <div className="mt-5"><p className="text-sm font-semibold text-blue-700">Career development</p><h1 className="mt-2 text-3xl font-bold tracking-tight">Skill Gap Intelligence</h1><p className="mt-2 text-slate-600">Based on currently available internship opportunities and your confirmed professional profile.</p></div>
    {error && <p role="alert" className="mt-8 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
    {!analysis && !error && <p className="mt-8 rounded-2xl border border-slate-200 bg-white p-10 text-center text-slate-500">Analyzing current opportunities…</p>}
    {analysis?.status === "INCOMPLETE_PROFILE" && <section className="mt-8 rounded-2xl border border-amber-200 bg-amber-50 p-6"><h2 className="font-bold text-amber-950">Complete your professional profile</h2><p className="mt-2 text-sm text-amber-900">{analysis.message}</p><Link href="/student/professional-profile" className="mt-4 inline-block rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-semibold text-white">Review Professional Profile</Link></section>}
    {analysis?.status === "UNAVAILABLE" && <p className="mt-8 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900">{analysis.message}</p>}
    {analysis?.status === "AVAILABLE" && <div className="mt-8 space-y-6"><p className="text-sm text-slate-500">Analyzed {analysis.opportunitiesAnalyzed} currently available opportunit{analysis.opportunitiesAnalyzed === 1 ? "y" : "ies"}.</p>
      <section className="rounded-2xl border border-blue-200 bg-white p-6"><h2 className="text-xl font-bold">Skills to Strengthen</h2><p className="mt-1 text-sm text-slate-600">Required gaps are shown before preferred gaps.</p>{analysis.skillsToStrengthen.length === 0 ? <p className="mt-5 text-sm text-slate-500">No missing listed skills were found in the current opportunity set.</p> : <ul className="mt-5 divide-y divide-slate-100">{analysis.skillsToStrengthen.map(value => <li key={value.skill} className="flex flex-wrap items-center justify-between gap-3 py-4"><span className="font-semibold">{value.skill}</span><span className="text-sm text-slate-600">Required by {value.requiredByCount} · Preferred by {value.preferredByCount}</span></li>)}</ul>}</section>
      <section className="rounded-2xl border border-emerald-200 bg-white p-6"><h2 className="text-xl font-bold">Your Existing Strengths</h2>{analysis.existingStrengths.length === 0 ? <p className="mt-5 text-sm text-slate-500">No exact matches were found in current opportunities.</p> : <ul className="mt-5 grid gap-3 sm:grid-cols-2">{analysis.existingStrengths.map(value => <li key={value.skill} className="rounded-xl bg-emerald-50 p-4"><p className="font-semibold text-emerald-950">{value.skill}</p><p className="mt-1 text-sm text-emerald-800">Exact match across {value.exactMatchCount} opportunit{value.exactMatchCount === 1 ? "y" : "ies"}</p></li>)}</ul>}</section>
      {analysis.relatedSkills.length > 0 && <section className="rounded-2xl border border-violet-200 bg-white p-6"><h2 className="text-xl font-bold">Related Skills</h2><p className="mt-1 text-sm text-slate-600">Related evidence is kept separate from exact possession.</p><ul className="mt-5 space-y-3">{analysis.relatedSkills.map(value => <li key={`${value.studentSkill}-${value.opportunitySkill}`} className="rounded-xl bg-violet-50 p-4 text-sm"><span className="font-semibold">{value.studentSkill}</span> is related to <span className="font-semibold">{value.opportunitySkill}</span> across {value.opportunityCount} opportunit{value.opportunityCount === 1 ? "y" : "ies"}.</li>)}</ul></section>}
    </div>}
  </main></AppFrame></ProtectedRoute>;
}
