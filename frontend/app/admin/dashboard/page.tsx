"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { Breakdown, DashboardSummary } from "@/lib/dashboard";
import { DashboardReportExport } from "@/components/DashboardReportExport";
import { AttendanceAbsenceOverview } from "@/components/AttendanceAbsenceOverview";
import { AcademicSessionPicker } from "@/components/AcademicSessionPicker";
import { CompletionStatistics } from "@/components/CompletionStatistics";
import { useAcademicSessions } from "@/lib/academic-sessions";
import { STUDENT_ACCOUNTS_CHANGED } from "@/lib/student-deletion";
import { CompletionPage } from "@/lib/internship-completion";
import { RecentNotifications } from "@/components/NotificationsContent";
import { AdminDualInternshipPanel } from "@/components/DualInternshipPanel";
import { DashboardPage, DashboardMetric as Metric, DashboardSection as Section } from "@/components/DashboardLayout";

const currency = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const percentage = (value: number, total: number) => total ? `${Math.round((value / total) * 100)}%` : "0%";

function BreakdownList({ items }: { items: Breakdown[] }) {
  const max = Math.max(...items.map(item => item.count), 1);
  return <div className="space-y-3">{items.map(item => <div key={item.value}><div className="flex justify-between gap-4 text-sm"><span className="font-medium text-slate-700">{item.label}</span><span className="font-semibold text-slate-900">{item.count}</span></div><div className="mt-1.5 h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-blue-600" style={{ width: `${(item.count / max) * 100}%` }}/></div></div>)}</div>;
}

export default function AdminDashboard() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null); const [error, setError] = useState("");
  const session = useAcademicSessions();
  const [accountRefresh, setAccountRefresh] = useState(0);
  useEffect(() => {
    const refresh = () => setAccountRefresh(value => value + 1);
    const storage = (event: StorageEvent) => { if (event.key === STUDENT_ACCOUNTS_CHANGED) refresh(); };
    window.addEventListener(STUDENT_ACCOUNTS_CHANGED, refresh); window.addEventListener("focus", refresh); window.addEventListener("storage", storage);
    return () => { window.removeEventListener(STUDENT_ACCOUNTS_CHANGED, refresh); window.removeEventListener("focus", refresh); window.removeEventListener("storage", storage); };
  }, []);
  const [completions, setCompletions] = useState<CompletionPage | null>(null);
  useEffect(() => {
    setSummary(null); setCompletions(null); setError("");
    if (!session.selected) return;
    const controller = new AbortController();
    Promise.all([api.get<DashboardSummary>("/admin/dashboard/summary", { params: { sessionId: session.selected }, signal: controller.signal }), api.get<CompletionPage>("/admin/internship-completions", { params: { sessionId: session.selected }, signal: controller.signal })])
      .then(([dashboard, completion]) => { setSummary(dashboard.data); setCompletions(completion.data); })
      .catch(() => { if (!controller.signal.aborted) setError("Unable to load statistics for this academic session."); });
    return () => controller.abort();
  }, [session.selected, accountRefresh]);
  const progress = summary ? [["Profile complete", summary.students.profileComplete], ["Internship secured", summary.internshipProgress.secured], ["Documents complete", summary.internshipProgress.documentsComplete], ["Diary issued", summary.internshipProgress.diaryIssued], ["Registration complete", summary.internshipProgress.detailsComplete]] as const : [];
  const attention = summary ? [["Profile Incomplete", "PROFILE_INCOMPLETE", summary.attention.profileIncomplete], ["Internship Not Secured", "INTERNSHIP_NOT_SECURED", summary.attention.internshipNotSecured], ["Documents Pending", "DOCUMENTS_PENDING", summary.attention.documentsPending], ["Diary Pending", "DIARY_PENDING", summary.attention.diaryPending], ["Internship Details Pending", "INTERNSHIP_DETAILS_PENDING", summary.attention.internshipDetailsPending]] as const : [];
  return <ProtectedRoute role="ADMIN"><AppFrame><DashboardPage
    eyebrow="III Cell workspace"
    title="Internship Monitoring Dashboard"
    description="Internship progress for the selected academic session."
    actions={<Link data-button-variant="outline" href={session.selected ? `/admin/students?academicSessionId=${session.selected}` : "/admin/students"} className="ui-button text-sm">Open student directory</Link>}
  >
    <div className="mt-6 flex min-w-0 flex-wrap items-center justify-between gap-4"><AcademicSessionPicker sessions={session.sessions} selected={session.selected} onChange={session.setSelected} /><DashboardReportExport key={session.selected} role="ADMIN" initialFilters={session.selected ? { academicSessionId: String(session.selected) } : {}} /></div>
    {session.error && <p role="alert" className="mt-4 text-sm text-red-700">{session.error}</p>}
    {completions && session.selected && completions.sessionId === session.selected && <CompletionStatistics stats={completions.stats} sessionId={session.selected} />}
    <RecentNotifications role="ADMIN" />
    <div id="second-internship-requests"><AdminDualInternshipPanel /></div>
    <Section title="Weekly internship progress" description="Monitor generated weeks, missing submissions, mentor decisions and attendance summaries." className="mt-6"><Link href="/admin/weekly-progress" className="ui-button mt-4 inline-block text-sm">Monitor Weekly Progress</Link></Section>
    {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}{!summary && !error && session.selected && <p role="status" className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 sm:p-7">Loading dashboard…</p>}
    {summary && <div className="mt-6 space-y-6">
      <Section title="Student overview"><div className="mt-5 grid grid-cols-[repeat(auto-fit,minmax(min(100%,12rem),1fr))] gap-4"><Metric label="Total students" value={summary.students.total} tone="blue"/><Metric label="Profile complete" value={summary.students.profileComplete} tone="emerald"/><Metric label="Profile incomplete" value={summary.students.profileIncomplete} tone="amber"/></div></Section>
      <Section title="Internship progress"><div className="mt-5 grid grid-cols-[repeat(auto-fit,minmax(min(100%,12rem),1fr))] gap-4"><Metric label="Internship secured" value={summary.internshipProgress.secured} tone="blue"/><Metric label="Not secured" value={summary.internshipProgress.notSecured} tone="amber"/><Metric label="Documents pending" value={summary.internshipProgress.documentsPending} tone="amber"/><Metric label="Documents complete" value={summary.internshipProgress.documentsComplete} tone="emerald"/><Metric label="Diary issued" value={summary.internshipProgress.diaryIssued} tone="emerald"/><Metric label="Registration complete" value={summary.internshipProgress.detailsComplete} tone="emerald"/></div></Section>
      <Section title="Lifecycle progress"><div className="mt-5 space-y-4">{progress.map(([label, count]) => <div key={label}><div className="flex justify-between gap-5 text-sm"><span className="font-medium text-slate-700">{label}</span><span className="font-semibold text-slate-900">{count} <span className="font-normal text-slate-500">({percentage(count, summary.students.total)})</span></span></div><div className="mt-1.5 h-2.5 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-blue-600" style={{ width: `${summary.students.total ? (count / summary.students.total) * 100 : 0}%` }}/></div></div>)}</div></Section>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,16rem),1fr))] gap-6"><Section title="Internship source"><div className="mt-5"><BreakdownList items={summary.sources}/></div></Section><Section title="Company type"><div className="mt-5"><BreakdownList items={summary.companyTypes}/></div></Section><Section title="Internship mode"><div className="mt-5"><BreakdownList items={summary.internshipModes}/></div></Section></div>
      <Section title="Internship outcomes"><div className="mt-5 grid grid-cols-[repeat(auto-fit,minmax(min(100%,12rem),1fr))] gap-4"><Metric label="Paid internships" value={summary.stipend.paid} tone="emerald"/><Metric label="Unpaid internships" value={summary.stipend.unpaid}/><Metric label="Average monthly stipend (paid)" value={summary.stipend.averagePaidMonthlyStipend === null ? "—" : currency.format(summary.stipend.averagePaidMonthlyStipend)} tone="blue"/><Metric label="Full-time opportunity — yes" value={summary.fullTimeEmployment.yes} tone="emerald"/><Metric label="Full-time opportunity — no" value={summary.fullTimeEmployment.no}/></div><p className="mt-4 text-xs text-slate-500">Full-time opportunity reflects student-entered company information and does not guarantee placement.</p></Section>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,18rem),1fr))] gap-6"><Section title="Top companies by student count"><div className="mt-5 overflow-x-auto"><table className="w-full text-sm"><thead className="border-b text-left text-xs uppercase tracking-wide text-slate-500"><tr><th className="pb-3">Company</th><th className="pb-3 text-right">Students</th></tr></thead><tbody>{summary.topCompanies.length ? summary.topCompanies.map(company => <tr key={company.companyName} className="border-b border-slate-100 last:border-0"><td className="py-3 font-medium text-slate-700">{company.companyName}</td><td className="py-3 text-right font-semibold">{company.students}</td></tr>) : <tr><td colSpan={2} className="py-6 text-center text-slate-500">No internship companies recorded yet.</td></tr>}</tbody></table></div></Section><Section title="Students requiring attention"><div className="mt-4 divide-y divide-slate-100">{attention.map(([label, filter, count]) => <Link key={filter} href={`/admin/students?attention=${filter}&academicSessionId=${session.selected}`} className="flex items-center justify-between gap-4 py-3.5 hover:text-blue-700"><span className="font-medium">{label}</span><span className="rounded-full bg-amber-50 px-3 py-1 text-sm font-bold text-amber-800">{count}</span></Link>)}</div><p className="mt-4 text-xs text-slate-500">Each category opens a server-filtered student list.</p></Section></div>
    </div>}
    <AttendanceAbsenceOverview role="ADMIN" />
  </DashboardPage></AppFrame></ProtectedRoute>;
}
