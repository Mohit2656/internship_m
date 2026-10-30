"use client";
import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { WeeklyProgress } from "@/components/WeeklyProgress";
export default function StudentWeeklyProgressPage() {
  return <ProtectedRoute role="STUDENT"><AppFrame><main className="mx-auto max-w-5xl px-5 py-10 sm:px-8"><Link href="/student/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link><h1 className="mt-6 text-3xl font-bold">Weekly Internship Progress</h1><p className="mt-2 text-slate-600">View your generated weeks, submit progress and read faculty mentor feedback.</p><WeeklyProgress scope="student"/></main></AppFrame></ProtectedRoute>;
}
