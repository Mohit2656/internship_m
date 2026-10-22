"use client";

import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { api, ApiError } from "@/lib/api";
import { LateAttendancePermission } from "@/lib/attendance";

export function LateAttendancePermissions() {
  const [students, setStudents] = useState<LateAttendancePermission[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  async function loadPermissions() {
    setLoading(true); setError("");
    try {
      const { data } = await api.get<LateAttendancePermission[]>("/mentor/attendance/late-permissions");
      setStudents(data);
    } catch (cause) {
      setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Late attendance permissions could not be loaded.");
    } finally { setLoading(false); }
  }

  useEffect(() => { void loadPermissions(); }, []);

  async function setPermission(student: LateAttendancePermission, enabled: boolean) {
    if (savingId !== null) return;
    setSavingId(student.studentUserId); setError(""); setNotice("");
    try {
      const { data } = await api.put<LateAttendancePermission>(`/mentor/attendance/late-permissions/${student.studentUserId}`, { enabled });
      setStudents(current => current.map(item => item.studentUserId === data.studentUserId ? data : item));
      setNotice(`Late attendance permission ${data.enabled ? "enabled" : "disabled"} for ${data.studentName || data.uid || "the student"} on ${data.attendanceDate}.`);
    } catch (cause) {
      setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Late attendance permission could not be changed. Please try again.");
    } finally { setSavingId(null); }
  }

  return <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-7" aria-labelledby="late-attendance-permission-title">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h2 id="late-attendance-permission-title" className="text-xl font-bold">Late Attendance Permission</h2>
      <button type="button" data-button-variant="text" disabled={loading || savingId !== null} onClick={() => void loadPermissions()} className="text-sm">Refresh permissions</button>
    </div>
    <p className="mt-2 text-sm text-slate-600">Allow an assigned student to submit after 12:00 PM for the displayed attendance date only. Permission expires at the end of that day in the attendance time zone. Students must still submit attendance and complete the required validation.</p>
    {notice && <p role="status" className="mt-4 rounded-lg bg-blue-50 px-4 py-3 text-sm text-blue-800">{notice}</p>}
    {error && <p role="alert" className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
    {loading ? <p className="mt-5 text-sm text-slate-500">Loading late attendance permissions...</p> : students.length === 0 ? <p className="mt-5 text-sm text-slate-500">No students are currently assigned.</p> : <div className="mt-5 overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b text-xs uppercase tracking-wide text-slate-500"><tr><th className="pb-3 pr-4">Student</th><th className="pb-3 pr-4">Enrollment ID</th><th className="pb-3 pr-4">Effective date</th><th className="pb-3 pr-4">Status</th><th className="pb-3">Permission</th></tr></thead>
        <tbody>{students.map(student => <tr key={student.studentUserId} className="border-b border-slate-100 last:border-0">
          <td className="py-4 pr-4 font-semibold">{student.studentName || "Profile incomplete"}</td>
          <td className="py-4 pr-4">{student.uid || "Enrollment ID unavailable"}</td>
          <td className="py-4 pr-4">{student.attendanceDate}<span className="mt-1 block text-xs text-slate-500">{student.attendanceZone}</span></td>
          <td className={`py-4 pr-4 font-medium ${student.enabled ? "text-blue-700" : "text-slate-500"}`}>{student.enabled ? "Enabled" : "Disabled"}</td>
          <td className="py-4"><label className={`inline-flex items-center gap-2 ${savingId !== null ? "cursor-wait" : "cursor-pointer"}`}>
            <input type="checkbox" role="switch" checked={student.enabled} disabled={savingId !== null} onChange={event => void setPermission(student, event.target.checked)} aria-label={`Late attendance permission for ${student.studentName || student.uid || "student"} on ${student.attendanceDate}`} className="peer sr-only" />
            <span aria-hidden="true" className="flex h-7 w-12 shrink-0 items-center rounded-full bg-slate-300 p-1 transition-colors peer-checked:bg-blue-600 peer-focus-visible:ring-2 peer-focus-visible:ring-blue-600 peer-focus-visible:ring-offset-2 peer-disabled:opacity-60"><span className={`h-5 w-5 rounded-full bg-white shadow-sm transition-transform ${student.enabled ? "translate-x-5" : "translate-x-0"}`} /></span>
            {savingId === student.studentUserId && <span className="text-xs text-slate-500">Saving...</span>}
          </label></td>
        </tr>)}</tbody>
      </table>
    </div>}
  </section>;
}
