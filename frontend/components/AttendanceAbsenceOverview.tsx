"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";

type Row = { recordId: number; studentName: string | null; uid: string | null; date: string; result: string; automaticAbsent: boolean };

export function AttendanceAbsenceOverview({ role }: { role: "ADMIN" | "MENTOR" }) {
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    const abort = new AbortController();
    const base = `/${role.toLowerCase()}/reports`;
    api.get<{ date: string }>(`${base}/types`, { signal: abort.signal }).then(({ data }) => {
      const from = new Date(`${data.date}T00:00:00Z`); from.setUTCDate(from.getUTCDate() - 30);
      return api.post<{ rows: Row[]; totalRows: number }>(`${base}/table`, { reportType: "ATTENDANCE", fields: ["recordId", "studentName", "uid", "date", "result", "automaticAbsent"], filters: { attendanceStatus: "ABSENT", historyFrom: from.toISOString().slice(0, 10), historyTo: data.date }, studentIds: [], page: 0, format: "XLSX" }, { signal: abort.signal });
    }).then(({ data }) => { setRows(data.rows); setTotal(data.totalRows); })
      .catch(() => { if (!abort.signal.aborted) setError("Daily absences could not be loaded."); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [role]);
  return <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6">
    <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-xl font-bold">Daily Absences</h2><Link href={`/${role.toLowerCase()}/attendance`} className="text-sm font-semibold text-blue-700 hover:underline">View attendance records and summaries</Link></div>
    <p className="mt-2 text-sm text-slate-600">Absent working days in the past 30 days{role === "MENTOR" ? " for your currently assigned students" : " within your authorized student scope"}.</p>
    {loading ? <p className="mt-4 text-sm text-slate-500">Loading daily absences…</p> : error ? <p role="alert" className="mt-4 text-sm text-red-700">{error}</p> : <>
      <p className="mt-4 text-sm font-semibold">{total} absent daily records</p>
      <div className="mt-3 max-h-80 overflow-auto"><table className="w-full text-left text-sm"><thead><tr className="border-b"><th className="p-3">Student</th><th className="p-3">Date</th><th className="p-3">Status</th><th className="p-3">Source</th></tr></thead><tbody>{rows.map(row => <tr key={row.recordId} className="border-b border-slate-100"><td className="p-3">{row.studentName || row.uid || "Student"}</td><td className="p-3">{row.date}</td><td className="p-3 font-semibold text-red-700">Absent</td><td className="p-3">{row.automaticAbsent ? "Automatic — no submission before deadline" : "Existing attendance record"}</td></tr>)}</tbody></table>{!rows.length && <p className="p-3 text-slate-500">No absent working days recorded in this period.</p>}</div>
      {total > rows.length && <p className="mt-3 text-xs text-slate-500">Showing {rows.length} records. Open attendance records to view all days.</p>}
    </>}
  </section>;
}
