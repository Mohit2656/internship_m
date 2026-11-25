"use client";

import Link from "next/link";
import type { ProfileCompletionResult } from "@/lib/profile-completion";

export function ProfileCompletionTracker({
  completion,
}: {
  completion: ProfileCompletionResult;
}) {
  const { percentage, remainingPercentage, isComplete, sections, nextAction } = completion;

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-bold text-slate-900">Profile Completion</h2>
            <span
              className={`rounded-full px-3 py-1 text-xs font-bold ${
                isComplete
                  ? "bg-emerald-50 text-emerald-800 border border-emerald-200"
                  : percentage >= 50
                  ? "bg-blue-50 text-blue-800 border border-blue-200"
                  : "bg-amber-50 text-amber-800 border border-amber-200"
              }`}
            >
              {percentage}% Complete
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-600">
            {percentage}% completed · {remainingPercentage}% remaining
          </p>
        </div>

        {!isComplete && nextAction && (
          <Link
            href={nextAction.href}
            className="ui-button text-sm inline-flex items-center gap-1.5"
          >
            <span>Complete Profile</span>
            <svg
              className="h-4 w-4"
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

      {/* Progress Bar */}
      <div className="mt-5">
        <div className="h-3 w-full overflow-hidden rounded-full bg-slate-100">
          <div
            className={`h-full transition-all duration-500 rounded-full ${
              isComplete
                ? "bg-emerald-600"
                : percentage >= 50
                ? "bg-blue-600"
                : "bg-amber-500"
            }`}
            style={{ width: `${percentage}%` }}
            role="progressbar"
            aria-valuenow={percentage}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label="Profile completion percentage"
          />
        </div>
      </div>

      {/* Breakdown by sections */}
      <div className="mt-6 grid gap-4 sm:grid-cols-3">
        {sections.map((section) => {
          if (!section.applicable) {
            return (
              <div
                key={section.id}
                className="rounded-xl border border-slate-200/60 bg-slate-50/60 p-4 text-xs text-slate-500"
              >
                <div className="flex items-center justify-between font-semibold">
                  <span>{section.title}</span>
                  <span className="text-slate-400">N/A</span>
                </div>
                <p className="mt-2 text-slate-400">Not required at current stage</p>
              </div>
            );
          }

          const sectionDone = section.completedCount === section.totalCount;

          return (
            <div
              key={section.id}
              className={`rounded-xl border p-4 text-xs transition-colors ${
                sectionDone
                  ? "border-emerald-200 bg-emerald-50/30 text-emerald-950"
                  : "border-slate-200 bg-white text-slate-900"
              }`}
            >
              <div className="flex items-center justify-between font-semibold">
                <span className="text-sm">{section.title}</span>
                <span
                  className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${
                    sectionDone
                      ? "bg-emerald-100 text-emerald-800"
                      : "bg-slate-100 text-slate-700"
                  }`}
                >
                  {section.percentage}%
                </span>
              </div>
              <p className="mt-1 text-slate-500">
                {section.completedCount} of {section.totalCount} items completed
              </p>

              {!sectionDone && section.missingFields.length > 0 && (
                <div className="mt-3 border-t border-slate-100 pt-2">
                  <p className="font-semibold text-amber-800">Incomplete:</p>
                  <p className="mt-0.5 text-slate-600 line-clamp-2">
                    {section.missingFields.slice(0, 3).join(", ")}
                    {section.missingFields.length > 3 && ` +${section.missingFields.length - 3} more`}
                  </p>
                  <Link
                    href={section.actionHref}
                    className="mt-2 inline-block font-semibold text-blue-700 hover:underline"
                  >
                    Update →
                  </Link>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </section>
  );
}

export function CompactProfileCompletion({
  completion,
}: {
  completion: ProfileCompletionResult;
}) {
  const { percentage, remainingPercentage, isComplete, nextAction, incompleteSections } =
    completion;

  // Rule: When profile reaches 100%, remove the completion reminder from dashboard
  if (isComplete) return null;

  const missingSummary = incompleteSections
    .map((s) => s.missingFields[0])
    .filter(Boolean)
    .slice(0, 2)
    .join(", ");

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
              ? `Action required: Add ${missingSummary}${
                  incompleteSections.length > 1 ? " and other details" : ""
                } to complete your profile.`
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
