"use client";

import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { RecentNotifications, NotificationsContent } from "@/components/NotificationsContent";

export default function AdminRecentActivityPage() {
  return (
    <ProtectedRoute role="ADMIN">
      <AppFrame>
        <main className="mx-auto max-w-6xl px-5 py-8 sm:px-8 sm:py-10">
          <Link href="/admin/dashboard" className="text-sm font-semibold text-blue-700 hover:underline">
            ← Back to dashboard
          </Link>
          <div className="mt-4">
            <h1 className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Recent Activity
            </h1>
            <p className="mt-2 text-slate-600 dark:text-slate-400">
              Monitor institutional events, student submissions, resume analyses, and mentor evaluations.
            </p>
          </div>

          <div className="mt-6">
            <RecentNotifications role="ADMIN" />
          </div>

          <div className="mt-8 border-t border-slate-200 pt-8 dark:border-slate-800">
            <h2 className="text-xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Activity History & Notifications
            </h2>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Complete historical record of updates across students, mentors, and the III Cell.
            </p>
            <div className="mt-4">
              <NotificationsContent role="ADMIN" />
            </div>
          </div>
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
