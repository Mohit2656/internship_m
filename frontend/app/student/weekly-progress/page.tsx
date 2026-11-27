"use client";
import { AppFrame } from "@/components/AppFrame";
import { BackButton } from "@/components/BackButton";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { StudentCompletionGate } from "@/components/StudentCompletionGate";
import { WeeklyProgress } from "@/components/WeeklyProgress";
export default function StudentWeeklyProgressPage() {
  return <ProtectedRoute role="STUDENT"><AppFrame><StudentCompletionGate mode="all" sectionTitle="Weekly Progress"><main className="mx-auto max-w-5xl px-4 py-6 sm:px-8 sm:py-10"><BackButton fallbackHref="/student/dashboard" fallbackLabel="Back to Dashboard" fixedFallback /><h1 className="mt-5 text-3xl font-bold">Weekly Internship Progress</h1><p className="mt-2 text-slate-600">View your generated weeks, submit progress and read faculty mentor feedback.</p><WeeklyProgress scope="student"/></main></StudentCompletionGate></AppFrame></ProtectedRoute>;
}
