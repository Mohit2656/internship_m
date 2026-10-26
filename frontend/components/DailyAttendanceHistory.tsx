"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { Attendance, attendanceLabel, isAutomaticAbsence } from "@/lib/attendance";
import { LocationVerificationResult } from "@/components/LocationVerificationResult";

export function DailyAttendanceHistory() {
  const [records, setRecords] = useState<Attendance[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    const abort = new AbortController();
    api.get<Attendance[]>("/student/attendance", { signal: abort.signal })
      .then(({ data }) => setRecords([...data].sort((a, b) => b.attendanceDate.localeCompare(a.attendanceDate))))
      .catch(() => { if (!abort.signal.aborted) setError("Daily attendance history could not be loaded."); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, []);
  const absent = records.filter(record => record.attendanceResult === "ABSENT").length;
  return <section className="mt-5 rounded-2xl border border-slate-200 bg-white p-7">
    <h2 className="text-xl font-bold">Daily Attendance History</h2>
    <p className="mt-2 text-sm text-slate-600">Each record covers one working day. Automatic absences are included in your attendance totals.</p>
    {loading ? <p className="mt-4 text-sm text-slate-500">Loading attendance history…</p> : error ? <p role="alert" className="mt-4 text-sm text-red-700">{error}</p> : <>
      <p className="mt-4 text-sm font-semibold">{records.length} daily records · {records.length - absent} present · {absent} absent</p>
      <div className="mt-4 max-h-96 overflow-auto"><table className="w-full text-left text-sm"><thead><tr className="border-b"><th className="p-3">Date</th><th className="p-3">Attendance</th><th className="p-3">Recorded by</th></tr></thead><tbody>{records.map(record => <tr key={record.id} className="border-b border-slate-100"><td className="p-3">{record.attendanceDate}</td><td className={`p-3 ${record.attendanceResult === "ABSENT" ? "font-semibold text-red-700" : "text-slate-700"}`}>{attendanceLabel(record)}{record.attendanceResult === "PRESENT" && <LocationVerificationResult evidence={record.evidence}/>}</td><td className="p-3">{isAutomaticAbsence(record) ? "Automatic — no submission before deadline" : "Student submission"}</td></tr>)}</tbody></table>{records.length === 0 && <p className="p-3 text-slate-500">No daily attendance records yet.</p>}</div>
    </>}
  </section>;
}
