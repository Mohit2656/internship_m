"use client";

import { useEffect, useState } from "react";
import axios from "axios";
import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { InternshipDetailsForm } from "@/components/InternshipDetailsForm";
import { api, ApiError } from "@/lib/api";
import {
  emptyInternshipDetails,
  InternshipDetails,
  InternshipDetailsFormData,
} from "@/lib/internship-details";
import { InternshipOnboarding } from "@/lib/internship-onboarding";

export default function SecondInternshipPage() {
  const [existing, setExisting] = useState<InternshipDetails | null>(null);
  const [onboarding, setOnboarding] = useState<InternshipOnboarding | null>(null);
  const [allowed, setAllowed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    Promise.all([
      api.get<InternshipDetails[]>("/student/internship/all"),
      api.get<any[]>("/student/second-internship-requests"),
      api.get<InternshipOnboarding>("/student/internship-onboarding?internshipNumber=2").catch(() => ({ data: null })),
    ])
      .then(([d, r, ob]) => {
        setExisting(d.data.find((x) => x.internshipNumber === 2) ?? null);
        setAllowed(r.data.some((x) => x.status === "APPROVED"));
        setOnboarding(ob.data);
      })
      .catch(() => setError("Internship 2 permission could not be loaded."))
      .finally(() => setLoading(false));
  }, []);

  async function submit(data: InternshipDetailsFormData) {
    setSaving(true);
    setError("");
    const payload = {
      ...data,
      otherCompanyType: data.companyType === "OTHER" ? data.otherCompanyType : null,
      durationMonths: Number(data.durationMonths),
      totalWeeks: Number(data.totalWeeks),
      stipendPerMonth: Number(data.stipendPerMonth),
      fullTimeEmploymentOffered: data.fullTimeEmploymentOffered === "true",
    };
    try {
      const r = await api.post<InternshipDetails>("/student/internship/second", payload);
      setExisting(r.data);
    } catch (c) {
      if (axios.isAxiosError<ApiError>(c)) {
        setError(c.response?.data?.message ?? "Internship 2 could not be saved.");
        setFieldErrors(c.response?.data?.fieldErrors ?? {});
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <ProtectedRoute role="STUDENT">
      <AppFrame>
        <main className="mx-auto max-w-5xl px-5 py-10">
          <Link href="/student/dashboard" className="text-sm font-semibold text-blue-700">
            ← Back to dashboard
          </Link>
          <p className="mt-4 text-sm font-semibold text-blue-700">Internship 2</p>
          <h1 className="mt-2 text-3xl font-bold">Second Internship</h1>
          {loading ? (
            <p className="mt-8">Loading…</p>
          ) : existing ? (
            <section className="mt-8 rounded-2xl border bg-white p-7">
              <h2 className="text-xl font-bold">{existing.companyName}</h2>
              <p className="mt-2">{existing.companyAddress}</p>
              <p className="mt-2 text-sm text-slate-600">
                {existing.startDate} to {existing.endDate}
              </p>
            </section>
          ) : !allowed ? (
            <p className="mt-8 rounded-xl bg-amber-50 p-5 text-amber-900">
              III Cell approval is required.{" "}
              <Link className="underline" href="/student/dashboard">
                Return to dashboard
              </Link>
              .
            </p>
          ) : onboarding?.workflowStatus !== "DIARY_ISSUED" && onboarding?.workflowStatus !== "INTERNSHIP_DETAILS_COMPLETE" ? (
            <section className="mt-8 rounded-2xl border border-amber-200 bg-amber-50 p-8 text-amber-900">
              <h2 className="text-lg font-bold">Initial Internship Information Required</h2>
              <p className="mt-2 text-sm text-amber-800">
                Complete Step 1 (Source, Required Documents, and Diary Confirmation) before entering company details for Internship 2.
              </p>
              <div className="mt-6">
                <Link href="/student/internship?tab=2" className="ui-button text-sm">
                  Go to Initial Information Form
                </Link>
              </div>
            </section>
          ) : (
            <div className="mt-8">
              <InternshipDetailsForm
                initial={emptyInternshipDetails}
                saving={saving}
                fieldErrors={fieldErrors}
                onSubmit={submit}
              />
            </div>
          )}
          {error && (
            <p role="alert" className="mt-4 text-red-700">
              {error}
            </p>
          )}
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
