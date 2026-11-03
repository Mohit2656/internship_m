"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { WorkflowBadge } from "@/components/WorkflowBadge";
import { TodayAttendanceCard } from "@/components/TodayAttendanceCard";
import { InternshipLocationManager } from "@/components/InternshipLocationManager";
import { DailyAttendanceHistory } from "@/components/DailyAttendanceHistory";
import { api } from "@/lib/api";
import type { InternshipDetails } from "@/lib/internship-details";
import type { InternshipOnboarding } from "@/lib/internship-onboarding";
import type { StudentProfile } from "@/lib/student-profile";
import type { ProfessionalProfile } from "@/lib/professional-profile";
import { formatDateTime } from "@/lib/monthly-progress";

import { getSelectedInternship, setSelectedInternship, deriveInternshipStatus } from "@/lib/internship-selection";

type StudentMentorInfo = {
  mentorName: string | null;
  mentorEmail: string | null;
  internship1MentorName: string | null;
  internship2MentorName: string | null;
};

type SecondInternshipRequest = {
  id: number;
  studentId: number;
  studentName: string;
  existingInternshipId: number;
  source: string | null;
  company: string;
  startDate: string;
  endDate: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  requestedAt: string;
  rejectionReason: string | null;
};

type NotificationItem = {
  id: number;
  title: string;
  message: string;
  actionUrl: string;
  createdAt: string;
  readAt: string | null;
};

function hasDashboardValue(val: unknown): boolean {
  if (val === null || val === undefined) return false;
  if (typeof val === "string") return val.trim().length > 0;
  if (typeof val === "number") return !Number.isNaN(val);
  return true;
}

function calculateDashboardProfileCompletion(params: {
  profile: StudentProfile | null;
  professionalProfile: ProfessionalProfile | null;
  onboarding: InternshipOnboarding | null;
  details: InternshipDetails | null;
}) {
  const { profile, professionalProfile, onboarding, details } = params;

  const personalChecks = [
    { label: "Personal Email", done: hasDashboardValue(profile?.personalEmail) },
    { label: "Phone Number", done: hasDashboardValue(profile?.phoneNumber) },
    { label: "Blood Group", done: hasDashboardValue(profile?.bloodGroup) },
    { label: "Roll Number", done: hasDashboardValue(profile?.rollNumber) },
    { label: "Academic Year", done: hasDashboardValue(profile?.academicYear) },
    { label: "Semester", done: hasDashboardValue(profile?.semester) },
    { label: "Permanent Address", done: hasDashboardValue(profile?.permanentAddress) },
    { label: "Parent / Guardian Name", done: hasDashboardValue(profile?.guardianName) },
    { label: "Parent / Guardian Contact", done: hasDashboardValue(profile?.guardianContact) },
    { label: "Emergency Contact", done: hasDashboardValue(profile?.emergencyContact) },
    { label: "Aadhaar Identity", done: hasDashboardValue(profile?.maskedAadhaar) },
  ];
  const personalDone = personalChecks.filter((c) => c.done).length;
  const personalTotal = personalChecks.length;
  const personalRatio = personalTotal > 0 ? personalDone / personalTotal : 0;
  const personalMissing = personalChecks.filter((c) => !c.done).map((c) => c.label);

  const professionalChecks = [
    { label: "Professional Headline", done: hasDashboardValue(professionalProfile?.headline) },
    { label: "Career Interests", done: hasDashboardValue(professionalProfile?.careerInterests) },
    { label: "Uploaded Resume", done: Boolean(professionalProfile?.resume) },
    { label: "Confirmed Skills", done: Boolean(professionalProfile?.skillsUpdatedAt) },
  ];
  const professionalDone = professionalChecks.filter((c) => c.done).length;
  const professionalTotal = professionalChecks.length;
  const professionalRatio = professionalTotal > 0 ? professionalDone / professionalTotal : 0;
  const professionalMissing = professionalChecks.filter((c) => !c.done).map((c) => c.label);

  const nonApplicableStatuses = ["PRE_COMMENCEMENT", "NOT_STARTED"];
  const isInternshipApplicable = Boolean(
    details ||
      (onboarding?.workflowStatus && !nonApplicableStatuses.includes(onboarding.workflowStatus))
  );

  let internshipRatio = 0;
  let internshipDone = 0;
  let internshipTotal = 7;
  let internshipMissing: string[] = [];

  if (isInternshipApplicable) {
    const internshipChecks = [
      { label: "Company Name", done: hasDashboardValue(details?.companyName) },
      { label: "Company Address", done: hasDashboardValue(details?.companyAddress) },
      { label: "Supervisor Name", done: hasDashboardValue(details?.industrySupervisorName) },
      { label: "Supervisor Contact", done: hasDashboardValue(details?.industrySupervisorContact) },
      { label: "Internship Mode", done: hasDashboardValue(details?.internshipMode) },
      { label: "Start Date", done: hasDashboardValue(details?.startDate) },
      { label: "End Date", done: hasDashboardValue(details?.endDate) },
    ];
    internshipDone = internshipChecks.filter((c) => c.done).length;
    internshipTotal = internshipChecks.length;
    internshipRatio = internshipTotal > 0 ? internshipDone / internshipTotal : 0;
    internshipMissing = internshipChecks.filter((c) => !c.done).map((c) => c.label);
  }

  let percentage: number;
  if (isInternshipApplicable) {
    percentage = Math.round(
      personalRatio * 40 + professionalRatio * 30 + internshipRatio * 30
    );
  } else {
    percentage = Math.round(personalRatio * 60 + professionalRatio * 40);
  }

  if (
    personalDone === personalTotal &&
    professionalDone === professionalTotal &&
    (!isInternshipApplicable || internshipDone === internshipTotal)
  ) {
    percentage = 100;
  }
  percentage = Math.max(0, Math.min(100, percentage));
  const remainingPercentage = 100 - percentage;
  const isComplete = percentage === 100;

  const incompleteMissing = [
    ...personalMissing,
    ...professionalMissing,
    ...(isInternshipApplicable ? internshipMissing : []),
  ];

  let nextAction = { label: "Complete Profile", href: "/student/settings?tab=profile&edit=true" };
  if (personalDone === personalTotal && professionalDone < professionalTotal) {
    nextAction = { label: "Update Professional Profile", href: "/student/professional-profile" };
  } else if (personalDone === personalTotal && professionalDone === professionalTotal && isInternshipApplicable && internshipDone < internshipTotal) {
    nextAction = { label: "Complete Internship Details", href: "/student/internship/details" };
  }

  return {
    percentage,
    remainingPercentage,
    isComplete,
    missingFields: incompleteMissing,
    nextAction: isComplete ? null : nextAction,
  };
}

function CompactProfileCompletion({
  completion,
}: {
  completion: ReturnType<typeof calculateDashboardProfileCompletion>;
}) {
  const { percentage, remainingPercentage, isComplete, nextAction, missingFields } = completion;
  if (isComplete) return null;

  const missingSummary = missingFields.slice(0, 2).join(", ");

  return (
    <section className="rounded-2xl border border-blue-200/80 bg-gradient-to-r from-blue-50/70 via-indigo-50/40 to-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex-1 min-w-[240px]">
          <div className="flex items-center gap-2.5">
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">
              %
            </span>
            <h2 className="text-base font-bold text-slate-900">
              Profile Completion — {percentage}%
            </h2>
            <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-800">
              {remainingPercentage}% remaining
            </span>
          </div>
          <p className="mt-1 text-xs text-slate-600">
            {missingSummary
              ? `Action required: Complete ${missingSummary}${
                  missingFields.length > 2 ? " and other required details" : ""
                } to finish your profile.`
              : "Complete your profile to increase your visibility for opportunities."}
          </p>

          <div className="mt-3 h-2 w-full max-w-md overflow-hidden rounded-full bg-blue-200/60">
            <div
              className="h-full rounded-full bg-blue-600 transition-all duration-500"
              style={{ width: `${percentage}%` }}
              role="progressbar"
              aria-valuenow={percentage}
              aria-valuemin={0}
              aria-valuemax={100}
            />
          </div>
        </div>

        {nextAction && (
          <Link
            href={nextAction.href}
            className="ui-button text-xs font-semibold inline-flex items-center gap-1.5 whitespace-nowrap"
          >
            <span>Complete Profile</span>
            <svg
              className="h-3.5 w-3.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              strokeWidth="2"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        )}
      </div>
    </section>
  );
}

export default function StudentDashboard() {
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [professionalProfile, setProfessionalProfile] = useState<ProfessionalProfile | null>(null);
  const [onboarding, setOnboarding] = useState<InternshipOnboarding | null>(null);
  const [details, setDetails] = useState<InternshipDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState("");
  const [activeTab, setActiveTabState] = useState<1 | 2>(1);
  const [secondRequests, setSecondRequests] = useState<SecondInternshipRequest[]>([]);
  const [secondDetails, setSecondDetails] = useState<InternshipDetails | null>(null);
  const [secondOnboarding, setSecondOnboarding] = useState<InternshipOnboarding | null>(null);
  const [secondStarting, setSecondStarting] = useState(false);
  const [secondError, setSecondError] = useState("");
  const [recentNotifications, setRecentNotifications] = useState<NotificationItem[]>([]);
  const [mentorInfo, setMentorInfo] = useState<StudentMentorInfo | null>(null);

  function setActiveTab(tab: 1 | 2) {
    setActiveTabState(tab);
    setSelectedInternship(tab);
  }

  useEffect(() => {
    setActiveTabState(getSelectedInternship());
    Promise.all([
      api.get<StudentProfile>("/student/profile").then((r) => r.data).catch(() => null),
      api.get<ProfessionalProfile>("/student/professional-profile").then((r) => r.data).catch(() => null),
      api.get<InternshipOnboarding>("/student/internship-onboarding").then((r) => r.data),
      api.get<InternshipOnboarding>("/student/internship-onboarding", { params: { internshipNumber: 2 } }).then((r) => r.data).catch(() => null),
      api.get<InternshipDetails>("/student/internship").then((r) => r.data).catch(() => null),
      api.get<InternshipDetails[]>("/student/internship/all").then((r) => r.data).catch(() => []),
      api.get<SecondInternshipRequest[]>("/student/second-internship-requests").then((r) => r.data).catch(() => []),
      api.get<{ content: NotificationItem[] }>("/student/notifications", { params: { page: 0 } }).then((r) => r.data?.content ?? []).catch(() => []),
      api.get<StudentMentorInfo>("/student/mentor").then((r) => r.data).catch(() => null),
    ])
      .then(([studentProfile, professional, internship, secondOb, internshipDetails, allDetails, requests, notifs, mentor]) => {
        setProfile(studentProfile);
        setProfessionalProfile(professional);
        setOnboarding(internship);
        setSecondOnboarding(secondOb);
        setDetails(internshipDetails);
        const slot2 = Array.isArray(allDetails) ? allDetails.find((x) => x.internshipNumber === 2) ?? null : null;
        setSecondDetails(slot2);
        setSecondRequests(Array.isArray(requests) ? requests : []);
        setRecentNotifications(Array.isArray(notifs) ? notifs.slice(0, 3) : []);
        setMentorInfo(mentor);
      })
      .catch(() => setError("Dashboard status could not be loaded."))
      .finally(() => setLoading(false));
  }, []);

  async function secureInternship() {
    setStarting(true);
    setError("");
    try {
      const { data } = await api.post<InternshipOnboarding>("/student/internship-onboarding/secure");
      setOnboarding(data);
      window.location.assign("/student/internship");
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
    } catch (cause: any) {
      setSecondError(cause.response?.data?.message ?? "Request could not be submitted.");
    } finally {
      setSecondStarting(false);
    }
  }

  const latestSecondRequest = secondRequests[0] ?? null;
  const todayStr = new Date().toISOString().slice(0, 10);
  const isInternshipStarted = Boolean(details && details.startDate && details.startDate <= todayStr);
  const studentFirstName = profile?.studentName?.trim().split(/\s+/)[0] || "Student";

  // Actionable items
  const pendingActions: Array<{ id: string; title: string; description: string; href: string; label: string }> = [];
  if (!loading) {
    if (!profile) {
      pendingActions.push({
        id: "profile",
        title: "Complete Institutional Profile",
        description: "Your student profile must be completed before registering an internship.",
        href: "/student/profile",
        label: "Complete Profile",
      });
    }
    if (onboarding && onboarding.workflowStatus !== "INTERNSHIP_DETAILS_COMPLETE") {
      if (onboarding.workflowStatus === "NOT_SECURED") {
        pendingActions.push({
          id: "secure",
          title: "Internship Registration",
          description: "Once you have secured an internship, raise a request to begin the approval process.",
          href: "/student/internship",
          label: "Start Registration",
        });
      } else if (onboarding.workflowStatus === "DIARY_ISSUED") {
        pendingActions.push({
          id: "details",
          title: "Complete Internship Details",
          description: "Your diary has been issued. Enter finalized information to complete registration.",
          href: "/student/internship/details",
          label: "Enter Details",
        });
      } else if (onboarding.documentsCompleted < onboarding.documentsRequired) {
        pendingActions.push({
          id: "docs",
          title: "Upload Required Documents",
          description: `${onboarding.documentsCompleted} of ${onboarding.documentsRequired} documents uploaded.`,
          href: "/student/internship",
          label: "Upload Documents",
        });
      }
    }
    if (latestSecondRequest?.status === "APPROVED" && !secondDetails) {
      if (secondOnboarding?.workflowStatus === "DIARY_ISSUED") {
        pendingActions.push({
          id: "second-approved",
          title: "Complete Internship 2 Details",
          description: "Your diary has been issued. Enter finalized information to complete registration.",
          href: "/student/internship/details?tab=2",
          label: "Enter Details",
        });
      } else {
        pendingActions.push({
          id: "second-approved",
          title: "Complete Internship 2 Initial Information",
          description: "Your second internship request was approved. Provide initial documents and diary confirmation.",
          href: "/student/internship?tab=2",
          label: "Fill Details",
        });
      }
    }
  }



  function renderRecentNotifications() {
    if (recentNotifications.length === 0) return null;
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-7">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-lg font-bold text-slate-900">Recent Notifications</h2>
          <Link href="/student/notifications" className="text-sm font-semibold text-blue-700 hover:underline">
            All notifications →
          </Link>
        </div>
        <ul className="mt-4 divide-y divide-slate-100">
          {recentNotifications.map((item) => (
            <li key={item.id} className="py-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <Link
                  href={item.actionUrl?.startsWith("/student/") ? item.actionUrl : "/student/notifications"}
                  className="text-sm font-semibold text-blue-700 hover:underline"
                >
                  {item.title}
                </Link>
                <span className="text-xs text-slate-500">
                  {item.readAt ? "Read" : "Unread"} · {formatDateTime(item.createdAt)}
                </span>
              </div>
              <p className="mt-1 text-sm text-slate-600">{item.message}</p>
            </li>
          ))}
        </ul>
      </section>
    );
  }

  const completion = calculateDashboardProfileCompletion({
    profile,
    professionalProfile,
    onboarding,
    details,
  });

  return (
    <ProtectedRoute role="STUDENT">
      <AppFrame>
        <main className="mx-auto max-w-7xl px-5 py-8 sm:px-8">
          {/* Header */}
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="text-sm font-semibold text-blue-700">Student workspace</p>
              <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950">
                Welcome back, {studentFirstName}!
              </h1>
              <p className="mt-1 text-sm text-slate-600">
                {isInternshipStarted
                  ? `Active internship at ${details?.companyName}. Track attendance and reports.`
                  : "Institutional internship registration and preparation portal."}
              </p>
            </div>
            <div className="flex items-center gap-3">
              {!loading && (
                <span
                  className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold ${
                    isInternshipStarted
                      ? "bg-emerald-50 text-emerald-800 border border-emerald-200"
                      : "bg-blue-50 text-blue-800 border border-blue-200"
                  }`}
                >
                  <span
                    className={`h-2 w-2 rounded-full ${
                      isInternshipStarted ? "bg-emerald-500" : "bg-blue-600"
                    }`}
                  />
                  {isInternshipStarted ? "Internship Active" : "Pre-Commencement"}
                </span>
              )}
            </div>
          </div>

          {error && (
            <p role="alert" className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">
              {error}
            </p>
          )}

          {/* Compact Profile Completion (only shown if profile is incomplete) */}
          <div className="mt-6">
            <CompactProfileCompletion completion={completion} />
          </div>

          {/* Pending Actions (only rendered if actionable items exist) */}
          {pendingActions.length > 0 && (
            <section className="mt-6 rounded-2xl border border-amber-200 bg-amber-50/60 p-5 sm:p-6" aria-label="Pending actions">
              <h2 className="text-sm font-bold uppercase tracking-[0.14em] text-amber-900">
                Pending Actions ({pendingActions.length})
              </h2>
              <div className="mt-3 space-y-3">
                {pendingActions.map((action) => (
                  <div
                    key={action.id}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200/80 bg-white p-4 text-sm shadow-sm"
                  >
                    <div>
                      <p className="font-semibold text-slate-900">{action.title}</p>
                      <p className="mt-0.5 text-xs text-slate-600">{action.description}</p>
                    </div>
                    <Link href={action.href} className="ui-button text-xs whitespace-nowrap">
                      {action.label}
                    </Link>
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* Conditional Layout Based on Internship Lifecycle */}
          {(() => {
            const availableInternships = [
              ...(details
                ? [
                    {
                      id: details.id,
                      internshipNumber: 1,
                      companyName: details.companyName,
                      status: deriveInternshipStatus(details),
                    },
                  ]
                : []),
              ...(secondDetails
                ? [
                    {
                      id: secondDetails.id,
                      internshipNumber: 2,
                      companyName: secondDetails.companyName,
                      status: deriveInternshipStatus(secondDetails),
                    },
                  ]
                : []),
            ];

            const activeInternshipId =
              activeTab === 2 && secondDetails ? secondDetails.id : details ? details.id : undefined;

            return !isInternshipStarted ? (
              /* PRE-COMMENCEMENT: REGISTRATION & ONBOARDING PROMINENT */
              <div className="mt-6 space-y-6">
                {renderRecentNotifications()}
                {!loading && availableInternships.length > 0 && (
                  <div className="mt-6">
                    <InternshipLocationManager
                      scope="student"
                      internships={availableInternships}
                      initialInternshipId={activeInternshipId}
                    />
                  </div>
                )}
                <div className="space-y-2">
                  <TodayAttendanceCard />
                  <div className="flex justify-end">
                    <Link
                      href="/student/attendance"
                      className="text-xs font-semibold text-blue-700 hover:underline inline-flex items-center gap-1"
                    >
                      <span>View complete attendance history</span>
                      <span aria-hidden="true">→</span>
                    </Link>
                  </div>
                </div>
              </div>
            ) : (
              /* POST-COMMENCEMENT: ATTENDANCE PROMINENT AT TOP */
              <div className="mt-6 space-y-6">
                <div className="space-y-2">
                  <TodayAttendanceCard />
                  <div className="flex justify-end">
                    <Link
                      href="/student/attendance"
                      className="text-xs font-semibold text-blue-700 hover:underline inline-flex items-center gap-1"
                    >
                      <span>View complete attendance history</span>
                      <span aria-hidden="true">→</span>
                    </Link>
                  </div>
                </div>
                {renderRecentNotifications()}
                {!loading && availableInternships.length > 0 && (
                  <div className="mt-6">
                    <InternshipLocationManager
                      scope="student"
                      internships={availableInternships}
                      initialInternshipId={activeInternshipId}
                    />
                  </div>
                )}
              </div>
            );
          })()}
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
