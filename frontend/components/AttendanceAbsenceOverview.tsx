"use client";

import Link from "next/link";
import { Fragment, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { Attendance, isAutomaticAbsence, livenessLabel } from "@/lib/attendance";
import { MentorRecordDelete } from "@/components/MentorRecordDelete";
import { AttendanceLocations } from "@/components/AttendanceLocations";

export type AttendanceTotals = { presentDays: number; absentDays: number; totalDays: number; percentage: number | null };
export type StudentAttendanceSummary = { studentUserId: number; uid: string | null; studentName: string | null; branch: string | null; facultyMentor: string | null; company: string | null; workflowStatus: string; attendance: AttendanceTotals };
export const formatAttendancePercentage = (value: number | null) => value == null ? "?" : `${value.toFixed(1)}%`;

type History = { studentUserId: number; attendanceZone: string; totals: AttendanceTotals; records: Attendance[] };
export function StudentAttendanceHistory({ role, studentId, name, onBack, onChanged, id }: { role: "ADMIN" | "MENTOR"; studentId: number; name: string; onBack: () => void; onChanged?: () => void; id?: string }) {
  const [history, setHistory] = useState<History | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const abort = new AbortController(); setHistory(null); setError("");
    api.get<History>(`/${role.toLowerCase()}/attendance/students/${studentId}`, { signal: abort.signal })
      .then(({ data }) => { if (!abort.signal.aborted) setHistory(data); })
      .catch(() => { if (!abort.signal.aborted) setError("Attendance history could not be loaded. Access may have changed."); });
    return () => abort.abort();
  }, [role, studentId, revision]);
  return <section id={id} className="mt-5 rounded-xl border border-blue-200 bg-white p-5" aria-label={`Attendance history for ${name}`}>
    <button type="button" data-button-variant="outline" onClick={onBack} className="text-sm">Back to student list</button>
    <h3 className="mt-4 text-lg font-bold">Attendance history: {name}</h3>
    {error ? <p role="alert" className="mt-4 text-red-700">{error}</p> : !history ? <p role="status" className="mt-4">Loading attendance history...</p> : <>
      <p className="mt-3 font-semibold">{history.totals.presentDays} present ? {history.totals.absentDays} absent ? {history.totals.totalDays} applicable recorded days ? {formatAttendancePercentage(history.totals.percentage)}</p>
      <p className="mt-2 text-xs text-slate-500">Totals exclude future dates, weekends, configured holidays, approved leave and unscheduled days. Times use {history.attendanceZone}.</p>
      <div className="mt-4 overflow-x-auto"><table className="w-full min-w-[650px] text-left text-sm"><thead><tr>{["Date", "Status", "Submission time", "Liveness", "Late submission", ...(role === "MENTOR" ? ["Actions"] : [])].map(label => <th key={label} className="p-3">{label}</th>)}</tr></thead>
        <tbody>{history.records.map(record => <tr key={record.id} className="border-t border-slate-100"><td className="p-3">{record.attendanceDate}</td><td className="p-3 font-semibold">{record.attendanceResult}</td><td className="p-3">{isAutomaticAbsence(record) ? "? (automatic absence)" : record.serverSubmittedAt ? new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short", timeZone: history.attendanceZone }).format(new Date(record.serverSubmittedAt)) : "?"}<AttendanceLocations evidence={record.evidence} /></td><td className="p-3">{livenessLabel(record.livenessStatus)}</td><td className="p-3">{record.submittedLate ? "Yes ? mentor permission" : "?"}</td>{role === "MENTOR" && <td className="p-3"><MentorRecordDelete kind="attendance" id={record.id} description={`${name} ? ${record.attendanceDate}`} onDeleted={() => { setRevision(value => value + 1); onChanged?.(); }} /></td>}</tr>)}</tbody>
      </table>{!history.records.length && <p className="p-3 text-slate-500">No attendance records yet.</p>}</div>
    </>}
  </section>;
}

export function AttendanceAbsenceOverview({ role }: { role: "ADMIN" | "MENTOR" }) {
  const [rows, setRows] = useState<StudentAttendanceSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<StudentAttendanceSummary | null>(null);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    setError("");
    api.get<StudentAttendanceSummary[]>(`/${role.toLowerCase()}/attendance/students`, { signal: abort.signal })
      .then(({ data }) => { if (!abort.signal.aborted) setRows(data); })
      .catch(() => { if (!abort.signal.aborted) setError("Student attendance summaries could not be loaded."); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [role, revision]);
  const query = search.trim().toLowerCase();
  const filtered = rows.filter(row => `${row.studentName ?? ""} ${row.uid ?? ""}`.toLowerCase().includes(query));
  return <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6">
    <h2 className="text-xl font-bold">{role === "MENTOR" ? "Assigned Students" : "Student Attendance"}</h2>
    <p className="mt-2 text-sm text-slate-600">Click a student's name to expand or collapse attendance history. Attendance percentage uses present and absent records on applicable working days.</p>
    <label className="mt-4 block text-sm font-semibold">Search by name or enrollment number<input value={search} onChange={event => setSearch(event.target.value)} className="mt-2 block w-full rounded-xl border border-slate-300 px-4 py-2.5 font-normal sm:max-w-md" /></label>
    {error && <p role="alert" className="mt-4 text-red-700">{error}</p>}
    {loading ? <p role="status" className="mt-4">Loading students...</p> : (!error || rows.length > 0) && <div className="mt-4 overflow-x-auto"><table className="w-full min-w-[1000px] table-fixed text-left text-sm"><thead><tr>{["Student Account ID", "UID / Enrollment", "Student Name", "Branch", "Faculty Mentor", "Company", "Attendance Percentage", "Workflow Status"].map(label => <th key={label} className="break-words p-3">{label}</th>)}</tr></thead>
      <tbody>{filtered.map(row => {
        const expanded = selected?.studentUserId === row.studentUserId;
        const historyId = `attendance-history-${role.toLowerCase()}-${row.studentUserId}`;
        return <Fragment key={row.studentUserId}>
          <tr className="border-t border-slate-100 align-top">
            <td className="break-words p-3">{row.studentUserId}</td>
            <td className="break-words p-3">{row.uid || "?"}</td>
            <td className="break-words p-3"><button type="button" data-button-variant="text" aria-expanded={expanded} aria-controls={historyId} className="text-left font-semibold text-blue-700 hover:underline" onClick={() => setSelected(current => current?.studentUserId === row.studentUserId ? null : row)}><span aria-hidden="true" className="mr-2 inline-block">{expanded ? "▾" : "▸"}</span>{row.studentName || row.uid || "View Attendance"}</button><Link className="mt-2 block text-xs text-blue-700 hover:underline" href={`/${role.toLowerCase()}/students/${row.studentUserId}`}>View student profile</Link></td>
            <td className="break-words p-3">{row.branch || "?"}</td>
            <td className="break-words p-3">{row.facultyMentor || "?"}</td>
            <td className="break-words p-3">{row.company || "?"}</td>
            <td className="p-3 font-semibold text-blue-700">{formatAttendancePercentage(row.attendance.percentage)}</td>
            <td className="break-words p-3">{row.workflowStatus.replaceAll("_", " ")}</td>
          </tr>
          {expanded && <tr><td colSpan={8} className="px-3 pb-5"><StudentAttendanceHistory key={row.studentUserId} id={historyId} role={role} studentId={row.studentUserId} name={row.studentName || row.uid || "Student"} onBack={() => setSelected(null)} onChanged={() => setRevision(value => value + 1)} /></td></tr>}
        </Fragment>;
      })}</tbody>
    </table>{!filtered.length && <p className="p-3 text-slate-500">No matching students.</p>}</div>}
  </section>;
}
