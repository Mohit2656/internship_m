"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { WorkflowBadge } from "@/components/WorkflowBadge";
import { api } from "@/lib/api";
import { InternshipDetails } from "@/lib/internship-details";
import { InternshipOnboarding } from "@/lib/internship-onboarding";
import { getSelectedInternship, setSelectedInternship } from "@/lib/internship-selection";

type StudentProfile = {
  id: number;
  studentName: string;
  collegeUid: string;
  department: string;
  academicYear: string;
  phone: string;
  address: string;
};

type SecondInternshipRequest = {
  id: number;
  status: "PENDING" | "APPROVED" | "REJECTED";
  requestedAt: string;
  rejectionReason: string | null;
};

type StudentMentorInfo = {
  internship1MentorName: string | null;
  internship2MentorName: string | null;
};

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

export interface InternshipSlotsSectionProps {
  activeTab?: 1 | 2;
  onTabChange?: (tab: 1 | 2) => void;
  onOnboardingUpdated?: () => void;
}

export function InternshipSlotsSection({
  activeTab: controlledActiveTab,
  onTabChange,
  onOnboardingUpdated,
}: InternshipSlotsSectionProps) {
  const [internalActiveTab, setInternalActiveTab] = useState<1 | 2>(1);
  const activeTab = controlledActiveTab ?? internalActiveTab;

  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [onboarding, setOnboarding] = useState<InternshipOnboarding | null>(null);
  const [secondOnboarding, setSecondOnboarding] = useState<InternshipOnboarding | null>(null);
  const [details, setDetails] = useState<InternshipDetails | null>(null);
  const [secondDetails, setSecondDetails] = useState<InternshipDetails | null>(null);
  const [secondRequests, setSecondRequests] = useState<SecondInternshipRequest[]>([]);
  const [mentorInfo, setMentorInfo] = useState<StudentMentorInfo | null>(null);

  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [secondStarting, setSecondStarting] = useState(false);
  const [error, setError] = useState("");
  const [secondError, setSecondError] = useState("");

  const refreshData = useCallback(async () => {
    try {
      const [
        studentProfile,
        internship,
        secondOb,
        internshipDetails,
        allDetails,
        requests,
        mentor,
      ] = await Promise.all([
        api.get<StudentProfile>("/student/profile").then((r) => r.data).catch(() => null),
        api.get<InternshipOnboarding>("/student/internship-onboarding").then((r) => r.data).catch(() => null),
        api
          .get<InternshipOnboarding>("/student/internship-onboarding", { params: { internshipNumber: 2 } })
          .then((r) => r.data)
          .catch(() => null),
        api.get<InternshipDetails>("/student/internship").then((r) => r.data).catch(() => null),
        api.get<InternshipDetails[]>("/student/internship/all").then((r) => r.data).catch(() => []),
        api.get<SecondInternshipRequest[]>("/student/second-internship-requests").then((r) => r.data).catch(() => []),
        api.get<StudentMentorInfo>("/student/mentor").then((r) => r.data).catch(() => null),
      ]);

      setProfile(studentProfile);
      setOnboarding(internship);
      setSecondOnboarding(secondOb);
      setDetails(internshipDetails);
      const slot2 = Array.isArray(allDetails) ? allDetails.find((x) => x.internshipNumber === 2) ?? null : null;
      setSecondDetails(slot2);
      setSecondRequests(Array.isArray(requests) ? requests : []);
      setMentorInfo(mentor);
    } catch {
      setError("Internship slots could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (controlledActiveTab === undefined) {
      setInternalActiveTab(getSelectedInternship());
    }
    void refreshData();
  }, [controlledActiveTab, refreshData]);

  function handleSelectTab(tab: 1 | 2) {
    if (controlledActiveTab === undefined) {
      setInternalActiveTab(tab);
    }
    setSelectedInternship(tab);
    onTabChange?.(tab);
  }

  async function secureInternship() {
    setStarting(true);
    setError("");
    try {
      const { data } = await api.post<InternshipOnboarding>("/student/internship-onboarding/secure");
      setOnboarding(data);
      onOnboardingUpdated?.();
      await refreshData();
    } catch {
      setError("Complete your student profile before registering an internship.");
    } finally {
      setStarting(false);
    }
  }

  async function raiseSecondRequest() {
    setSecondStarting(true);
    setSecondError("");
    try {
      await api.post("/student/second-internship-requests");
      const { data } = await api.get<SecondInternshipRequest[]>("/student/second-internship-requests");
      setSecondRequests(Array.isArray(data) ? data : []);
      onOnboardingUpdated?.();
      await refreshData();
    } catch (cause: any) {
      setSecondError(cause.response?.data?.message ?? "Request could not be submitted.");
    } finally {
      setSecondStarting(false);
    }
  }

  const latestSecondRequest = secondRequests[0] ?? null;
  const todayStr = new Date().toISOString().slice(0, 10);
  const isInternshipStarted = Boolean(details && details.startDate && details.startDate <= todayStr);

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-7">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">
            {isInternshipStarted ? "Active Internship" : "Internship Registration"}
          </p>
          <h2 className="mt-1 text-2xl font-bold text-slate-950">Internship Slots &amp; Requests</h2>
          <p className="mt-1 text-sm text-slate-600">
            Switch between your internship slots to view details or raise requests.
          </p>
        </div>
      </div>

      {error && (
        <p role="alert" className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">
          {error}
        </p>
      )}

      <div className="mt-6">
        <div className="inline-flex items-center gap-1 rounded-lg bg-slate-100/90 p-1 border border-slate-200/80 shadow-xs" role="tablist" aria-label="Internship slots">
          <button
            type="button"
            role="tab"
            id="tab-slot-1"
            aria-selected={activeTab === 1}
            aria-controls="panel-slot-1"
            onClick={() => handleSelectTab(1)}
            className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              activeTab === 1
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
            }`}
          >
            Internship 1 {details ? `(${details.companyName})` : "(Slot 1)"}
          </button>
          <button
            type="button"
            role="tab"
            id="tab-slot-2"
            aria-selected={activeTab === 2}
            aria-controls="panel-slot-2"
            onClick={() => handleSelectTab(2)}
            className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              activeTab === 2
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
            }`}
          >
            Internship 2 {secondDetails ? `(${secondDetails.companyName})` : "(Slot 2)"}
          </button>
        </div>

        {/* TAB 1: FIRST INTERNSHIP */}
        {activeTab === 1 && (
          <div id="panel-slot-1" role="tabpanel" aria-labelledby="tab-slot-1" className="mt-6">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">First Internship Slot</p>
                <h3 className="mt-1 text-xl font-bold text-slate-900">
                  {loading
                    ? "Checking status…"
                    : details
                    ? details.companyName
                    : onboarding?.workflowStatus === "DIARY_ISSUED"
                    ? "Internship Details Pending"
                    : onboarding?.workflowStatus === "NOT_SECURED"
                    ? "Not Secured"
                    : "Internship Onboarding"}
                </h3>
              </div>
              {!loading && onboarding && <WorkflowBadge status={onboarding.workflowStatus} />}
            </div>

            {!loading && !profile ? (
              <div className="mt-5 rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
                <p className="font-semibold">Profile required</p>
                <p className="mt-1 text-slate-600">Complete your student profile before registering an internship.</p>
                <Link href="/student/profile" className="ui-button mt-4 inline-block text-sm">
                  Complete Profile
                </Link>
              </div>
            ) : !loading && onboarding ? (
              <>
                {details ? (
                  <div className="mt-5 grid grid-cols-2 gap-3 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-5">
                    <div>
                      <p className="text-xs text-slate-500">Company</p>
                      <p className="mt-1 font-semibold">{details.companyName}</p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">Mode</p>
                      <p className="mt-1 font-semibold">{details.internshipModeLabel}</p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">Start Date</p>
                      <p className="mt-1 font-semibold">{details.startDate}</p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">End Date</p>
                      <p className="mt-1 font-semibold">{details.endDate}</p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">Faculty Mentor</p>
                      <p className="mt-1 font-semibold">{mentorInfo?.internship1MentorName ?? "Not assigned yet"}</p>
                    </div>
                  </div>
                ) : (
                  <div className="mt-5 grid grid-cols-2 gap-3 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-4">
                    <div>
                      <p className="text-xs text-slate-500">Source</p>
                      <p className="mt-1 font-semibold">{onboarding.sourceLabel ?? "—"}</p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">Documents</p>
                      <p className="mt-1 font-semibold">
                        {onboarding.documentsCompleted} / {onboarding.documentsRequired}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">Diary</p>
                      <p className="mt-1 font-semibold">{onboarding.diaryIssued ? "Received" : "Not issued"}</p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">Faculty Mentor</p>
                      <p className="mt-1 font-semibold">{mentorInfo?.internship1MentorName ?? "Not assigned yet"}</p>
                    </div>
                  </div>
                )}

                <div className="mt-5 flex flex-wrap gap-3">
                  {details ? (
                    <>
                      <Link href="/student/internship/details" className="ui-button text-sm">
                        View Details
                      </Link>
                      <Link data-button-variant="outline" href="/student/internship/details#edit" className="ui-button text-sm">
                        Edit Details
                      </Link>
                    </>
                  ) : onboarding.workflowStatus === "NOT_SECURED" ? (
                    <button
                      id="raise-request-slot-1"
                      disabled={starting}
                      onClick={secureInternship}
                      className="ui-button text-sm"
                    >
                      {starting ? "Starting…" : "Raise Internship Request"}
                    </button>
                  ) : onboarding.workflowStatus === "DIARY_ISSUED" ? (
                    <Link href="/student/internship/details" className="ui-button text-sm">
                      Complete Internship Details
                    </Link>
                  ) : (
                    <Link href="/student/internship" className="ui-button text-sm">
                      Continue Internship Registration
                    </Link>
                  )}
                </div>
              </>
            ) : null}
          </div>
        )}

        {/* TAB 2: SECOND INTERNSHIP */}
        {activeTab === 2 && (
          <div id="panel-slot-2" role="tabpanel" aria-labelledby="tab-slot-2" className="mt-6">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">Second Internship Slot</p>
                <h3 className="mt-1 text-xl font-bold text-slate-900">
                  {secondDetails
                    ? secondDetails.companyName
                    : latestSecondRequest
                    ? `Request: ${latestSecondRequest.status.replace("_", " ")}`
                    : "Second Internship"}
                </h3>
              </div>
              {latestSecondRequest && (
                <span
                  className={`rounded-full px-3 py-1 text-xs font-bold ${
                    latestSecondRequest.status === "APPROVED"
                      ? "bg-emerald-100 text-emerald-800"
                      : latestSecondRequest.status === "REJECTED"
                      ? "bg-red-100 text-red-800"
                      : "bg-amber-100 text-amber-800"
                  }`}
                >
                  {latestSecondRequest.status}
                </span>
              )}
            </div>

            <p className="mt-2 text-sm text-slate-600">
              Institutional III Cell approval is required before a second internship slot can be registered.
            </p>

            {secondError && (
              <p role="alert" className="mt-3 text-sm text-red-700">
                {secondError}
              </p>
            )}

            {!details ? (
              <div className="mt-4 rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
                <p className="font-semibold">Prerequisite required</p>
                <p className="mt-1 text-amber-800">
                  Complete your primary internship (Internship 1) before requesting permission for a second internship.
                </p>
              </div>
            ) : secondDetails ? (
              <>
                <div className="mt-5 grid grid-cols-2 gap-3 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-5">
                  <div>
                    <p className="text-xs text-slate-500">Company</p>
                    <p className="mt-1 font-semibold">{secondDetails.companyName}</p>
                  </div>
                  <div>
                    <p className="text-xs text-slate-500">Mode</p>
                    <p className="mt-1 font-semibold">{secondDetails.internshipModeLabel}</p>
                  </div>
                  <div>
                    <p className="text-xs text-slate-500">Start Date</p>
                    <p className="mt-1 font-semibold">{secondDetails.startDate}</p>
                  </div>
                  <div>
                    <p className="text-xs text-slate-500">End Date</p>
                    <p className="mt-1 font-semibold">{secondDetails.endDate}</p>
                  </div>
                  <div>
                    <p className="text-xs text-slate-500">Faculty Mentor</p>
                    <p className="mt-1 font-semibold">{mentorInfo?.internship2MentorName ?? "Not assigned yet"}</p>
                  </div>
                </div>
                <div className="mt-5 flex gap-3">
                  <Link
                    href="/student/internship/details?tab=2"
                    onClick={() => setSelectedInternship(2)}
                    className="ui-button text-sm"
                  >
                    View Details
                  </Link>
                  <Link
                    data-button-variant="outline"
                    href="/student/internship/details?tab=2#edit"
                    onClick={() => setSelectedInternship(2)}
                    className="ui-button text-sm"
                  >
                    Edit Details
                  </Link>
                </div>
              </>
            ) : latestSecondRequest?.status === "APPROVED" ? (
              <div className="mt-4 rounded-xl bg-emerald-50 p-4 text-sm text-emerald-900">
                <p className="font-semibold">Request Approved</p>
                <p className="mt-1 text-emerald-800">
                  The III Cell has approved your request for a second internship. You may now complete your second internship registration.
                </p>
                {secondOnboarding && (
                  <div className="mt-4 grid grid-cols-2 gap-3 rounded-xl bg-white p-4 text-sm sm:grid-cols-4">
                    <div>
                      <p className="text-xs text-slate-500">Source</p>
                      <p className="mt-1 font-semibold">{secondOnboarding.sourceLabel ?? "—"}</p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">Documents</p>
                      <p className="mt-1 font-semibold">
                        {secondOnboarding.documentsCompleted} / {secondOnboarding.documentsRequired}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">Diary</p>
                      <p className="mt-1 font-semibold">{secondOnboarding.diaryIssued ? "Received" : "Not issued"}</p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500">Faculty Mentor</p>
                      <p className="mt-1 font-semibold">{mentorInfo?.internship2MentorName ?? "Not assigned yet"}</p>
                    </div>
                  </div>
                )}
                <div className="mt-4">
                  {secondOnboarding?.workflowStatus === "DIARY_ISSUED" ? (
                    <Link
                      href="/student/internship/details?tab=2"
                      onClick={() => setSelectedInternship(2)}
                      className="ui-button text-sm"
                    >
                      Complete Internship Details
                    </Link>
                  ) : (
                    <Link
                      href="/student/internship?tab=2"
                      onClick={() => setSelectedInternship(2)}
                      className="ui-button text-sm"
                    >
                      Fill Details
                    </Link>
                  )}
                </div>
              </div>
            ) : latestSecondRequest?.status === "PENDING" ? (
              <div className="mt-4 rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
                <p className="font-semibold">Request Submitted</p>
                <p className="mt-1 text-amber-800">
                  Your second internship request was submitted on {formatDateTime(latestSecondRequest.requestedAt)} and is currently awaiting III Cell decision.
                </p>
              </div>
            ) : (
              <div className="mt-4">
                {latestSecondRequest?.status === "REJECTED" && (
                  <div className="mb-4 rounded-xl bg-red-50 p-4 text-sm text-red-900">
                    <p className="font-semibold">Previous Request Rejected</p>
                    <p className="mt-1 text-red-800">
                      Reason: {latestSecondRequest.rejectionReason || "No rejection reason provided."}
                    </p>
                  </div>
                )}
                <p className="text-sm text-slate-600">
                  You are eligible to request permission for a second internship slot.
                </p>
                <div className="mt-4">
                  <button
                    id="raise-request-slot-2"
                    disabled={secondStarting}
                    onClick={raiseSecondRequest}
                    className="ui-button text-sm"
                  >
                    {secondStarting ? "Submitting…" : "Raise Internship Request"}
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
