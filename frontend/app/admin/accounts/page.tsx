"use client";

import Link from "next/link";
import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { StudentAccountsContent } from "@/components/StudentAccountsContent";
import { MentorAccountsContent } from "@/components/MentorAccountsContent";

function AdminAccountsContent() {
  const searchParams = useSearchParams();
  const tabParam = searchParams?.get("tab");
  const [accountType, setAccountType] = useState<"students" | "mentors">(
    tabParam === "mentors" ? "mentors" : "students"
  );

  useEffect(() => {
    if (tabParam === "mentors") {
      setAccountType("mentors");
    } else if (tabParam === "students") {
      setAccountType("students");
    }
  }, [tabParam]);

  const handleTabChange = (type: "students" | "mentors") => {
    setAccountType(type);
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
        <p className="text-sm font-semibold text-blue-700">III Cell workspace</p>
        <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950 dark:text-slate-100">
          Account Management
        </h1>
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
          Manage institutional student and faculty mentor accounts, import cohorts, and maintain faculty assignments.
        </p>
      </div>

      {/* Internal Tabs: Students & Mentors */}
      <div className="mt-8">
        <div
          className="inline-flex items-center gap-1 rounded-lg bg-slate-100/90 p-1 border border-slate-200/80 shadow-xs"
          role="tablist"
          aria-label="Account management types"
        >
          <button
            type="button"
            role="tab"
            id="tab-students"
            aria-selected={accountType === "students"}
            aria-controls="panel-students"
            onClick={() => handleTabChange("students")}
            className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              accountType === "students"
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
            }`}
          >
            Students
          </button>
          <button
            type="button"
            role="tab"
            id="tab-mentors"
            aria-selected={accountType === "mentors"}
            aria-controls="panel-mentors"
            onClick={() => handleTabChange("mentors")}
            className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              accountType === "mentors"
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
            }`}
          >
            Mentors
          </button>
        </div>

        <div className="mt-6">
          {accountType === "students" ? (
            <div id="panel-students" role="tabpanel" aria-labelledby="tab-students">
              <StudentAccountsContent embedded={true} />
            </div>
          ) : (
            <div id="panel-mentors" role="tabpanel" aria-labelledby="tab-mentors">
              <MentorAccountsContent embedded={true} />
            </div>
          )}
        </div>
      </div>
    </main>
  );
}

export default function AdminAccountsPage() {
  return (
    <ProtectedRoute role="ADMIN">
      <AppFrame>
        <Suspense fallback={<p className="py-20 text-center text-sm text-slate-500">Loading accounts…</p>}>
          <AdminAccountsContent />
        </Suspense>
      </AppFrame>
    </ProtectedRoute>
  );
}
