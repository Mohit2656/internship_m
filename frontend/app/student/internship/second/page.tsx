"use client";

import { useEffect, useState } from "react";
import axios from "axios";
import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { BackButton } from "@/components/BackButton";
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
      companyName: data.companyName ? data.companyName.trim() : null,
      companyType: data.companyType || null,
      otherCompanyType: data.companyType === "OTHER" ? (data.otherCompanyType?.trim() || null) : null,
      companyAddress: data.companyAddress ? data.companyAddress.trim() : null,
      companyPhone: data.companyPhone ? data.companyPhone.trim() : null,
      industrySupervisorName: data.industrySupervisorName ? data.industrySupervisorName.trim() : null,
      industrySupervisorDesignation: data.industrySupervisorDesignation ? data.industrySupervisorDesignation.trim() : null,
      industrySupervisorContact: data.industrySupervisorContact ? data.industrySupervisorContact.trim() : null,
      durationMonths: data.durationMonths ? Number(data.durationMonths) : null,
      startDate: data.startDate || null,
      endDate: data.endDate || null,
      totalWeeks: data.totalWeeks ? Number(data.totalWeeks) : null,
      internshipMode: data.internshipMode || null,
      stipendPerMonth: data.stipendPerMonth !== "" ? Number(data.stipendPerMonth) : null,
      fullTimeEmploymentOffered: data.fullTimeEmploymentOffered === "true" ? true : data.fullTimeEmploymentOffered === "false" ? false : null,
      workplace: data.workplace || null,
    };
    try {
      const r = await api.post<InternshipDetails>("/student/internship/second", payload);
      setExisting(r.data);
      api.get<InternshipOnboarding>("/student/internship-onboarding?internshipNumber=2").then(ob => setOnboarding(ob.data)).catch(() => {});
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
        <main className="mx-auto max-w-5xl px-4 py-6 sm:px-8 sm:py-10">
          <BackButton fallbackHref="/student/dashboard" fallbackLabel="Back to Dashboard" />
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
