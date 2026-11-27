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
import type { ProfilePrefill, StudentProfile } from "@/lib/student-profile";
import type { ProfessionalProfile, SkillProfile } from "@/lib/professional-profile";
import { calculateProfileCompletion, type ProfileCompletionResult } from "@/lib/profile-completion";
import { syncStudentLockedRoutes } from "@/components/StudentCompletionGate";
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

function CompactProfileCompletion({
  completion,
}: {
  completion: ProfileCompletionResult;
}) {
  const { percentage, remainingPercentage, isComplete, nextAction, incompleteSections } = completion;
  if (isComplete) return null;

  const missingFields = incompleteSections.flatMap((section) => section.missingFields);
  const missingSummary = missingFields.slice(0, 2).join(", ");

  return (
    <section className="rounded-2xl border border-blue-200/80 bg-gradient-to-r from-blue-50/70 via-indigo-50/40 to-white p-4 shadow-sm sm:p-5 dark:border-[#345782] dark:from-[#162742] dark:via-[#14233b] dark:to-[#142136]">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">
              %
            </span>
            <h2 className="text-base font-bold text-slate-900">
              Profile Completion — {percentage}%
            </h2>
            <span className="whitespace-nowrap shrink-0 rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-semibold text-blue-800">
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
            className="ui-button w-full sm:w-auto text-xs font-semibold inline-flex items-center justify-center gap-1.5 whitespace-nowrap"
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
  const [prefill, setPrefill] = useState<ProfilePrefill | null>(null);
  const [professionalProfile, setProfessionalProfile] = useState<ProfessionalProfile | null>(null);
  const [skillsProfile, setSkillsProfile] = useState<SkillProfile | null>(null);
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
      api.get<ProfilePrefill>("/student/profile/prefill").then((r) => r.data).catch(() => null),
      api.get<ProfessionalProfile>("/student/professional-profile").then((r) => r.data).catch(() => null),
      api.get<SkillProfile>("/student/professional-profile/skills").then((r) => r.data).catch(() => null),
      api.get<InternshipOnboarding>("/student/internship-onboarding").then((r) => r.data),
      api.get<InternshipOnboarding>("/student/internship-onboarding", { params: { internshipNumber: 2 } }).then((r) => r.data).catch(() => null),
      api.get<InternshipDetails>("/student/internship").then((r) => r.data).catch(() => null),
      api.get<InternshipDetails[]>("/student/internship/all").then((r) => r.data).catch(() => []),
      api.get<SecondInternshipRequest[]>("/student/second-internship-requests").then((r) => r.data).catch(() => []),
      api.get<{ content: NotificationItem[] }>("/student/notifications", { params: { page: 0 } }).then((r) => r.data?.content ?? []).catch(() => []),
      api.get<StudentMentorInfo>("/student/mentor").then((r) => r.data).catch(() => null),
    ])
      .then(([studentProfile, profilePrefill, professional, skills, internship, secondOb, internshipDetails, allDetails, requests, notifs, mentor]) => {
        setProfile(studentProfile);
        setPrefill(profilePrefill);
        setProfessionalProfile(professional);
        setSkillsProfile(skills);
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
  const now = new Date();
  const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  const selectedDetails = activeTab === 2 ? secondDetails : details;
  const isInternshipStarted = Boolean(selectedDetails?.startDate && selectedDetails.startDate <= todayStr);
  const studentDisplayName = (profile?.studentName || prefill?.studentName || "").trim();
  const studentFirstName = studentDisplayName ? studentDisplayName.split(/\s+/)[0] : "Student";

  // Actionable items
  const pendingActions: Array<{ id: string; title: string; description: string; href: string; label: string }> = [];
  if (!loading) {
    if (!profile) {
      pendingActions.push({
        id: "profile",
        title: "Complete Institutional Profile",
        description: "Your student profile must be completed before registering an internship.",
        href: "/student/settings?tab=profile&edit=true&panel=personal",
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
      <section className="rounded-2xl border border-slate-200 bg-white p-4 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-base font-bold text-slate-900 sm:text-lg">Recent Notifications</h2>
          <Link
            href="/student/notifications"
            className="whitespace-nowrap shrink-0 rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-blue-700 hover:bg-blue-50"
          >
            View all
          </Link>
        </div>
        <ul className="mt-4 space-y-2.5">
          {recentNotifications.map((item) => {
            const isUnread = !item.readAt;
            return (
              <li
                key={item.id}
                className={`rounded-xl border p-3.5 sm:p-4 ${
                  isUnread
                    ? "border-blue-200/80 bg-blue-50/40"
                    : "border-slate-200/80 bg-slate-50/60"
                }`}
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <Link
                    href={item.actionUrl?.startsWith("/student/") ? item.actionUrl : "/student/notifications"}
                    className="min-w-0 flex-1 text-sm font-semibold text-blue-700 hover:underline"
                  >
                    {item.title}
                  </Link>
                  <span
                    className={`shrink-0 whitespace-nowrap rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                      isUnread
                        ? "bg-blue-100 text-blue-800"
                        : "bg-slate-200/70 text-slate-600"
                    }`}
                  >
                    {isUnread ? "Unread" : "Read"}
                  </span>
                </div>
                <p className="mt-1.5 text-sm leading-relaxed text-slate-600">{item.message}</p>
                <p className="mt-2 text-xs text-slate-500">{formatDateTime(item.createdAt)}</p>
              </li>
            );
          })}
        </ul>
      </section>
    );
  }

  const completion = calculateProfileCompletion({
    profile,
    professionalProfile,
    skillsCount: skillsProfile?.skills?.length ?? 0,
    onboarding,
    details,
  });

  const completionIncompleteKey = completion.incompleteSections.map((s) => s.id).join(",");
  useEffect(() => {
    if (!loading) {
      syncStudentLockedRoutes(completion);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, completion.percentage, completion.isComplete, completionIncompleteKey]);

  return (
    <ProtectedRoute role="STUDENT">
      <AppFrame>
        <main className="mx-auto max-w-7xl px-4 py-5 sm:px-8 sm:py-8">
          {/* Header */}
          <div className="space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-semibold text-blue-700">Student workspace</p>
              {!loading && (
                <span
                  className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold whitespace-nowrap ${
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
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
                Welcome back, {studentFirstName}!
              </h1>
              <p className="mt-1 text-sm text-slate-600">
                {isInternshipStarted
                  ? `Active internship at ${selectedDetails?.companyName}. Track attendance and reports.`
                  : "Institutional internship registration and preparation portal."}
              </p>
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

          {/* Assigned Faculty Mentor (auto-populated from III Cell assignment) */}
          {!loading && (() => {
            const assignedMentorName =
              mentorInfo?.mentorName?.trim() ||
              mentorInfo?.internship1MentorName?.trim() ||
              mentorInfo?.internship2MentorName?.trim() ||
              "";
            const assignedMentorEmail = mentorInfo?.mentorEmail?.trim() || "";
            const hasDistinctSlotMentors =
              Boolean(mentorInfo?.internship1MentorName && mentorInfo?.internship2MentorName) &&
              mentorInfo?.internship1MentorName !== mentorInfo?.internship2MentorName;

            return (
              <section
                aria-label="Assigned faculty mentor"
                className="mt-6 rounded-2xl border border-slate-200 bg-white p-4 sm:p-5"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-xs font-bold uppercase tracking-[0.14em] text-blue-700">
                    Faculty Mentor
                  </p>
                  <span
                    className={`whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      assignedMentorName
                        ? "border border-emerald-200 bg-emerald-50 text-emerald-800"
                        : "border border-slate-200 bg-slate-100 text-slate-600"
                    }`}
                  >
                    {assignedMentorName ? "Assigned" : "Not assigned yet"}
                  </span>
                </div>
                {assignedMentorName ? (
                  <div className="mt-3 space-y-1">
                    <p className="text-base font-bold text-slate-900">{assignedMentorName}</p>
                    {assignedMentorEmail && (
                      <p className="text-sm text-slate-600">
                        <a
                          href={`mailto:${assignedMentorEmail}`}
                          className="font-medium text-blue-700 hover:underline break-all"
                        >
                          {assignedMentorEmail}
                        </a>
                      </p>
                    )}
                    {hasDistinctSlotMentors && (
                      <p className="pt-1 text-xs text-slate-500">
                        Internship 1: {mentorInfo?.internship1MentorName} · Internship 2: {mentorInfo?.internship2MentorName}
                      </p>
                    )}
                  </div>
                ) : (
                  <p className="mt-2 text-sm text-slate-600">
                    Your assigned faculty mentor will appear here automatically once assigned by the III Cell.
                  </p>
                )}
              </section>
            );
          })()}

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
                    <Link href={action.href} className="ui-button w-full sm:w-auto justify-center text-xs whitespace-nowrap">
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
            const attendanceDetails = activeTab === 2 ? secondDetails : details;
            const attendanceUnavailable = !attendanceDetails;
            const isCompleted = Boolean(attendanceDetails?.endDate && attendanceDetails.endDate < todayStr);
            const futureStart = Boolean(attendanceDetails?.startDate && attendanceDetails.startDate > todayStr);
            const isOngoing = Boolean(attendanceDetails?.startDate && attendanceDetails.startDate <= todayStr && !isCompleted);

            if (isCompleted) {
              return (
                /* POST-COMPLETION: INTERNSHIP COMPLETED & HISTORICAL ATTENDANCE */
                <div className="mt-6 space-y-6">
                  <section className="rounded-2xl border border-emerald-200 bg-emerald-50/80 p-6">
                    <div className="flex items-center gap-3">
                      <span className="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-600 text-white font-bold text-sm">✓</span>
                      <div>
                        <h2 className="text-lg font-bold text-emerald-950">Internship Completed</h2>
                        <p className="mt-1 text-sm text-emerald-800">
                          Your internship at {attendanceDetails?.companyName} ended on {attendanceDetails?.endDate}.
                        </p>
                      </div>
                    </div>
                  </section>
                  <div className="flex justify-end">
                    <Link
                      href="/student/attendance"
                      className="text-xs font-semibold text-blue-700 hover:underline inline-flex items-center gap-1"
                    >
                      <span>View complete attendance history</span>
                      <span aria-hidden="true">→</span>
                    </Link>
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
            }

            if (!isOngoing) {
              /* PRE-COMMENCEMENT: REGISTRATION & ONBOARDING PROMINENT */
              return (
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
                  {futureStart && (
                    <section className="rounded-2xl border border-blue-200 bg-blue-50 p-5 text-sm text-blue-900">
                      Your internship starts on {attendanceDetails?.startDate}. Attendance will become available when your internship begins.
                    </section>
                  )}
                  {!attendanceUnavailable && (
                    <div className="flex justify-end">
                      <Link href="/student/attendance" className="text-xs font-semibold text-blue-700 hover:underline">
                        View attendance history →
                      </Link>
                    </div>
                  )}
                </div>
              );
            }

            return (
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
