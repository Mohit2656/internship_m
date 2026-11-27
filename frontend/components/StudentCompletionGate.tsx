"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { BackButton } from "@/components/BackButton";
import { api } from "@/lib/api";
import { InternshipDetails } from "@/lib/internship-details";
import { InternshipOnboarding } from "@/lib/internship-onboarding";
import { ProfessionalProfile, SkillProfile } from "@/lib/professional-profile";
import { calculateProfileCompletion, ProfileCompletionResult } from "@/lib/profile-completion";
import { StudentProfile } from "@/lib/student-profile";

export const STUDENT_COMPLETION_CHANGED_EVENT = "ims-student-completion-changed";
export const STUDENT_LOCKED_ROUTES_KEY = "ims_student_locked_routes";

export function syncStudentLockedRoutes(completion: ProfileCompletionResult | null) {
  if (typeof window === "undefined" || !completion) return;
  const personal = completion.sections.find((s) => s.id === "personal");
  const professional = completion.sections.find((s) => s.id === "professional");
  const isCoreComplete = Boolean(
    personal &&
      professional &&
      personal.completedCount === personal.totalCount &&
      professional.completedCount === professional.totalCount
  );
  const locked: string[] = [];
  if (!isCoreComplete) {
    locked.push("/student/internship");
  }
  if (!completion.isComplete) {
    locked.push("/student/attendance", "/student/progress-reports", "/student/opportunities");
  }
  let changed = false;
  try {
    const next = JSON.stringify(locked);
    if (sessionStorage.getItem(STUDENT_LOCKED_ROUTES_KEY) !== next) {
      sessionStorage.setItem(STUDENT_LOCKED_ROUTES_KEY, next);
      changed = true;
    }
  } catch {}
  if (changed) {
    window.dispatchEvent(new Event(STUDENT_COMPLETION_CHANGED_EVENT));
  }
}

export function notifyStudentCompletionChanged() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(STUDENT_COMPLETION_CHANGED_EVENT));
  }
}

export type StudentCompletionState = {
  loading: boolean;
  completion: ProfileCompletionResult | null;
  isCoreComplete: boolean;
  isAllComplete: boolean;
};

/**
 * Loads the student's live profile completion status across Personal Profile,
 * Professional Profile & Skills, and Internship Details.
 */
export function useStudentCompletionStatus(): StudentCompletionState {
  const [loading, setLoading] = useState(true);
  const [completion, setCompletion] = useState<ProfileCompletionResult | null>(null);

  const load = useCallback(async () => {
    try {
      const [profile, professional, skills, onboarding, details] = await Promise.all([
        api.get<StudentProfile>("/student/profile").then((r) => r.data).catch(() => null),
        api.get<ProfessionalProfile>("/student/professional-profile").then((r) => r.data).catch(() => null),
        api.get<SkillProfile>("/student/professional-profile/skills").then((r) => r.data).catch(() => null),
        api.get<InternshipOnboarding>("/student/internship-onboarding").then((r) => r.data).catch(() => null),
        api.get<InternshipDetails>("/student/internship/details").then((r) => r.data).catch(() => null),
      ]);
      const computed = calculateProfileCompletion({
        profile,
        professionalProfile: professional,
        skillsCount: skills?.skills?.length ?? 0,
        onboarding,
        details,
      });
      setCompletion(computed);
      const personalSec = computed.sections.find((s) => s.id === "personal");
      const professionalSec = computed.sections.find((s) => s.id === "professional");
      const coreOk = Boolean(
        personalSec &&
          professionalSec &&
          personalSec.completedCount === personalSec.totalCount &&
          professionalSec.completedCount === professionalSec.totalCount
      );
      const locked: string[] = [];
      if (!coreOk) locked.push("/student/internship");
      if (!computed.isComplete) {
        locked.push("/student/attendance", "/student/progress-reports", "/student/opportunities");
      }
      try {
        sessionStorage.setItem(STUDENT_LOCKED_ROUTES_KEY, JSON.stringify(locked));
      } catch {}
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const onChanged = () => void load();
    window.addEventListener(STUDENT_COMPLETION_CHANGED_EVENT, onChanged);
    return () => window.removeEventListener(STUDENT_COMPLETION_CHANGED_EVENT, onChanged);
  }, [load]);

  const personal = completion?.sections.find((s) => s.id === "personal");
  const professional = completion?.sections.find((s) => s.id === "professional");
  const isCoreComplete = Boolean(
    personal &&
      professional &&
      personal.completedCount === personal.totalCount &&
      professional.completedCount === professional.totalCount
  );
  const isAllComplete = Boolean(completion?.isComplete);

  return { loading, completion, isCoreComplete, isAllComplete };
}

/**
 * Gates access to student workspace sections until the required profile sections are 100% complete.
 * - `mode="core"`: requires Personal Profile + Professional Profile & Skills at 100% (used for My Internships onboarding so diary can be issued).
 * - `mode="all"`: requires all applicable sections (including Internship Details) at 100%.
 */
export function StudentCompletionGate({
  mode = "all",
  sectionTitle,
  children,
}: {
  mode?: "all" | "core";
  sectionTitle: string;
  children: React.ReactNode;
}) {
  const { loading, completion, isCoreComplete, isAllComplete } = useStudentCompletionStatus();

  if (loading) {
    return (
      <main className="mx-auto max-w-4xl px-4 py-8 sm:px-8">
        <BackButton fallbackHref="/student/dashboard" fallbackLabel="Back to Dashboard" fixedFallback />
        <p role="status" className="mt-6 rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
          Checking profile completion…
        </p>
      </main>
    );
  }

  const unlocked = mode === "core" ? isCoreComplete : isAllComplete;
  if (unlocked || !completion) {
    return <>{children}</>;
  }

  const relevantSections =
    mode === "core"
      ? completion.sections.filter((s) => s.id === "personal" || s.id === "professional")
      : completion.sections.filter((s) => s.applicable);

  const firstIncomplete = relevantSections.find((s) => s.completedCount < s.totalCount);
  const targetHref = `/student/settings?tab=profile&edit=true&panel=${firstIncomplete?.id ?? "personal"}`;

  return (
    <main className="mx-auto max-w-4xl px-4 py-6 sm:px-8 sm:py-10">
      <BackButton fallbackHref="/student/dashboard" fallbackLabel="Back to Dashboard" fixedFallback />

      <section
        aria-label="Profile completion required"
        className="mt-6 rounded-2xl border border-blue-200 bg-white p-5 shadow-sm sm:p-8"
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-800">
            <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="h-3.5 w-3.5">
              <rect x="4" y="11" width="16" height="10" rx="2" />
              <path strokeLinecap="round" d="M8 11V7a4 4 0 0 1 8 0v4" />
            </svg>
            Profile Completion Required
          </span>
          <span className="rounded-full bg-blue-50 px-3 py-1 text-xs font-bold text-blue-800">
            {completion.percentage}% Complete
          </span>
        </div>

        <h1 className="mt-4 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
          Complete your profile to access {sectionTitle}
        </h1>
        <p className="mt-2 text-sm leading-6 text-slate-600">
          {mode === "core"
            ? "Finish your Personal Profile and Professional Profile & Skills first so your internship workflow can be unlocked."
            : "Complete all three profile sections (Personal Profile, Professional Profile & Skills, and Internship Details) from the profile setup screen before accessing this section."}
        </p>

        <div className="mt-6 grid gap-3 sm:grid-cols-3">
          {relevantSections.map((sec) => {
            const done = sec.completedCount === sec.totalCount;
            return (
              <div
                key={sec.id}
                className={`rounded-xl border p-3.5 text-xs ${
                  done
                    ? "border-emerald-200 bg-emerald-50/40 text-emerald-950"
                    : "border-slate-200 bg-slate-50 text-slate-900"
                }`}
              >
                <div className="flex items-center justify-between gap-2 font-semibold">
                  <span>{sec.title}</span>
                  <span
                    className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${
                      done ? "bg-emerald-100 text-emerald-800" : "bg-amber-100 text-amber-800"
                    }`}
                  >
                    {sec.percentage}%
                  </span>
                </div>
                <p className="mt-1 text-slate-500">
                  {sec.completedCount} of {sec.totalCount} completed
                </p>
              </div>
            );
          })}
        </div>

        <div className="mt-6 flex flex-wrap gap-3">
          <Link href={targetHref} className="ui-button w-full sm:w-auto justify-center text-sm">
            Complete Profile
          </Link>
        </div>
      </section>
    </main>
  );
}
