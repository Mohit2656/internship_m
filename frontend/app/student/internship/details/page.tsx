"use client";

import axios from "axios";
import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { InternshipDetailsForm } from "@/components/InternshipDetailsForm";
import { InternshipDetailsView } from "@/components/InternshipDetailsView";
import { InternshipLocationManager } from "@/components/InternshipLocationManager";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { ApiError, api } from "@/lib/api";
import { emptyInternshipDetails, InternshipDetails, InternshipDetailsFormData, toFormData } from "@/lib/internship-details";
import { InternshipOnboarding } from "@/lib/internship-onboarding";
import type { InternshipLocation } from "@/lib/attendance";
import {
  getSelectedInternship,
  setSelectedInternship,
  getStatusBadgeClass,
  deriveInternshipStatus,
  InternshipStatusBadge,
} from "@/lib/internship-selection";

type SecondRequest = {
  id: number;
  status: "PENDING" | "APPROVED" | "REJECTED";
  rejectionReason: string | null;
  requestedAt: string;
};

export default function InternshipDetailsPage() {
  const [activeTab, setActiveTab] = useState<1 | 2>(1);

  // Slot 1 state
  const [onboarding1, setOnboarding1] = useState<InternshipOnboarding | null>(null);
  const [details1, setDetails1] = useState<InternshipDetails | null>(null);
  const [location1, setLocation1] = useState<InternshipLocation | null>(null);
  const [editing1, setEditing1] = useState(false);

  // Slot 2 state
  const [details2, setDetails2] = useState<InternshipDetails | null>(null);
  const [location2, setLocation2] = useState<InternshipLocation | null>(null);
  const [editing2, setEditing2] = useState(false);
  const [secondRequests, setSecondRequests] = useState<SecondRequest[]>([]);
  const [requestingSlot2, setRequestingSlot2] = useState(false);

  // Global UI state
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [geofenceRadiusMeters, setGeofenceRadiusMeters] = useState<number | null>(null);

  useEffect(() => {
    setActiveTab(getSelectedInternship());

    Promise.all([
      // Primary internship onboarding & details
      api.get<InternshipOnboarding>("/student/internship-onboarding?internshipNumber=1")
        .then((r) => r.data)
        .catch(() => null),
      api.get<InternshipDetails[]>("/student/internship/all")
        .then((r) => r.data)
        .catch(() => []),
      api.get<InternshipLocation>("/student/internship/location")
        .then((r) => r.data)
        .catch(() => null),
      // Second internship requests
      api.get<SecondRequest[]>("/student/second-internship-requests")
        .then((r) => r.data)
        .catch(() => []),
    ])
      .then(async ([wf1, allDetails, loc1, requests]) => {
        setOnboarding1(wf1);
        const d1 = allDetails.find((d) => d.internshipNumber === 1) ?? null;
        const d2 = allDetails.find((d) => d.internshipNumber === 2) ?? null;
        setDetails1(d1);
        setDetails2(d2);
        setLocation1(loc1);
        setGeofenceRadiusMeters(loc1?.geofenceRadiusMeters ?? null);
        setSecondRequests(requests);

        if (d2?.id) {
          try {
            const loc2 = await api.get<InternshipLocation>("/student/internship/location", {
              params: { internshipId: d2.id },
            });
            setLocation2(loc2.data);
          } catch {
            setLocation2(null);
          }
        }

        if (window.location.hash === "#edit") {
          const currentTab = getSelectedInternship();
          if (currentTab === 1 && d1) setEditing1(true);
          if (currentTab === 2 && d2) setEditing2(true);
        }
      })
      .catch(() => setError("Internship details could not be loaded."))
      .finally(() => setLoading(false));
  }, []);

  function handleTabChange(tab: 1 | 2) {
    setActiveTab(tab);
    setSelectedInternship(tab);
    setError("");
    setFieldErrors({});
  }

  const latestSecondRequest = secondRequests.length > 0 ? secondRequests[0] : null;

  const status1: InternshipStatusBadge = deriveInternshipStatus(
    details1,
    location1?.status,
    onboarding1?.workflowStatus === "NOT_SECURED" ? "Not Requested" : "Approved"
  );

  const status2: InternshipStatusBadge = deriveInternshipStatus(
    details2,
    location2?.status,
    latestSecondRequest?.status ?? null
  );

  async function submitSlot1(data: InternshipDetailsFormData) {
    setSaving(true);
    setError("");
    setFieldErrors({});
    const payload = {
      ...data,
      otherCompanyType: data.companyType === "OTHER" ? data.otherCompanyType : null,
      durationMonths: Number(data.durationMonths),
      totalWeeks: Number(data.totalWeeks),
      stipendPerMonth: Number(data.stipendPerMonth),
      fullTimeEmploymentOffered: data.fullTimeEmploymentOffered === "true",
    };
    try {
      const response = details1
        ? await api.put<InternshipDetails>("/student/internship", payload)
        : await api.post<InternshipDetails>("/student/internship", payload);
      setDetails1(response.data);
      setEditing1(false);
      window.history.replaceState(null, "", window.location.pathname);
      setOnboarding1((curr) => (curr ? { ...curr, workflowStatus: "INTERNSHIP_DETAILS_COMPLETE" } : curr));
    } catch (cause) {
      if (axios.isAxiosError<ApiError>(cause)) {
        setError(cause.response?.data?.message ?? "Internship details could not be saved.");
        setFieldErrors(cause.response?.data?.fieldErrors ?? {});
      } else setError("Internship details could not be saved.");
    } finally {
      setSaving(false);
    }
  }

  async function submitSlot2(data: InternshipDetailsFormData) {
    setSaving(true);
    setError("");
    setFieldErrors({});
    const payload = {
      ...data,
      otherCompanyType: data.companyType === "OTHER" ? data.otherCompanyType : null,
      durationMonths: Number(data.durationMonths),
      totalWeeks: Number(data.totalWeeks),
      stipendPerMonth: Number(data.stipendPerMonth),
      fullTimeEmploymentOffered: data.fullTimeEmploymentOffered === "true",
    };
    try {
      const response = details2
        ? await api.put<InternshipDetails>("/student/internship/second", payload)
        : await api.post<InternshipDetails>("/student/internship/second", payload);
      setDetails2(response.data);
      setEditing2(false);
      window.history.replaceState(null, "", window.location.pathname);
    } catch (cause) {
      if (axios.isAxiosError<ApiError>(cause)) {
        setError(cause.response?.data?.message ?? "Internship 2 details could not be saved.");
        setFieldErrors(cause.response?.data?.fieldErrors ?? {});
      } else setError("Internship 2 details could not be saved.");
    } finally {
      setSaving(false);
    }
  }

  async function raiseSecondRequest() {
    setRequestingSlot2(true);
    setError("");
    try {
      const response = await api.post<SecondRequest>("/student/second-internship-requests");
      setSecondRequests([response.data, ...secondRequests]);
    } catch (cause) {
      if (axios.isAxiosError<ApiError>(cause)) {
        setError(cause.response?.data?.message ?? "Failed to request second internship.");
      } else {
        setError("Failed to request second internship.");
      }
    } finally {
      setRequestingSlot2(false);
    }
  }

  const allowedSlot1 =
    onboarding1?.workflowStatus === "DIARY_ISSUED" ||
    onboarding1?.workflowStatus === "INTERNSHIP_DETAILS_COMPLETE" ||
    Boolean(details1);

  return (
    <ProtectedRoute role="STUDENT">
      <AppFrame>
        <main className="mx-auto max-w-5xl px-5 py-10 sm:px-8">
          <Link href="/student/dashboard" className="text-sm font-semibold text-blue-700">
            ← Back to dashboard
          </Link>

          <div className="mt-5">
            <p className="text-sm font-semibold text-blue-700">Internship registration</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight">My Internship Details</h1>
            <p className="mt-2 text-slate-600">
              View and manage complete details for your primary and secondary internships.
            </p>
          </div>

          {/* Internship Tabs */}
          <div className="mt-8 flex border-b border-slate-200">
            <button
              id="tab-details-slot-1"
              role="tab"
              aria-selected={activeTab === 1}
              onClick={() => handleTabChange(1)}
              className={`flex items-center gap-2 border-b-2 px-5 py-3 text-sm font-semibold transition-colors ${
                activeTab === 1
                  ? "border-blue-600 text-blue-700"
                  : "border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700"
              }`}
            >
              <span>Internship 1</span>
              <span className={`rounded-full border px-2 py-0.5 text-xs font-semibold ${getStatusBadgeClass(status1)}`}>
                {status1}
              </span>
            </button>
            <button
              id="tab-details-slot-2"
              role="tab"
              aria-selected={activeTab === 2}
              onClick={() => handleTabChange(2)}
              className={`flex items-center gap-2 border-b-2 px-5 py-3 text-sm font-semibold transition-colors ${
                activeTab === 2
                  ? "border-blue-600 text-blue-700"
                  : "border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700"
              }`}
            >
              <span>Internship 2</span>
              <span className={`rounded-full border px-2 py-0.5 text-xs font-semibold ${getStatusBadgeClass(status2)}`}>
                {status2}
              </span>
            </button>
          </div>

          {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}

          {loading ? (
            <p className="py-20 text-center text-sm text-slate-500">Loading internship details…</p>
          ) : activeTab === 1 ? (
            /* TAB 1: INTERNSHIP 1 */
            <div className="mt-6">
              {!allowedSlot1 ? (
                <section className="rounded-2xl border border-amber-200 bg-amber-50 p-8">
                  <h2 className="font-bold text-amber-900">Internship diary required</h2>
                  <p className="mt-2 text-sm text-amber-800">
                    Complete Phase 2.2 and confirm receipt of your internship diary before entering internship details.
                  </p>
                  <Link href="/student/internship" className="ui-button mt-5 inline-block text-sm">
                    Return to Internship Onboarding
                  </Link>
                </section>
              ) : details1 && !editing1 ? (
                <div>
                  <div className="mb-6 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
                    <div>
                      <p className="text-xs uppercase tracking-wider text-slate-500">Lifecycle Status</p>
                      <p className="mt-1 font-semibold text-slate-900">
                        {status1 === "Completed"
                          ? "Completed"
                          : status1 === "Upcoming"
                          ? "Upcoming — Starts on " + details1.startDate
                          : status1 === "Pending Approval"
                          ? "Pending Mentor Review"
                          : status1 === "Rejected"
                          ? "Workplace Rejected — Please update coordinates"
                          : "Active"}
                      </p>
                    </div>
                    <span className={`rounded-full border px-3 py-1 text-xs font-bold ${getStatusBadgeClass(status1)}`}>
                      {status1}
                    </span>
                  </div>

                  <InternshipDetailsView details={details1} />
                  <div className="mt-6">
                    <InternshipLocationManager
                      key={details1.updatedAt}
                      scope="student"
                      internshipId={details1.id}
                    />
                  </div>
                  <div className="mt-5 flex gap-3">
                    <button onClick={() => setEditing1(true)} className="ui-button text-sm">
                      Edit Details
                    </button>
                    <Link data-button-variant="outline" href="/student/dashboard" className="ui-button text-sm">
                      Back to Dashboard
                    </Link>
                  </div>
                </div>
              ) : (
                <div>
                  <InternshipDetailsForm
                    key={details1?.updatedAt ?? "new-1"}
                    initial={details1 ? toFormData(details1) : emptyInternshipDetails}
                    saving={saving}
                    fieldErrors={fieldErrors}
                    geofenceRadiusMeters={geofenceRadiusMeters}
                    onSubmit={submitSlot1}
                    onCancel={details1 ? () => setEditing1(false) : undefined}
                  />
                </div>
              )}
            </div>
          ) : (
            /* TAB 2: INTERNSHIP 2 */
            <div className="mt-6">
              {!details1 ? (
                <div className="rounded-2xl border border-amber-200 bg-amber-50 p-8 text-amber-900">
                  <h2 className="font-bold">Primary Internship Required</h2>
                  <p className="mt-2 text-sm text-amber-800">
                    Complete your primary internship (Internship 1) before requesting or setting up a second internship.
                  </p>
                </div>
              ) : details2 && !editing2 ? (
                <div>
                  <div className="mb-6 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
                    <div>
                      <p className="text-xs uppercase tracking-wider text-slate-500">Lifecycle Status</p>
                      <p className="mt-1 font-semibold text-slate-900">
                        {status2 === "Completed"
                          ? "Completed"
                          : status2 === "Upcoming"
                          ? "Upcoming — Starts on " + details2.startDate
                          : status2 === "Pending Approval"
                          ? "Pending Mentor Review"
                          : status2 === "Rejected"
                          ? "Workplace Rejected — Please update coordinates"
                          : "Active"}
                      </p>
                    </div>
                    <span className={`rounded-full border px-3 py-1 text-xs font-bold ${getStatusBadgeClass(status2)}`}>
                      {status2}
                    </span>
                  </div>

                  <InternshipDetailsView details={details2} />
                  <div className="mt-6">
                    <InternshipLocationManager
                      key={details2.updatedAt}
                      scope="student"
                      internshipId={details2.id}
                    />
                  </div>
                  <div className="mt-5 flex gap-3">
                    <button onClick={() => setEditing2(true)} className="ui-button text-sm">
                      Edit Details
                    </button>
                    <Link data-button-variant="outline" href="/student/dashboard" className="ui-button text-sm">
                      Back to Dashboard
                    </Link>
                  </div>
                </div>
              ) : editing2 && details2 ? (
                <div>
                  <InternshipDetailsForm
                    key={details2.updatedAt}
                    initial={toFormData(details2)}
                    saving={saving}
                    fieldErrors={fieldErrors}
                    geofenceRadiusMeters={geofenceRadiusMeters}
                    onSubmit={submitSlot2}
                    onCancel={() => setEditing2(false)}
                  />
                </div>
              ) : latestSecondRequest?.status === "APPROVED" ? (
                <div className="rounded-2xl border border-slate-200 bg-white p-8">
                  <div className="rounded-xl bg-emerald-50 p-4 text-sm text-emerald-900">
                    <p className="font-semibold">III Cell Approval Granted</p>
                    <p className="mt-1 text-emerald-800">
                      Your request for a second internship has been approved. Please enter your Internship 2 details below.
                    </p>
                  </div>
                  <div className="mt-6">
                    <InternshipDetailsForm
                      key="new-2"
                      initial={emptyInternshipDetails}
                      saving={saving}
                      fieldErrors={fieldErrors}
                      geofenceRadiusMeters={geofenceRadiusMeters}
                      onSubmit={submitSlot2}
                    />
                  </div>
                </div>
              ) : latestSecondRequest?.status === "PENDING" ? (
                <div className="rounded-2xl border border-amber-200 bg-amber-50 p-8 text-amber-900">
                  <h2 className="font-bold">Request Pending Approval</h2>
                  <p className="mt-2 text-sm text-amber-800">
                    Your second internship request was submitted and is currently awaiting approval from the III Cell.
                  </p>
                  <p className="mt-4 text-xs text-amber-700">
                    Submitted on: {new Date(latestSecondRequest.requestedAt).toLocaleDateString()}
                  </p>
                </div>
              ) : (
                <div className="rounded-2xl border border-slate-200 bg-white p-8">
                  {latestSecondRequest?.status === "REJECTED" && (
                    <div className="mb-6 rounded-xl bg-red-50 p-4 text-sm text-red-900">
                      <p className="font-semibold">Previous Request Rejected</p>
                      <p className="mt-1 text-red-800">
                        Reason: {latestSecondRequest.rejectionReason || "No rejection reason provided."}
                      </p>
                    </div>
                  )}
                  <h2 className="text-xl font-bold text-slate-900">Second Internship Slot</h2>
                  <p className="mt-2 text-sm text-slate-600">
                    Students who have secured or completed their primary internship can request authorization from the III Cell for a second internship.
                  </p>
                  <div className="mt-6">
                    <button
                      id="raise-request-slot-2"
                      disabled={requestingSlot2}
                      onClick={raiseSecondRequest}
                      className="ui-button text-sm"
                    >
                      {requestingSlot2 ? "Submitting Request…" : "Raise Internship Request"}
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
