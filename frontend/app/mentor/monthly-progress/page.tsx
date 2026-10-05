"use client";

import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { MonthlyProgressReportList } from "@/components/MonthlyProgressReportList";
import { ProtectedRoute } from "@/components/ProtectedRoute";

export default function MentorMonthlyProgressPage() {
  return <ProtectedRoute role="MENTOR"><AppFrame><main className="mx-auto max-w-6xl px-5 py-10 sm:px-8"><Link href="/mentor/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link><p className="mt-6 text-sm font-semibold text-blue-700">Assigned student monitoring</p><h1 className="mt-2 text-3xl font-bold tracking-tight">Monthly Progress</h1><p className="mt-2 text-slate-600">View institutional progress reports submitted by students currently assigned to you.</p><MonthlyProgressReportList scope="mentor"/></main></AppFrame></ProtectedRoute>;
}
