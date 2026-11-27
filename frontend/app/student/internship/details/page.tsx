"use client";

import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { BackButton } from "@/components/BackButton";
import { InternshipDetailsPanel } from "@/components/InternshipDetailsPanel";

export default function InternshipDetailsPage() {
  return (
    <ProtectedRoute role="STUDENT">
      <AppFrame>
        <main className="mx-auto max-w-5xl px-4 py-6 sm:px-8 sm:py-10">
          <BackButton fallbackHref="/student/dashboard" fallbackLabel="Back to Dashboard" />

          <div className="mt-5">
            <p className="text-sm font-semibold text-blue-700">Internship registration</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight">My Internship Details</h1>
            <p className="mt-2 text-slate-600">
              View and manage complete details for your primary and secondary internships.
            </p>
          </div>

          <div className="mt-8">
            <InternshipDetailsPanel />
          </div>
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
