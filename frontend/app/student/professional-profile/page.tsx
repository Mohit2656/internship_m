"use client";

import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { BackButton } from "@/components/BackButton";
import { ProfessionalProfilePanel } from "@/components/ProfessionalProfilePanel";

export default function ProfessionalProfilePage() {
  return (
    <ProtectedRoute role="STUDENT">
      <AppFrame>
        <main className="mx-auto max-w-5xl px-4 py-6 sm:px-8 sm:py-10">
          <div>
            <BackButton fallbackHref="/student/dashboard" fallbackLabel="Back to Dashboard" fixedFallback />
          </div>
          <div className="mt-5">
            <p className="text-sm font-semibold text-blue-700">Career workspace</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight">Professional Profile</h1>
            <p className="mt-2 text-slate-600">
              Add a career summary and keep one current resume ready for future opportunity matching.
            </p>
          </div>
          <div className="mt-8">
            <ProfessionalProfilePanel />
          </div>
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
