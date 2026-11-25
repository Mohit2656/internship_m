"use client";

import Link from "next/link";
import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { WeeklyProgress } from "@/components/WeeklyProgress";
import { MonthlyProgressReportList } from "@/components/MonthlyProgressReportList";

function AdminProgressContent() {
  const searchParams = useSearchParams();
  const tabParam = searchParams?.get("tab");
  const [reportType, setReportType] = useState<"weekly" | "monthly">(
    tabParam === "monthly" ? "monthly" : "weekly"
  );

  useEffect(() => {
    if (tabParam === "monthly") {
      setReportType("monthly");
    } else if (tabParam === "weekly") {
      setReportType("weekly");
    }
  }, [tabParam]);

  const handleTabChange = (type: "weekly" | "monthly") => {
    setReportType(type);
    if (typeof window !== "undefined") {
      const url = new URL(window.location.href);
      url.searchParams.set("tab", type);
      window.history.replaceState(null, "", url.toString());
    }
  };

  return (
    <main className="mx-auto max-w-7xl px-5 py-10 sm:px-8">
      <Link href="/admin/dashboard" className="text-sm font-semibold text-blue-700 hover:underline">
        ← Back to dashboard
      </Link>
      <div className="mt-4">
        <p className="text-sm font-semibold text-blue-700">III Cell monitoring</p>
        <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950 dark:text-slate-100">
          Student Progress
        </h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Monitor student weekly submission logs, mentor reviews, and monthly institutional progress reports.
        </p>
      </div>

      {/* Internal Tabs: Weekly & Monthly */}
      <div className="mt-8">
        <div
          className="inline-flex items-center gap-1 rounded-lg bg-slate-100/90 p-1 border border-slate-200/80 shadow-xs"
          role="tablist"
          aria-label="Progress report types"
        >
          <button
            type="button"
            role="tab"
            id="tab-weekly"
            aria-selected={reportType === "weekly"}
            aria-controls="panel-weekly"
            onClick={() => handleTabChange("weekly")}
            className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              reportType === "weekly"
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
            }`}
          >
            Weekly
          </button>
          <button
            type="button"
            role="tab"
            id="tab-monthly"
            aria-selected={reportType === "monthly"}
            aria-controls="panel-monthly"
            onClick={() => handleTabChange("monthly")}
            className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              reportType === "monthly"
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
            }`}
          >
            Monthly
          </button>
        </div>

        <div className="mt-6">
          {reportType === "weekly" ? (
            <div id="panel-weekly" role="tabpanel" aria-labelledby="tab-weekly">
              <WeeklyProgress scope="admin" />
            </div>
          ) : (
            <div id="panel-monthly" role="tabpanel" aria-labelledby="tab-monthly">
              <MonthlyProgressReportList scope="admin" />
            </div>
          )}
        </div>
      </div>
    </main>
  );
}

export default function AdminProgressPage() {
  return (
    <ProtectedRoute role="ADMIN">
      <AppFrame>
        <Suspense fallback={<p className="py-20 text-center text-sm text-slate-500">Loading progress reports…</p>}>
          <AdminProgressContent />
        </Suspense>
      </AppFrame>
    </ProtectedRoute>
  );
}
