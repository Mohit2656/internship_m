"use client";

import Link from "next/link";
import { Fragment, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { Attendance, isAutomaticAbsence, livenessLabel } from "@/lib/attendance";
import { MentorRecordDelete } from "@/components/MentorRecordDelete";
import { AttendanceLocations } from "@/components/AttendanceLocations";
import { LocationVerificationResult } from "@/components/LocationVerificationResult";
import { AttendanceReviewPanel } from "@/components/AttendanceReviewPanel";

export type AttendanceTotals = { presentDays: number; absentDays: number; totalDays: number; percentage: number | null };
export type StudentAttendanceSummary = { studentUserId: number; uid: string | null; studentName: string | null; branch: string | null; facultyMentor: string | null; company: string | null; workflowStatus: string; attendance: AttendanceTotals; reviewSummary?: { submitted: number; gpsVerified: number; pending: number; approved: number; rejected: number; manualReview: number } };
export const formatAttendancePercentage = (value: number | null) => (value == null || isNaN(value)) ? "—" : `${value.toFixed(1)}%`;

export type InternshipSummary = { id: number; internshipNumber: number; companyName: string | null; internshipMode: string | null };
type History = {
  studentUserId: number;
  attendanceZone: string;
  totals: AttendanceTotals;
  records: Attendance[];
  internships?: InternshipSummary[];
  selectedInternshipId?: number | null;
};
export function StudentAttendanceHistory({ role, studentId, name, onBack, onChanged, id }: { role: "ADMIN" | "MENTOR"; studentId: number; name: string; onBack: () => void; onChanged?: () => void; id?: string }) {
  const [history, setHistory] = useState<History | null>(null);
  const [selectedInternshipId, setSelectedInternshipId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const abort = new AbortController(); setError("");
    const params = selectedInternshipId ? { internshipId: selectedInternshipId } : undefined;
    api.get<History>(`/${role.toLowerCase()}/attendance/students/${studentId}`, { signal: abort.signal, params })
      .then(({ data }) => {
        if (!abort.signal.aborted) {
          setHistory(data);
          if (data.selectedInternshipId && selectedInternshipId !== data.selectedInternshipId) {
            setSelectedInternshipId(data.selectedInternshipId);
          }
        }
      })
      .catch(() => { if (!abort.signal.aborted) setError("Attendance history could not be loaded. Access may have changed."); });
    return () => abort.abort();
  }, [role, studentId, selectedInternshipId, revision]);

  const activeId = history?.selectedInternshipId ?? selectedInternshipId ?? (history?.internships?.[0]?.id ?? null);
  const activeInternship = history?.internships?.find(i => i.id === activeId);

  return <section id={id} className="mt-5 w-full max-w-full overflow-hidden rounded-xl border border-slate-200 bg-white p-5" aria-label={`Attendance history for ${name}`}>
    <button type="button" data-button-variant="outline" onClick={onBack} className="text-sm">Back to student list</button>
    <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
      <h3 className="text-lg font-bold text-slate-950">
        Attendance history: {name}
        {activeInternship && history?.internships && history.internships.length > 1 && (
          <span className="ml-2 text-sm font-semibold text-blue-700">
            (Internship {activeInternship.internshipNumber}{activeInternship.companyName ? ` — ${activeInternship.companyName}` : ""})
          </span>
        )}
      </h3>
    </div>

    {history?.internships && history.internships.length > 1 && (
      <div className="mt-4 flex flex-wrap gap-2 border-b border-slate-200 pb-3" role="tablist" aria-label="Internship tabs">
        {history.internships.map(internship => {
          const active = internship.id === activeId;
          return (
            <button
              key={internship.id}
              type="button"
              role="tab"
              aria-selected={active}
              onClick={() => setSelectedInternshipId(internship.id)}
              className={`rounded-lg px-4 py-2 text-sm font-semibold transition-colors ${
                active
                  ? "border border-blue-300 bg-blue-50 text-blue-900 shadow-sm"
                  : "border border-transparent bg-slate-100 text-slate-700 hover:bg-slate-200 hover:text-slate-900"
              }`}
            >
              Internship {internship.internshipNumber}{internship.companyName ? ` — ${internship.companyName}` : ""}
            </button>
          );
        })}
      </div>
    )}

    {error ? <p role="alert" className="mt-4 text-red-700">{error}</p> : !history ? <p role="status" className="mt-4 text-slate-500">Loading attendance history...</p> : <>
      <p className="mt-3 font-semibold text-slate-900">{history.totals.presentDays} present · {history.totals.absentDays} absent · {history.totals.totalDays} applicable recorded days · {formatAttendancePercentage(history.totals.percentage)}</p>
      <p className="mt-2 text-xs text-slate-500">Totals exclude future dates, weekends, configured holidays, approved leave and unscheduled days. Times use {history.attendanceZone}.</p>
      <div className="mt-4 w-full overflow-x-auto"><table className="w-full min-w-[950px] border-collapse text-left text-sm text-slate-900"><thead><tr className="border-b border-slate-200">{["Date", "Status", "Submission time", "Liveness", "Late submission", "Mentor approval", ...(role === "MENTOR" ? ["Actions"] : [])].map(label => <th key={label} className="p-3 font-semibold text-slate-900 whitespace-nowrap">{label}</th>)}</tr></thead>
        <tbody>{history.records.map(record => <tr key={record.id} className="border-t border-slate-100 align-top"><td className="p-3 text-slate-900 whitespace-nowrap">{record.attendanceDate}</td><td className="p-3 text-slate-900"><p className="font-semibold text-slate-900">{record.attendanceResult}</p><LocationVerificationResult evidence={record.evidence} />{record.evidence?.dailyWorkSummary && <p className="mt-2 whitespace-pre-wrap text-xs text-slate-600">{record.evidence.dailyWorkSummary}</p>}</td><td className="p-3 text-slate-900">{isAutomaticAbsence(record) ? "— (automatic absence)" : record.serverSubmittedAt ? new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short", timeZone: history.attendanceZone }).format(new Date(record.serverSubmittedAt)) : "—"}<AttendanceLocations evidence={record.evidence} /></td><td className="p-3 text-slate-900 whitespace-nowrap">{livenessLabel(record.livenessStatus)}</td><td className="p-3 text-slate-900 whitespace-nowrap">{record.submittedLate ? "Yes · mentor permission" : "—"}</td><td className="p-3 text-slate-900"><AttendanceReviewPanel record={record} role={role} onChanged={() => { setRevision(value => value + 1); onChanged?.(); }} /></td>{role === "MENTOR" && <td className="p-3 text-slate-900"><MentorRecordDelete kind="attendance" id={record.id} description={`${name} · ${record.attendanceDate}`} onDeleted={() => { setRevision(value => value + 1); onChanged?.(); }} /></td>}</tr>)}</tbody>
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
  const sorted = [...rows].sort((a, b) => a.studentUserId - b.studentUserId);
  const filtered = sorted.filter(row => `${row.studentName ?? ""} ${row.uid ?? ""}`.toLowerCase().includes(query));
  const headers = role === "MENTOR"
    ? ["", "UID", "Student Name", "Branch", "Company", "Attendance %"]
    : ["", "UID", "Student Name", "Branch", "Faculty Mentor", "Company", "Attendance %", "Mentor Review", "Workflow Status"];
  return <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
    <h2 className="text-xl font-bold text-slate-950 dark:text-slate-100">{role === "MENTOR" ? "Assigned Students" : "Student Attendance"}</h2>
    <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">Click the chevron next to a student to expand or collapse attendance history. Attendance percentage uses present and absent records on applicable working days.</p>
    <label className="mt-4 block text-sm font-semibold text-slate-900 dark:text-slate-200">Search by name or enrollment number<input value={search} onChange={event => setSearch(event.target.value)} className="mt-2 block w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 font-normal text-slate-900 sm:max-w-md dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100" /></label>
    {error && <p role="alert" className="mt-4 text-red-700 dark:text-red-400">{error}</p>}
    {loading ? <p role="status" className="mt-4 text-slate-500 dark:text-slate-400">Loading students...</p> : (!error || rows.length > 0) && <div className="mt-4 w-full overflow-x-auto min-h-[300px]"><table className="w-full min-w-[760px] text-left text-sm text-slate-900 dark:text-slate-100"><thead><tr className="border-b border-slate-200 text-xs font-semibold uppercase tracking-wider text-slate-600 dark:border-slate-800 dark:text-slate-400">{headers.map((label, idx) => <th key={label || `col-${idx}`} className={`break-words p-3 ${idx === 0 ? "w-10 text-center" : ""}`}>{label}</th>)}</tr></thead>
      <tbody className="divide-y divide-slate-100 dark:divide-slate-800">{filtered.map(row => {
        const expanded = selected?.studentUserId === row.studentUserId;
        const historyId = `attendance-history-${role.toLowerCase()}-${row.studentUserId}`;
        const displayUid = row.uid && !row.uid.includes("@") && row.uid.trim() ? row.uid.trim() : "Not provided";
        const displayMentor = row.facultyMentor && row.facultyMentor.trim() ? row.facultyMentor.trim() : "Not assigned";
        const pct = row.attendance?.percentage;
        const isLow = pct !== null && pct !== undefined && !isNaN(pct) && pct < 75;

        return <Fragment key={row.studentUserId}>
          <tr className="transition-colors hover:bg-slate-50/70 dark:hover:bg-slate-800/50 align-middle">
            <td className="p-3 text-center w-10">
              <button
                type="button"
                aria-expanded={expanded}
                aria-controls={historyId}
                aria-label={`${expanded ? "Collapse" : "Expand"} attendance history for ${row.studentName?.trim() || displayUid}`}
                className="chevron-btn"
                onClick={() => setSelected(current => current?.studentUserId === row.studentUserId ? null : row)}
              >
                <span aria-hidden="true">{expanded ? "▾" : "▸"}</span>
              </button>
            </td>
            <td className="break-words p-3 font-medium text-slate-900 dark:text-slate-200">{displayUid}</td>
            <td className="break-words p-3 font-semibold">
              <Link
                href={`/${role.toLowerCase()}/students/${row.studentUserId}${role === "ADMIN" ? "?from=attendance" : ""}`}
                className="text-slate-950 hover:text-blue-700 hover:underline dark:text-slate-100 dark:hover:text-blue-400"
              >
                {row.studentName?.trim() || "Name unavailable"}
              </Link>
            </td>
            <td className="break-words p-3 text-slate-600 dark:text-slate-400">{row.branch || "—"}</td>
            {role === "MENTOR" ? (
              <>
                <td className="break-words p-3 text-slate-600 dark:text-slate-400">{row.company || "—"}</td>
                <td className="p-3 whitespace-nowrap">
                  <span className={isLow ? "font-semibold text-amber-700 dark:text-amber-400" : "font-semibold text-slate-900 dark:text-slate-100"}>
                    {formatAttendancePercentage(pct)}
                  </span>
                  {isLow && (
                    <span className="ml-1.5 inline-block rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-800 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800">
                      Low
                    </span>
                  )}
                </td>
              </>
            ) : (
              <>
                <td className="break-words p-3 text-slate-700 dark:text-slate-300">{displayMentor}</td>
                <td className="break-words p-3 text-slate-600 dark:text-slate-400">{row.company || "—"}</td>
                <td className="p-3 whitespace-nowrap">
                  <span className={isLow ? "font-semibold text-amber-700 dark:text-amber-400" : "font-semibold text-slate-900 dark:text-slate-100"}>
                    {formatAttendancePercentage(pct)}
                  </span>
                  {isLow && (
                    <span className="ml-1.5 inline-block rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-800 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800">
                      Low
                    </span>
                  )}
                </td>
                <td className="p-3 text-xs align-middle">
                  {row.reviewSummary ? (
                    <div className="space-y-1">
                      <div className="font-medium">
                        {row.reviewSummary.pending > 0 ? (
                          <span className="font-semibold text-amber-700 dark:text-amber-400">
                            {row.reviewSummary.gpsVerified > 0 ? `${row.reviewSummary.gpsVerified} Verified · ` : ""}
                            {row.reviewSummary.pending} Pending {row.reviewSummary.pending === 1 ? "Review" : "Reviews"}
                          </span>
                        ) : row.reviewSummary.submitted === 0 ? (
                          <span className="text-slate-500 dark:text-slate-400">No submissions</span>
                        ) : (
                          <span className="text-emerald-700 dark:text-emerald-400 font-semibold">
                            {row.reviewSummary.approved} Approved
                            {row.reviewSummary.rejected > 0 ? ` · ${row.reviewSummary.rejected} Rejected` : ""}
                          </span>
                        )}
                      </div>
                      <details className="group relative inline-block">
                        <summary className="cursor-pointer text-[11px] font-semibold text-blue-700 hover:underline dark:text-blue-400 list-none inline-flex items-center gap-0.5">
                          <span>View Details</span>
                          <span className="text-[9px] transition-transform group-open:rotate-180">▾</span>
                        </summary>
                        <div className="absolute left-0 top-full z-30 mt-1.5 w-64 rounded-xl border border-slate-200 bg-white p-3 shadow-xl dark:border-slate-700 dark:bg-slate-800">
                          <p className="text-[11px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400 mb-2">Review Breakdown</p>
                          <div className="space-y-1.5 text-xs text-slate-700 dark:text-slate-300">
                            <div className="flex justify-between items-center py-0.5 border-b border-slate-100 dark:border-slate-700/60">
                              <span className="text-slate-600 dark:text-slate-400">Submitted Records</span>
                              <span className="font-semibold text-slate-900 dark:text-slate-100">{row.reviewSummary.submitted}</span>
                            </div>
                            <div className="flex justify-between items-center py-0.5 border-b border-slate-100 dark:border-slate-700/60">
                              <span className="text-slate-600 dark:text-slate-400">GPS Verified</span>
                              <span className="font-semibold text-blue-700 dark:text-blue-400">{row.reviewSummary.gpsVerified}</span>
                            </div>
                            <div className="flex justify-between items-center py-0.5 border-b border-slate-100 dark:border-slate-700/60">
                              <span className="text-slate-600 dark:text-slate-400">Pending Reviews</span>
                              <span className="font-semibold text-amber-700 dark:text-amber-400">{row.reviewSummary.pending}</span>
                            </div>
                            <div className="flex justify-between items-center py-0.5 border-b border-slate-100 dark:border-slate-700/60">
                              <span className="text-slate-600 dark:text-slate-400">Manual Reviews</span>
                              <span className="font-semibold text-slate-700 dark:text-slate-300">{row.reviewSummary.manualReview}</span>
                            </div>
                            <div className="flex justify-between items-center py-0.5 border-b border-slate-100 dark:border-slate-700/60">
                              <span className="text-slate-600 dark:text-slate-400">Approved Records</span>
                              <span className="font-semibold text-emerald-700 dark:text-emerald-400">{row.reviewSummary.approved}</span>
                            </div>
                            <div className="flex justify-between items-center py-0.5">
                              <span className="text-slate-600 dark:text-slate-400">Rejected Records</span>
                              <span className="font-semibold text-red-600 dark:text-red-400">{row.reviewSummary.rejected}</span>
                            </div>
                          </div>
                        </div>
                      </details>
                    </div>
                  ) : (
                    <span className="text-slate-400">Not available</span>
                  )}
                </td>
                <td className="break-words p-3 text-slate-700 dark:text-slate-300">{row.workflowStatus.replaceAll("_", " ")}</td>
              </>
            )}
          </tr>
          {expanded && <tr><td colSpan={role === "MENTOR" ? 6 : 9} className="max-w-0 px-3 pb-5"><StudentAttendanceHistory key={row.studentUserId} id={historyId} role={role} studentId={row.studentUserId} name={row.studentName || displayUid} onBack={() => setSelected(null)} onChanged={() => setRevision(value => value + 1)} /></td></tr>}
        </Fragment>;
      })}</tbody>
    </table>{!filtered.length && <p className="p-3 text-slate-500 dark:text-slate-400">No matching students.</p>}</div>}
  </section>;
}
