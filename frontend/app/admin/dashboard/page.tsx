"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { DashboardSummary } from "@/lib/dashboard";
import { DashboardReportExport } from "@/components/DashboardReportExport";
import { AcademicSessionPicker } from "@/components/AcademicSessionPicker";
import { useAcademicSessions } from "@/lib/academic-sessions";
import { STUDENT_ACCOUNTS_CHANGED } from "@/lib/student-deletion";
import { CompletionPage } from "@/lib/internship-completion";
import { DashboardStudentList } from "@/components/DashboardStudentList";

const currency = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const percentage = (value: number, total: number) => total ? `${Math.round((value / total) * 100)}%` : "0%";

export interface DashboardSelection {
  sectionId: string;
  categoryKey: string;
  label: string;
  count: number;
  params: Record<string, string | number | boolean | undefined>;
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 sm:p-6 shadow-sm">
      <h2 className="text-lg font-bold text-slate-900">{title}</h2>
      {children}
    </section>
  );
}

function InteractiveSummaryCard({
  label,
  value,
  tone = "slate",
  isSelected = false,
  onClick,
}: {
  label: string;
  value: number | string;
  tone?: "slate" | "blue" | "amber" | "emerald";
  isSelected?: boolean;
  onClick?: () => void;
}) {
  const tones = {
    slate: "border-slate-200 bg-slate-50/70 hover:border-slate-300 hover:bg-slate-100/70",
    blue: "border-blue-200 bg-blue-50/40 hover:border-blue-300 hover:bg-blue-50/70",
    amber: "border-amber-200 bg-amber-50/50 hover:border-amber-300 hover:bg-amber-50/80",
    emerald: "border-emerald-200 bg-emerald-50/50 hover:border-emerald-300 hover:bg-emerald-50/80",
  };

  const activeStyles = "ring-2 ring-blue-600 border-blue-500 bg-blue-50/90 shadow-sm";

  if (!onClick) {
    return (
      <article className={`rounded-2xl border p-5 ${tones[tone]}`}>
        <p className="text-xs font-bold uppercase tracking-[0.14em] text-slate-500">{label}</p>
        <p className="mt-2 text-3xl font-bold tracking-tight text-slate-900">{value}</p>
      </article>
    );
  }

  return (
    <button
      type="button"
      role="button"
      aria-pressed={isSelected}
      onClick={onClick}
      className={`overview-summary-card border ${isSelected ? activeStyles : tones[tone]}`}
    >
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-bold uppercase tracking-[0.14em] text-slate-500">{label}</p>
        {isSelected && (
          <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-blue-700">
            <span className="h-1.5 w-1.5 rounded-full bg-blue-600" />
            Active
          </span>
        )}
      </div>
      <p className="mt-2 text-3xl font-bold tracking-tight text-slate-900">{value}</p>
    </button>
  );
}

export default function AdminDashboard() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState("");
  const session = useAcademicSessions();
  const [accountRefresh, setAccountRefresh] = useState(0);

  useEffect(() => {
    const refresh = () => setAccountRefresh(value => value + 1);
    const storage = (event: StorageEvent) => { if (event.key === STUDENT_ACCOUNTS_CHANGED) refresh(); };
    window.addEventListener(STUDENT_ACCOUNTS_CHANGED, refresh);
    window.addEventListener("focus", refresh);
    window.addEventListener("storage", storage);
    return () => {
      window.removeEventListener(STUDENT_ACCOUNTS_CHANGED, refresh);
      window.removeEventListener("focus", refresh);
      window.removeEventListener("storage", storage);
    };
  }, []);

  const [completions, setCompletions] = useState<CompletionPage | null>(null);

  useEffect(() => {
    setSummary(null);
    setCompletions(null);
    setError("");
    if (!session.selected) return;
    const controller = new AbortController();
    Promise.all([
      api.get<DashboardSummary>("/admin/dashboard/summary", { params: { sessionId: session.selected }, signal: controller.signal }),
      api.get<CompletionPage>("/admin/internship-completions", { params: { sessionId: session.selected }, signal: controller.signal }),
    ])
      .then(([dashboard, completion]) => {
        setSummary(dashboard.data);
        setCompletions(completion.data);
      })
      .catch(() => {
        if (!controller.signal.aborted) setError("Unable to load statistics for this academic session.");
      });
    return () => controller.abort();
  }, [session.selected, accountRefresh]);

  const [activeSelection, setActiveSelection] = useState<DashboardSelection | null>(() => {
    if (typeof window !== "undefined") {
      const urlParams = new URLSearchParams(window.location.search);
      const cat = urlParams.get("category");
      if (cat === "TOTAL") return { sectionId: "student-overview", categoryKey: "TOTAL", label: "Total students", count: 0, params: {} };
      if (cat === "PROFILE_COMPLETE") return { sectionId: "student-overview", categoryKey: "PROFILE_COMPLETE", label: "Profile complete", count: 0, params: { profileStatus: "COMPLETE" } };
      if (cat === "PROFILE_INCOMPLETE") return { sectionId: "student-overview", categoryKey: "PROFILE_INCOMPLETE", label: "Profile incomplete", count: 0, params: { profileStatus: "INCOMPLETE" } };
    }
    return null;
  });

  const handleSelectCategory = (selection: DashboardSelection) => {
    const isSame = activeSelection?.sectionId === selection.sectionId && activeSelection?.categoryKey === selection.categoryKey;
    const next = isSame ? null : selection;
    setActiveSelection(next);
    if (typeof window !== "undefined") {
      const url = new URL(window.location.href);
      if (next) {
        url.searchParams.set("category", next.categoryKey);
        url.searchParams.set("section", next.sectionId);
      } else {
        url.searchParams.delete("category");
        url.searchParams.delete("section");
      }
      window.history.replaceState(null, "", url.toString());
    }
  };

  const studentOverviewCards = summary
    ? [
        { key: "TOTAL", label: "Total students", count: summary.students.total, tone: "blue" as const, params: {} },
        { key: "PROFILE_COMPLETE", label: "Profile complete", count: summary.students.profileComplete, tone: "emerald" as const, params: { profileStatus: "COMPLETE" } },
        { key: "PROFILE_INCOMPLETE", label: "Profile incomplete", count: summary.students.profileIncomplete, tone: "amber" as const, params: { profileStatus: "INCOMPLETE" } },
      ]
    : [];

  const internshipProgressCards = summary
    ? [
        { key: "SECURED", label: "Internship secured", count: summary.internshipProgress.secured, tone: "blue" as const, params: { internshipSecured: true } },
        { key: "NOT_SECURED", label: "Not secured", count: summary.internshipProgress.notSecured, tone: "amber" as const, params: { internshipSecured: false } },
        { key: "DOCUMENTS_PENDING", label: "Documents pending", count: summary.internshipProgress.documentsPending, tone: "amber" as const, params: { attention: "DOCUMENTS_PENDING" } },
        { key: "DOCUMENTS_COMPLETE", label: "Documents complete", count: summary.internshipProgress.documentsComplete, tone: "emerald" as const, params: { internshipStatus: "DOCUMENTS_COMPLETE" } },
        { key: "DIARY_ISSUED", label: "Diary issued", count: summary.internshipProgress.diaryIssued, tone: "emerald" as const, params: { internshipStatus: "DIARY_ISSUED" } },
        { key: "REGISTRATION_COMPLETE", label: "Registration complete", count: summary.internshipProgress.detailsComplete, tone: "emerald" as const, params: { internshipStatus: "INTERNSHIP_DETAILS_COMPLETE" } },
      ]
    : [];

  const attentionItems = summary
    ? [
        { key: "PROFILE_INCOMPLETE", label: "Profile Incomplete", count: summary.attention.profileIncomplete, params: { attention: "PROFILE_INCOMPLETE" } },
        { key: "INTERNSHIP_NOT_SECURED", label: "Internship Not Secured", count: summary.attention.internshipNotSecured, params: { attention: "INTERNSHIP_NOT_SECURED" } },
        { key: "DOCUMENTS_PENDING", label: "Documents Pending", count: summary.attention.documentsPending, params: { attention: "DOCUMENTS_PENDING" } },
        { key: "DIARY_PENDING", label: "Diary Pending", count: summary.attention.diaryPending, params: { attention: "DIARY_PENDING" } },
        { key: "INTERNSHIP_DETAILS_PENDING", label: "Internship Details Pending", count: summary.attention.internshipDetailsPending, params: { attention: "INTERNSHIP_DETAILS_PENDING" } },
      ]
    : [];

  const lifecycleItems = summary
    ? [
        { key: "PROFILE_COMPLETE", label: "Profile complete", count: summary.students.profileComplete, params: { profileStatus: "COMPLETE" } },
        { key: "INTERNSHIP_SECURED", label: "Internship secured", count: summary.internshipProgress.secured, params: { internshipSecured: true } },
        { key: "DOCUMENTS_COMPLETE", label: "Documents complete", count: summary.internshipProgress.documentsComplete, params: { internshipStatus: "DOCUMENTS_COMPLETE" } },
        { key: "DIARY_ISSUED", label: "Diary issued", count: summary.internshipProgress.diaryIssued, params: { internshipStatus: "DIARY_ISSUED" } },
        { key: "REGISTRATION_COMPLETE", label: "Registration complete", count: summary.internshipProgress.detailsComplete, params: { internshipStatus: "INTERNSHIP_DETAILS_COMPLETE" } },
      ]
    : [];

  const outcomeCards = summary
    ? [
        { key: "PAID", label: "Paid internships", value: summary.stipend.paid, count: summary.stipend.paid, tone: "emerald" as const, params: { paid: true } },
        { key: "UNPAID", label: "Unpaid internships", value: summary.stipend.unpaid, count: summary.stipend.unpaid, tone: "slate" as const, params: { paid: false } },
        { key: "AVG_STIPEND", label: "Average monthly stipend (paid)", value: summary.stipend.averagePaidMonthlyStipend === null ? "—" : currency.format(summary.stipend.averagePaidMonthlyStipend), count: 0, tone: "blue" as const, params: null },
        { key: "FTE_YES", label: "Full-time opportunity — yes", value: summary.fullTimeEmployment.yes, count: summary.fullTimeEmployment.yes, tone: "emerald" as const, params: { fullTimeEmploymentOffered: true } },
        { key: "FTE_NO", label: "Full-time opportunity — no", value: summary.fullTimeEmployment.no, count: summary.fullTimeEmployment.no, tone: "slate" as const, params: { fullTimeEmploymentOffered: false } },
      ]
    : [];

  return (
    <ProtectedRoute role="ADMIN">
      <AppFrame>
        <main className="mx-auto max-w-7xl px-5 py-8 sm:px-8 sm:py-10">
          <p className="text-sm font-semibold text-blue-700">III Cell workspace</p>
          <div className="mt-2">
            <h1 className="text-3xl font-bold tracking-tight">Internship Monitoring Dashboard</h1>
            <p className="mt-2 text-slate-600">Internship progress for the selected academic session.</p>
          </div>

          <div className="mt-5 flex flex-wrap items-center justify-between gap-4">
            <AcademicSessionPicker sessions={session.sessions} selected={session.selected} onChange={session.setSelected} />
            <DashboardReportExport
              key={session.selected}
              role="ADMIN"
              initialFilters={session.selected ? { academicSessionId: String(session.selected) } : {}}
            />
          </div>

          {session.error && <p role="alert" className="mt-4 text-sm text-red-700">{session.error}</p>}
          {error && <p className="mt-7 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}

          {!summary && !error && session.selected && (
            <p className="mt-8 rounded-2xl border border-slate-200 bg-white p-8 text-slate-500">Loading dashboard…</p>
          )}

          {!session.selected && (
            <p className="mt-8 rounded-2xl border border-slate-200 bg-white p-8 text-slate-500">Select an academic session to view dashboard metrics.</p>
          )}

          {summary && (
            <div className="mt-8 space-y-6">
              {/* SECTION 1: Student Overview */}
              <Section title="Student overview">
                <div className="mt-5 grid gap-4 sm:grid-cols-3">
                  {studentOverviewCards.map((card) => {
                    const isSelected = activeSelection?.sectionId === "student-overview" && activeSelection?.categoryKey === card.key;
                    return (
                      <InteractiveSummaryCard
                        key={card.key}
                        label={card.label}
                        value={card.count}
                        tone={card.tone}
                        isSelected={isSelected}
                        onClick={() => handleSelectCategory({ sectionId: "student-overview", categoryKey: card.key, label: card.label, count: card.count, params: card.params })}
                      />
                    );
                  })}
                </div>
                {activeSelection?.sectionId === "student-overview" && (
                  <DashboardStudentList
                    title={activeSelection.label}
                    count={activeSelection.count}
                    params={activeSelection.params}
                    sessionId={session.selected}
                    categoryKey={activeSelection.categoryKey}
                    onClose={() => handleSelectCategory(activeSelection)}
                  />
                )}
              </Section>

              {/* SECTION 2: Internship Progress */}
              <Section title="Internship progress">
                <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {internshipProgressCards.map((card) => {
                    const isSelected = activeSelection?.sectionId === "internship-progress" && activeSelection?.categoryKey === card.key;
                    return (
                      <InteractiveSummaryCard
                        key={card.key}
                        label={card.label}
                        value={card.count}
                        tone={card.tone}
                        isSelected={isSelected}
                        onClick={() => handleSelectCategory({ sectionId: "internship-progress", categoryKey: card.key, label: card.label, count: card.count, params: card.params })}
                      />
                    );
                  })}
                </div>
                <p className="mt-4 text-xs text-slate-500">Metrics reflect unique students enrolled in the selected academic session.</p>
                {activeSelection?.sectionId === "internship-progress" && (
                  <DashboardStudentList
                    title={activeSelection.label}
                    count={activeSelection.count}
                    params={activeSelection.params}
                    sessionId={session.selected}
                    categoryKey={activeSelection.categoryKey}
                    onClose={() => handleSelectCategory(activeSelection)}
                  />
                )}
              </Section>

              {/* SECTION 3: Students Requiring Attention */}
              <Section title="Students requiring attention">
                <p className="mt-1 text-xs text-slate-500">Select any actionable issue below to inspect the affected students.</p>
                <div className="mt-4 divide-y divide-slate-100">
                  {attentionItems.map((item) => {
                    const isSelected = activeSelection?.sectionId === "students-requiring-attention" && activeSelection?.categoryKey === item.key;
                    return (
                      <button
                        key={item.key}
                        type="button"
                        role="button"
                        aria-pressed={isSelected}
                        onClick={() => handleSelectCategory({ sectionId: "students-requiring-attention", categoryKey: item.key, label: item.label, count: item.count, params: item.params })}
                        className={`dashboard-interactive-btn flex items-center justify-between gap-4 py-3.5 px-3 rounded-xl transition-all ${
                          isSelected ? "ring-2 ring-blue-600 border-blue-500 bg-blue-50/90 shadow-sm" : "hover:bg-slate-50/80"
                        }`}
                      >
                        <span className="font-medium text-slate-800">{item.label}</span>
                        <div className="flex items-center gap-2">
                          {isSelected && <span className="text-[11px] font-semibold text-blue-700">● Selected</span>}
                          <span className="rounded-full bg-amber-50 px-3 py-1 text-sm font-bold text-amber-800">{item.count}</span>
                        </div>
                      </button>
                    );
                  })}
                </div>
                {activeSelection?.sectionId === "students-requiring-attention" && (
                  <DashboardStudentList
                    title={activeSelection.label}
                    count={activeSelection.count}
                    params={activeSelection.params}
                    sessionId={session.selected}
                    categoryKey={activeSelection.categoryKey}
                    onClose={() => handleSelectCategory(activeSelection)}
                  />
                )}
              </Section>

              {/* SECTION 4: Lifecycle Progress */}
              <Section title="Lifecycle progress">
                <p className="mt-1 text-xs text-slate-500">Click any workflow stage to inspect matching students.</p>
                <div className="mt-5 space-y-3">
                  {lifecycleItems.map((item) => {
                    const isSelected = activeSelection?.sectionId === "lifecycle-progress" && activeSelection?.categoryKey === item.key;
                    return (
                      <button
                        key={item.key}
                        type="button"
                        role="button"
                        aria-pressed={isSelected}
                        onClick={() => handleSelectCategory({ sectionId: "lifecycle-progress", categoryKey: item.key, label: item.label, count: item.count, params: item.params })}
                        className={`dashboard-interactive-btn w-full p-3 rounded-xl border transition-all text-left ${
                          isSelected ? "ring-2 ring-blue-600 border-blue-500 bg-blue-50/90 shadow-sm" : "border-slate-200 bg-white hover:bg-slate-50/80"
                        }`}
                      >
                        <div className="flex justify-between gap-5 text-sm">
                          <span className="font-medium text-slate-700">{item.label}</span>
                          <span className="font-semibold text-slate-900">
                            {item.count}{" "}
                            <span className="font-normal text-slate-500">
                              ({percentage(item.count, summary.students.total)})
                            </span>
                          </span>
                        </div>
                        <div className="mt-1.5 h-2.5 overflow-hidden rounded-full bg-slate-100">
                          <div
                            className="h-full rounded-full bg-blue-600"
                            style={{ width: `${summary.students.total ? (item.count / summary.students.total) * 100 : 0}%` }}
                          />
                        </div>
                      </button>
                    );
                  })}
                </div>
                {activeSelection?.sectionId === "lifecycle-progress" && (
                  <DashboardStudentList
                    title={activeSelection.label}
                    count={activeSelection.count}
                    params={activeSelection.params}
                    sessionId={session.selected}
                    categoryKey={activeSelection.categoryKey}
                    onClose={() => handleSelectCategory(activeSelection)}
                  />
                )}
              </Section>

              {/* SECTIONS 5, 6, 7: Internship Source, Company Type, Internship Mode */}
              <div className="grid gap-6 lg:grid-cols-3">
                {/* SECTION 5: Internship Source */}
                <Section title="Internship source">
                  <p className="mt-1 text-xs text-slate-500">Click a source category to view enrolled students.</p>
                  <div className="mt-4 space-y-2">
                    {summary.sources.map((item) => {
                      const isSelected = activeSelection?.sectionId === "internship-source" && activeSelection?.categoryKey === item.value;
                      const max = Math.max(...summary.sources.map(s => s.count), 1);
                      return (
                        <button
                          key={item.value}
                          type="button"
                          role="button"
                          aria-pressed={isSelected}
                          onClick={() => handleSelectCategory({ sectionId: "internship-source", categoryKey: item.value, label: `Source: ${item.label}`, count: item.count, params: { internshipSource: item.value } })}
                          className={`dashboard-interactive-btn w-full p-3 rounded-xl border transition-all text-left ${
                            isSelected ? "ring-2 ring-blue-600 border-blue-500 bg-blue-50/90 shadow-sm" : "border-slate-200 bg-white hover:bg-slate-50/80"
                          }`}
                        >
                          <div className="flex justify-between gap-4 text-sm">
                            <span className="font-medium text-slate-700">{item.label}</span>
                            <span className="font-semibold text-slate-900">{item.count}</span>
                          </div>
                          <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-slate-100">
                            <div className="h-full rounded-full bg-blue-600" style={{ width: `${(item.count / max) * 100}%` }} />
                          </div>
                        </button>
                      );
                    })}
                  </div>
                  {activeSelection?.sectionId === "internship-source" && (
                    <DashboardStudentList
                      title={activeSelection.label}
                      count={activeSelection.count}
                      params={activeSelection.params}
                      sessionId={session.selected}
                      categoryKey={activeSelection.categoryKey}
                      onClose={() => handleSelectCategory(activeSelection)}
                    />
                  )}
                </Section>

                {/* SECTION 6: Company Type */}
                <Section title="Company type">
                  <p className="mt-1 text-xs text-slate-500">Click a company classification to view students.</p>
                  <div className="mt-4 space-y-2">
                    {summary.companyTypes.map((item) => {
                      const isSelected = activeSelection?.sectionId === "company-type" && activeSelection?.categoryKey === item.value;
                      const max = Math.max(...summary.companyTypes.map(s => s.count), 1);
                      return (
                        <button
                          key={item.value}
                          type="button"
                          role="button"
                          aria-pressed={isSelected}
                          onClick={() => handleSelectCategory({ sectionId: "company-type", categoryKey: item.value, label: `Company Type: ${item.label}`, count: item.count, params: { companyType: item.value } })}
                          className={`dashboard-interactive-btn w-full p-3 rounded-xl border transition-all text-left ${
                            isSelected ? "ring-2 ring-blue-600 border-blue-500 bg-blue-50/90 shadow-sm" : "border-slate-200 bg-white hover:bg-slate-50/80"
                          }`}
                        >
                          <div className="flex justify-between gap-4 text-sm">
                            <span className="font-medium text-slate-700">{item.label}</span>
                            <span className="font-semibold text-slate-900">{item.count}</span>
                          </div>
                          <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-slate-100">
                            <div className="h-full rounded-full bg-blue-600" style={{ width: `${(item.count / max) * 100}%` }} />
                          </div>
                        </button>
                      );
                    })}
                  </div>
                  {activeSelection?.sectionId === "company-type" && (
                    <DashboardStudentList
                      title={activeSelection.label}
                      count={activeSelection.count}
                      params={activeSelection.params}
                      sessionId={session.selected}
                      categoryKey={activeSelection.categoryKey}
                      onClose={() => handleSelectCategory(activeSelection)}
                    />
                  )}
                </Section>

                {/* SECTION 7: Internship Mode */}
                <Section title="Internship mode">
                  <p className="mt-1 text-xs text-slate-500">Click an internship mode to view students.</p>
                  <div className="mt-4 space-y-2">
                    {summary.internshipModes.map((item) => {
                      const isSelected = activeSelection?.sectionId === "internship-mode" && activeSelection?.categoryKey === item.value;
                      const max = Math.max(...summary.internshipModes.map(s => s.count), 1);
                      return (
                        <button
                          key={item.value}
                          type="button"
                          role="button"
                          aria-pressed={isSelected}
                          onClick={() => handleSelectCategory({ sectionId: "internship-mode", categoryKey: item.value, label: `Mode: ${item.label}`, count: item.count, params: { internshipMode: item.value } })}
                          className={`dashboard-interactive-btn w-full p-3 rounded-xl border transition-all text-left ${
                            isSelected ? "ring-2 ring-blue-600 border-blue-500 bg-blue-50/90 shadow-sm" : "border-slate-200 bg-white hover:bg-slate-50/80"
                          }`}
                        >
                          <div className="flex justify-between gap-4 text-sm">
                            <span className="font-medium text-slate-700">{item.label}</span>
                            <span className="font-semibold text-slate-900">{item.count}</span>
                          </div>
                          <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-slate-100">
                            <div className="h-full rounded-full bg-blue-600" style={{ width: `${(item.count / max) * 100}%` }} />
                          </div>
                        </button>
                      );
                    })}
                  </div>
                  {activeSelection?.sectionId === "internship-mode" && (
                    <DashboardStudentList
                      title={activeSelection.label}
                      count={activeSelection.count}
                      params={activeSelection.params}
                      sessionId={session.selected}
                      categoryKey={activeSelection.categoryKey}
                      onClose={() => handleSelectCategory(activeSelection)}
                    />
                  )}
                </Section>
              </div>

              {/* SECTION 8: Internship Outcomes */}
              <Section title="Internship outcomes">
                <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
                  {outcomeCards.map((card) => {
                    const isSelected = activeSelection?.sectionId === "internship-outcomes" && activeSelection?.categoryKey === card.key;
                    return (
                      <InteractiveSummaryCard
                        key={card.key}
                        label={card.label}
                        value={card.value}
                        tone={card.tone}
                        isSelected={isSelected}
                        onClick={card.params ? () => handleSelectCategory({ sectionId: "internship-outcomes", categoryKey: card.key, label: card.label, count: card.count, params: card.params! }) : undefined}
                      />
                    );
                  })}
                </div>
                <p className="mt-4 text-xs text-slate-500">Full-time opportunity reflects student-entered company information and does not guarantee placement.</p>
                {activeSelection?.sectionId === "internship-outcomes" && (
                  <DashboardStudentList
                    title={activeSelection.label}
                    count={activeSelection.count}
                    params={activeSelection.params}
                    sessionId={session.selected}
                    categoryKey={activeSelection.categoryKey}
                    onClose={() => handleSelectCategory(activeSelection)}
                  />
                )}
              </Section>

              {/* SECTION 9: Top Companies by Student Count */}
              <Section title="Top companies by student count">
                <p className="mt-1 text-xs text-slate-500">Click a company to inspect enrolled students.</p>
                <div className="mt-5 overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="border-b text-left text-xs uppercase tracking-wide text-slate-500">
                      <tr>
                        <th className="pb-3">Company</th>
                        <th className="pb-3 text-right">Students</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.topCompanies.length ? (
                        summary.topCompanies.map((company) => {
                          const isSelected = activeSelection?.sectionId === "top-companies" && activeSelection?.categoryKey === company.companyName;
                          return (
                            <tr
                              key={company.companyName}
                              role="button"
                              tabIndex={0}
                              aria-pressed={isSelected}
                              onClick={() => handleSelectCategory({ sectionId: "top-companies", categoryKey: company.companyName, label: `Company: ${company.companyName}`, count: company.students, params: { companyName: company.companyName } })}
                              onKeyDown={(e) => {
                                if (e.key === "Enter" || e.key === " ") {
                                  e.preventDefault();
                                  handleSelectCategory({ sectionId: "top-companies", categoryKey: company.companyName, label: `Company: ${company.companyName}`, count: company.students, params: { companyName: company.companyName } });
                                }
                              }}
                              className={`border-b border-slate-100 last:border-0 cursor-pointer transition-colors ${
                                isSelected ? "bg-blue-50/90 font-semibold" : "hover:bg-slate-50/80"
                              }`}
                            >
                              <td className="py-3 font-medium text-slate-800">
                                <div className="flex items-center gap-2">
                                  {isSelected && <span className="text-[11px] font-semibold text-blue-700">●</span>}
                                  <span>{company.companyName}</span>
                                </div>
                              </td>
                              <td className="py-3 text-right font-semibold text-slate-900">{company.students}</td>
                            </tr>
                          );
                        })
                      ) : (
                        <tr>
                          <td colSpan={2} className="py-6 text-center text-slate-500">
                            No internship companies recorded yet.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
                {activeSelection?.sectionId === "top-companies" && (
                  <DashboardStudentList
                    title={activeSelection.label}
                    count={activeSelection.count}
                    params={activeSelection.params}
                    sessionId={session.selected}
                    categoryKey={activeSelection.categoryKey}
                    onClose={() => handleSelectCategory(activeSelection)}
                  />
                )}
              </Section>
            </div>
          )}
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
