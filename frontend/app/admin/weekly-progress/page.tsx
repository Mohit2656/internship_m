"use client";
import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { WeeklyProgress } from "@/components/WeeklyProgress";
export default function AdminWeeklyProgressPage() {
  return <ProtectedRoute role="ADMIN"><AppFrame><main className="mx-auto max-w-6xl px-5 py-10 sm:px-8"><Link href="/admin/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link><h1 className="mt-6 text-3xl font-bold">Weekly Progress Monitoring</h1><p className="mt-2 text-slate-600">Monitor generated periods, missing submissions, faculty decisions and attendance summaries.</p><WeeklyProgress scope="admin"/></main></AppFrame></ProtectedRoute>;
}
