"use client";

import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { AdminDualInternshipPanel } from "@/components/DualInternshipPanel";

export default function AdminSecondInternshipRequestsPage() {
  return (
    <ProtectedRoute role="ADMIN">
      <AppFrame>
        <main className="mx-auto max-w-6xl px-5 py-8 sm:px-8 sm:py-10">
          <Link href="/admin/dashboard" className="text-sm font-semibold text-blue-700 hover:underline">
            ← Back to dashboard
          </Link>
          <div className="mt-4">
            <h1 className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Second Internship Requests
            </h1>
            <p className="mt-2 text-slate-600 dark:text-slate-400">
              Review and manage student applications for a second internship. Approval from the III Cell is required before students can register Internship 2.
            </p>
          </div>

          <div className="mt-6">
            <AdminDualInternshipPanel />
          </div>
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
