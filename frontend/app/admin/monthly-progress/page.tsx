"use client";

import Link from "next/link";
import { AppFrame } from "@/components/AppFrame";
import { MonthlyProgressReportList } from "@/components/MonthlyProgressReportList";
import { ProtectedRoute } from "@/components/ProtectedRoute";

export default function AdminMonthlyProgressPage() {
  return <ProtectedRoute role="ADMIN"><AppFrame><main className="mx-auto max-w-6xl px-5 py-10 sm:px-8"><Link href="/admin/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link><p className="mt-6 text-sm font-semibold text-blue-700">III Cell monitoring</p><h1 className="mt-2 text-3xl font-bold tracking-tight">Monthly Progress Reports</h1><p className="mt-2 text-slate-600">Review submitted reports and download standardized institutional PDFs.</p><MonthlyProgressReportList scope="admin"/></main></AppFrame></ProtectedRoute>;
}
