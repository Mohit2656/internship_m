"use client";
import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { WeeklyProgress } from "@/components/WeeklyProgress";
export default function MentorWeeklyProgressPage() {
  return <ProtectedRoute role="MENTOR"><AppFrame><main className="mx-auto max-w-6xl px-5 py-10 sm:px-8"><Link href="/mentor/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link><h1 className="mt-6 text-3xl font-bold">Weekly Progress Review</h1><p className="mt-2 text-slate-600">Expand an assigned student to monitor weekly progress and review submissions.</p><WeeklyProgress scope="mentor"/></main></AppFrame></ProtectedRoute>;
}
