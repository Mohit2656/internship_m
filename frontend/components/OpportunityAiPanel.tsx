"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AxiosError } from "axios";
import { api, ApiError } from "@/lib/api";
import { Opportunity, OpportunityAiAnalysis, OpportunityAiSuggestion } from "@/lib/opportunities";

function errorMessage(cause: unknown) {
  return (cause as AxiosError<ApiError>).response?.data?.message || "We couldn't analyze this job description. Try again.";
}

export function OpportunityAiPanel({ opportunity, onSkillsChanged }: { opportunity: Opportunity; onSkillsChanged: () => Promise<void> }) {
  const [analysis, setAnalysis] = useState<OpportunityAiAnalysis | null>(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const endpoint = `/admin/opportunities/${opportunity.id}/ai-analysis`;

  const load = useCallback(async () => {
    try { setAnalysis((await api.get<OpportunityAiAnalysis>(endpoint)).data); setError(""); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setLoading(false); }
  }, [endpoint]);
  useEffect(() => { void load(); }, [load]);

  const pendingCount = useMemo(() => analysis
    ? [...analysis.requiredSuggestions, ...analysis.preferredSuggestions].filter(item => item.reviewStatus === "PENDING").length : 0, [analysis]);

  async function analyze() {
    setWorking("analyze"); setError(""); setNotice("");
    try { setAnalysis((await api.post<OpportunityAiAnalysis>(endpoint)).data); setNotice("Job description analysis completed."); }
    catch (cause) { setError(errorMessage(cause)); await load(); }
    finally { setWorking(""); }
  }
  async function review(suggestion: OpportunityAiSuggestion, action: "confirm" | "reject") {
    setWorking(`${action}-${suggestion.id}`); setError(""); setNotice("");
    try {
      setAnalysis((await api.post<OpportunityAiAnalysis>(`${endpoint}/suggestions/${suggestion.id}/${action}`)).data);
      if (action === "confirm") await onSkillsChanged();
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setWorking(""); }
  }
  async function confirmAll() {
    setWorking("all"); setError(""); setNotice("");
    try { setAnalysis((await api.post<OpportunityAiAnalysis>(`${endpoint}/suggestions/confirm-all`)).data); await onSkillsChanged(); setNotice("All pending skill suggestions were confirmed."); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setWorking(""); }
  }

  function suggestionList(title: string, values: OpportunityAiSuggestion[]) {
    return <div><h4 className="text-sm font-bold text-slate-800">{title}</h4>
      {values.length === 0 ? <p className="mt-2 text-sm text-slate-500">No new {title.toLowerCase()}.</p> : <ul className="mt-2 space-y-2">{values.map(item =>
        <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm">
          <span className="font-medium">{item.displayName}</span>
          {item.reviewStatus === "PENDING" ? <span className="flex gap-2"><button disabled={!!working} onClick={() => void review(item, "confirm")} className="font-semibold text-emerald-700 disabled:opacity-50">Confirm</button><button disabled={!!working} onClick={() => void review(item, "reject")} className="font-semibold text-red-700 disabled:opacity-50">Reject</button></span>
            : <span className={`text-xs font-bold ${item.reviewStatus === "CONFIRMED" ? "text-emerald-700" : "text-slate-500"}`}>{item.reviewStatus === "CONFIRMED" ? "Confirmed" : "Rejected"}</span>}
        </li>)}</ul>}
    </div>;
  }

  return <section className="mt-8 border-t border-slate-200 pt-7">
    <div className="flex flex-wrap items-start justify-between gap-4"><div><h3 className="text-lg font-bold">AI Job Description Analysis</h3><p className="mt-1 text-sm text-slate-600">Local AI identifies candidate skills and signals. Nothing becomes authoritative until you confirm it.</p></div>
      {!loading && opportunity.status !== "CLOSED" && analysis?.status !== "PENDING" && <button disabled={working === "analyze" || !opportunity.description || analysis?.status === "ANALYZED"} onClick={() => void analyze()} className="rounded-xl bg-violet-700 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50">{working === "analyze" ? "Analyzing…" : analysis?.status === "STALE" || analysis?.status === "FAILED" ? "Re-analyze JD" : analysis?.status === "ANALYZED" ? "Analysis current" : "Analyze JD"}</button>}
    </div>
    {loading && <p className="mt-5 text-sm text-slate-500">Loading analysis…</p>}
    {error && <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
    {notice && <p role="status" className="mt-5 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{notice}</p>}
    {!loading && analysis?.status === "NOT_ANALYZED" && <p className="mt-5 rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-600">Not analyzed. Run local AI when the job description is ready.</p>}
    {analysis?.status === "PENDING" && <p className="mt-5 rounded-xl bg-violet-50 px-4 py-3 text-sm text-violet-800">Analysis is in progress…</p>}
    {analysis?.status === "FAILED" && <p className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">We couldn't analyze this job description. Try again.</p>}
    {analysis?.status === "STALE" && <p className="mt-5 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800">Job description changed. Re-analyze to refresh AI suggestions.</p>}
    {analysis?.status === "ANALYZED" && <div className="mt-6 space-y-6">
      <div className="grid gap-5 sm:grid-cols-2">{suggestionList("Required Skills", analysis.requiredSuggestions)}{suggestionList("Preferred Skills", analysis.preferredSuggestions)}</div>
      {pendingCount > 0 && <button disabled={!!working} onClick={() => void confirmAll()} className="rounded-xl border border-violet-300 px-4 py-2.5 text-sm font-semibold text-violet-800 disabled:opacity-50">{working === "all" ? "Confirming…" : `Confirm All Skills (${pendingCount})`}</button>}
      <div className="grid gap-5 rounded-xl bg-slate-50 p-4 sm:grid-cols-2"><div><h4 className="text-sm font-bold">Role Signals</h4><p className="mt-2 text-sm text-slate-600">{analysis.roleSignals.length ? analysis.roleSignals.join(", ") : "None detected"}</p></div><div><h4 className="text-sm font-bold">Responsibilities</h4>{analysis.responsibilitySignals.length ? <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">{analysis.responsibilitySignals.map(value => <li key={value}>{value}</li>)}</ul> : <p className="mt-2 text-sm text-slate-600">None detected</p>}</div></div>
      <p className="text-xs text-slate-500">Local model: {analysis.modelName} · {analysis.modelVersion}</p>
    </div>}
  </section>;
}
