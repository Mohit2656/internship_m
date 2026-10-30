"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { AcademicSessionPicker } from "@/components/AcademicSessionPicker";
import { CompletionStatistics } from "@/components/CompletionStatistics";
import { useAcademicSessions } from "@/lib/academic-sessions";
import { api } from "@/lib/api";
import { completionLabel, CompletionPage, completionReportFields, completionReportFilters } from "@/lib/internship-completion";
import { formatDateTime } from "@/lib/monthly-progress";

export default function InternshipCompletionsPage() {
  const session = useAcademicSessions();
  const [result, setResult] = useState<CompletionPage | null>(null);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("COMPLETED");
  const [sort, setSort] = useState("completionDate");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!session.selected) { setResult(null); return; }
    const controller = new AbortController(); setLoading(true); setResult(null); setError("");
    const timer = window.setTimeout(() => { api.get<CompletionPage>("/admin/internship-completions", { params: { sessionId: session.selected, search, status, sort, page }, signal: controller.signal }).then(r => setResult(r.data)).catch(() => { if (!controller.signal.aborted) setError("Internship completions could not be loaded."); }).finally(() => { if (!controller.signal.aborted) setLoading(false); }); }, 200);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [session.selected, search, status, sort, page]);
  async function download(format: "XLSX" | "PDF") {
    if (!session.selected || !result || exporting || loading) return; setExporting(true); setError("");
    try {
      const { data } = await api.post<Blob>("/admin/dashboard/reports/export", { fields: completionReportFields, filters: completionReportFilters(session.selected, status, search), format }, { responseType: "blob", timeout: 180_000 });
      const url = URL.createObjectURL(data), link = document.createElement("a"); link.href = url; link.download = `internship-completions-${session.sessions.find(s => s.id === session.selected)?.name}.${format.toLowerCase()}`; document.body.appendChild(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
    } catch { setError("Completion report could not be exported."); } finally { setExporting(false); }
  }
  return <ProtectedRoute role="ADMIN"><AppFrame><main className="mx-auto max-w-7xl px-5 py-10 sm:px-8"><Link href="/admin/dashboard" className="text-sm font-semibold text-blue-700">← Dashboard</Link><h1 className="mt-4 text-3xl font-bold">Internship Completions</h1><div className="mt-5"><AcademicSessionPicker sessions={session.sessions} selected={session.selected} onChange={id => { session.setSelected(id); setPage(0); }} /></div>
    {(error || session.error) && <p role="alert" className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error || session.error}</p>}
    {result && session.selected && <CompletionStatistics stats={result.stats} sessionId={session.selected} />}
    <div className="mt-6 flex flex-wrap gap-3 rounded-2xl border border-slate-200 bg-white p-5"><input aria-label="Search completions" maxLength={254} placeholder="Student name, UID or company" value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} className="min-w-56 flex-1 rounded-xl border border-slate-300 px-3 py-2 text-sm" /><select aria-label="Completion status" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }} className="rounded-xl border border-slate-300 px-3 py-2 text-sm"><option value="">All internships</option>{["COMPLETED", "IN_PROGRESS", "NOT_STARTED", "COMPLETION_PENDING"].map(s => <option key={s} value={s}>{completionLabel(s)}</option>)}</select><select aria-label="Sort completions" value={sort} onChange={e => { setSort(e.target.value); setPage(0); }} className="rounded-xl border border-slate-300 px-3 py-2 text-sm"><option value="completionDate">Completion date (newest)</option><option value="name">Student name</option><option value="company">Company</option><option value="startDate">Start date (newest)</option></select><button data-button-variant="outline" disabled={!result || loading || exporting} onClick={() => download("XLSX")} className="text-sm">Export Excel</button><button data-button-variant="outline" disabled={!result || loading || exporting} onClick={() => download("PDF")} className="text-sm">Export PDF</button></div>
    <div aria-busy={loading} className="mt-5 overflow-x-auto rounded-2xl border border-slate-200 bg-white"><table className="w-full whitespace-nowrap text-left text-sm"><thead className="bg-slate-50 text-xs text-slate-500"><tr>{["Student", "UID", "Company", "Mentor", "Start date", "End date", "Completion date", "IIIC reviewer", "Status"].map(h => <th key={h} className="p-4">{h}</th>)}</tr></thead><tbody>{result?.content.map(row => <tr key={row.studentId} className="border-t border-slate-100"><td className="p-4"><Link href={`/admin/students/${row.studentId}#internship-completion`} className="font-semibold text-blue-700">{row.student_name}</Link></td><td className="p-4">{row.uid ?? "—"}</td><td className="p-4">{row.company}</td><td className="p-4">{row.mentor ?? "Unassigned"}</td><td className="p-4">{row.startDate}</td><td className="p-4">{row.endDate}</td><td className="p-4">{row.completionDate ? formatDateTime(row.completionDate) : "Pending"}</td><td className="p-4">{row.reviewer ?? "Pending"}</td><td className="p-4">{completionLabel(row.status)}</td></tr>)}{!result?.content.length && <tr><td colSpan={9} className="p-8 text-center text-slate-500">{loading ? "Loading internships…" : session.selected ? "No internships match these filters." : "Select an academic session."}</td></tr>}</tbody></table></div>
    {result && <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm"><p>{result.totalElements} internships · Page {result.page + 1} of {Math.max(1, Math.ceil(result.totalElements / 30))}</p><div className="flex gap-2"><button data-button-variant="outline" disabled={loading || page === 0} onClick={() => setPage(p => p - 1)}>Previous</button><button data-button-variant="outline" disabled={loading || (page + 1) * 30 >= result.totalElements} onClick={() => setPage(p => p + 1)}>Next</button></div></div>}
  </main></AppFrame></ProtectedRoute>;
}
