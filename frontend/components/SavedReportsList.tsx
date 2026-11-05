"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";

export type SavedReportDto = {
  id: number;
  title: string;
  description: string | null;
  scope: string;
  configurationJson: string;
  createdBy: number | null;
  creatorEmail: string | null;
  createdAt: string;
  updatedAt: string;
};

interface SavedReportsListProps {
  onLoadReport: (saved: SavedReportDto) => void;
  activeReportId?: number | null;
}

export function SavedReportsList({ onLoadReport, activeReportId }: SavedReportsListProps) {
  const [reports, setReports] = useState<SavedReportDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [deletingId, setDeletingId] = useState<number | null>(null);

  async function loadReports() {
    setLoading(true);
    setError("");
    try {
      const { data } = await api.get<SavedReportDto[]>("/admin/reports/saved");
      setReports(data);
    } catch {
      setError("Failed to load saved report configurations.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadReports();
  }, []);

  async function handleDelete(id: number, title: string) {
    if (!window.confirm(`Are you sure you want to delete "${title}"?`)) return;
    setDeletingId(id);
    try {
      await api.delete(`/admin/reports/saved/${id}`);
      setReports(prev => prev.filter(r => r.id !== id));
    } catch {
      alert("Failed to delete saved report.");
    } finally {
      setDeletingId(null);
    }
  }

  const filtered = reports.filter(r =>
    r.title.toLowerCase().includes(search.toLowerCase()) ||
    (r.description && r.description.toLowerCase().includes(search.toLowerCase())) ||
    (r.creatorEmail && r.creatorEmail.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-xs">
      <div className="flex flex-wrap items-center justify-between gap-3 pb-4 border-b border-slate-100 dark:border-slate-800">
        <div>
          <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100">Saved Report Configurations</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Re-run saved report setups against the latest student records without re-building filters.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <input
            type="text"
            placeholder="Search saved reports…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-48 sm:w-64 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-3 py-1.5 text-xs text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            type="button"
            onClick={loadReports}
            title="Refresh"
            className="rounded-xl border border-slate-200 dark:border-slate-700 p-2 text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800 transition"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>

      {error && (
        <div className="mt-4 rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900/60 p-3 text-xs text-red-700 dark:text-red-300">
          {error}
        </div>
      )}

      {loading ? (
        <div className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
          Loading saved configurations…
        </div>
      ) : filtered.length === 0 ? (
        <div className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
          {reports.length === 0
            ? "No saved reports found. Design a report in Custom Report Builder and click 'Save Configuration'."
            : "No saved reports match your search."}
        </div>
      ) : (
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map(report => {
            const isActive = activeReportId === report.id;
            return (
              <div
                key={report.id}
                className={`relative rounded-xl border p-4 transition-all flex flex-col justify-between ${
                  isActive
                    ? "border-blue-500 bg-blue-50/50 dark:bg-blue-950/20 shadow-xs"
                    : "border-slate-200 dark:border-slate-700/80 bg-white dark:bg-slate-800/60 hover:border-slate-300 dark:hover:border-slate-600"
                }`}
              >
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100 line-clamp-1" title={report.title}>
                      {report.title}
                    </h3>
                    <span className="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold bg-blue-100 text-blue-700 dark:bg-blue-900/50 dark:text-blue-300">
                      {report.scope === "STUDENT" ? "Student-wise" : "Internship-wise"}
                    </span>
                  </div>
                  {report.description && (
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400 line-clamp-2">
                      {report.description}
                    </p>
                  )}
                  <div className="mt-3 text-[11px] text-slate-400 dark:text-slate-500">
                    <span>By: {report.creatorEmail || "Administrator"}</span>
                  </div>
                </div>

                <div className="mt-4 flex items-center justify-between gap-2 pt-3 border-t border-slate-100 dark:border-slate-700/60">
                  <button
                    type="button"
                    onClick={() => onLoadReport(report)}
                    className="rounded-lg bg-blue-600 hover:bg-blue-700 px-3 py-1.5 text-xs font-semibold text-white transition shadow-xs"
                  >
                    Load in Builder →
                  </button>
                  <button
                    type="button"
                    disabled={deletingId === report.id}
                    onClick={() => handleDelete(report.id, report.title)}
                    className="text-xs text-red-600 hover:text-red-700 dark:text-red-400 hover:underline disabled:opacity-50"
                  >
                    Delete
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
