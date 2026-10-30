"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/monthly-progress";
import { downloadResume, ProfessionalProfile } from "@/lib/professional-profile";
import { branchLabels, MatchingRun, MatchingRunSummary, matchingCsv } from "@/lib/opportunities";

export function OpportunityMatchingReport({ opportunityId }: { opportunityId: string }) {
  const [history, setHistory] = useState<MatchingRunSummary[]>([]);
  const [selected, setSelected] = useState("");
  const [run, setRun] = useState<MatchingRun | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [eligibility, setEligibility] = useState("ALL");
  const [branch, setBranch] = useState("");
  const [order, setOrder] = useState("DESC");
  const endpoint = `/admin/opportunities/${opportunityId}/matching-runs`;

  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setRun(null); setSelected(""); setHistory([]); setError("");
    api.get<MatchingRunSummary[]>(endpoint, { signal: abort.signal }).then(({ data }) => {
      if (!abort.signal.aborted) { setHistory(data); setSelected(data[0]?.id.toString() ?? ""); }
    }).catch(() => { if (!abort.signal.aborted) setError("Matching history could not be loaded."); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [endpoint]);

  useEffect(() => {
    if (!selected) return;
    const abort = new AbortController(); setLoading(true); setRun(null); setError("");
    api.get<MatchingRun>(`${endpoint}/${selected}`, { signal: abort.signal }).then(({ data }) => {
      if (!abort.signal.aborted) setRun(data);
    }).catch(() => { if (!abort.signal.aborted) setError("The selected matching report could not be loaded."); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [endpoint, selected]);

  async function generate() {
    if (busy) return;
    setBusy(true); setError("");
    try {
      const { data } = await api.post<MatchingRun>(endpoint);
      setHistory(previous => [data, ...previous]); setSelected(data.id.toString());
    } catch { setError("Matching could not be generated. Closed opportunities have read-only history."); }
    finally { setBusy(false); }
  }

  async function resume(studentId: number) {
    setBusy(true); setError("");
    try {
      const { data } = await api.get<ProfessionalProfile>(`/admin/students/${studentId}/professional-profile`);
      if (!data.resume) throw new Error("No current resume");
      await downloadResume(`/admin/students/${studentId}/resume/download`, data.resume.originalFilename);
    }
    catch { setError("The current resume could not be downloaded. Open the student's profile to check its availability."); }
    finally { setBusy(false); }
  }
  const candidates = (run?.report.candidates ?? []).filter(candidate => {
    const text = `${candidate.studentName ?? ""} ${candidate.uid ?? ""} ${candidate.matchedSkills.join(" ")} ${candidate.matchedPreferredSkills.join(" ")}`.toLowerCase();
    return (!search.trim() || text.includes(search.trim().toLowerCase())) && (eligibility === "ALL" || candidate.eligibility === eligibility)
      && (!branch || candidate.branch === branch);
  }).sort((a, b) => (order === "DESC" ? b.matchScore - a.matchScore : a.matchScore - b.matchScore) || (a.studentName ?? "").localeCompare(b.studentName ?? ""));

  function exportReport() {
    const url = URL.createObjectURL(new Blob([matchingCsv(candidates)], { type: "text/csv;charset=utf-8" }));
    const anchor = document.createElement("a"); anchor.href = url; anchor.download = `opportunity-${opportunityId}-run-${selected}.csv`; anchor.click(); URL.revokeObjectURL(url);
  }
  return <section className="mt-6 rounded-2xl border border-blue-200 bg-white p-5 sm:p-6">
    <div className="flex flex-wrap items-start justify-between gap-4"><div><h2 className="text-xl font-bold">Ranked matching report</h2><p className="mt-2 text-sm text-slate-600">Publication creates a saved report. Recalculate after resume corrections or changed requirements.</p></div><button type="button" disabled={busy || loading} onClick={() => void generate()} className="text-sm">{busy ? "Working..." : history.length ? "Recalculate matches" : "Generate matching report"}</button></div>
    {error && <p role="alert" className="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {history.length > 0 && <label className="mt-5 block text-sm font-semibold">Matching history<select aria-label="Matching history" value={selected} disabled={busy} onChange={event => setSelected(event.target.value)} className="mt-2 block w-full rounded-xl border border-slate-300 px-3 py-2">{history.map(item => <option key={item.id} value={item.id}>{formatDateTime(item.createdAt)} · {item.triggerType.toLowerCase()} · Run {item.id}</option>)}</select></label>}
    {loading ? <p role="status" className="mt-5 text-sm text-slate-500">Loading saved matching report...</p> : !run ? <p className="mt-5 text-sm text-slate-500">No saved matching report yet. Generate a report to rank current candidates.</p> : <>
      <p className="mt-4 text-xs leading-6 text-slate-600">{run.report.scoringExplanation}</p>
      <p className="mt-2 text-xs text-slate-500">Snapshot: {formatDateTime(run.createdAt)}. Strong-match alerts require verified eligibility, every required skill, and at least {run.report.strongMatchThreshold}/100. Profile and resume links open current information.</p>
      <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <label className="text-xs font-semibold">Student, UID or skill<input aria-label="Search matching candidates" value={search} onChange={event => setSearch(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm" /></label>
        <label className="text-xs font-semibold">Eligibility<select aria-label="Matching eligibility" value={eligibility} onChange={event => setEligibility(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm"><option value="ALL">All</option><option value="ELIGIBLE">Eligible</option><option value="UNKNOWN">Unknown</option><option value="INELIGIBLE">Ineligible</option></select></label>
        <label className="text-xs font-semibold">Branch<select aria-label="Matching branch" value={branch} onChange={event => setBranch(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm"><option value="">All branches</option>{Object.entries(branchLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        <label className="text-xs font-semibold">Score order<select aria-label="Score order" value={order} onChange={event => setOrder(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm"><option value="DESC">Highest first</option><option value="ASC">Lowest first</option></select></label>
      </div>
      <div className="mt-4 flex items-center justify-between gap-3"><p className="text-sm text-slate-600">{candidates.length} candidates</p><button type="button" data-button-variant="outline" disabled={!candidates.length} onClick={exportReport} className="text-xs">Export CSV</button></div>
      <div className="mt-4 overflow-x-auto"><table className="w-full text-left text-sm"><caption className="sr-only">Saved opportunity candidate matching scores and eligibility</caption><thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="p-3">Student</th><th className="p-3">Branch</th><th className="p-3">Skills matched</th><th className="p-3">Missing required</th><th className="p-3">Match score</th><th className="p-3">Eligibility</th></tr></thead><tbody>{candidates.map(candidate => <tr key={candidate.studentId} className="border-b border-slate-100 align-top">
        <td className="p-3"><Link href={`/admin/students/${candidate.studentId}`} className="font-semibold text-blue-700 hover:underline">{candidate.studentName}</Link><p className="mt-1 text-xs text-slate-500">{candidate.uid}</p>{candidate.resumeAvailable && <button type="button" data-button-variant="text" disabled={busy} onClick={() => void resume(candidate.studentId)} className="mt-2 text-xs">Download resume</button>}</td>
        <td className="p-3">{candidate.branch ? branchLabels[candidate.branch] : "Unknown"}</td><td className="p-3"><p>{candidate.matchedSkills.join(", ") || "No required matches"}</p>{candidate.matchedPreferredSkills.length > 0 && <p className="mt-2 text-xs text-slate-500">Preferred: {candidate.matchedPreferredSkills.join(", ")}</p>}{candidate.contextEvidence.length > 0 && <details className="mt-2 text-xs text-slate-600"><summary className="cursor-pointer">Supporting resume evidence</summary><ul className="mt-2 list-disc pl-4">{candidate.contextEvidence.map(value => <li key={value}>{value}</li>)}</ul></details>}</td>
        <td className="p-3">{candidate.missingRequiredSkills.join(", ") || "None"}</td><td className="p-3"><span className="font-bold">{candidate.matchScore}/100</span>{candidate.strongMatch && <p className="mt-1 text-xs font-semibold text-emerald-700">Strong match</p>}</td><td className="p-3"><span className={`text-xs font-bold ${candidate.eligibility === "ELIGIBLE" ? "text-emerald-700" : candidate.eligibility === "UNKNOWN" ? "text-amber-700" : "text-red-700"}`}>{candidate.eligibility}</span><p className="mt-2 max-w-xs text-xs leading-5 text-slate-500">{candidate.eligibilityReasons.join(". ")}</p></td>
      </tr>)}</tbody></table>{candidates.length === 0 && <p className="p-6 text-center text-sm text-slate-500">No candidates match these filters.</p>}</div>
    </>}
  </section>;
}
