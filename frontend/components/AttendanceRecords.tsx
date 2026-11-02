"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { StudentAttendanceHistory, type StudentAttendanceSummary } from "@/components/AttendanceAbsenceOverview";
import { DashboardSection } from "@/components/DashboardLayout";

export function AttendanceRecords({ role }: { role: "ADMIN" | "MENTOR" }) {
  const [students, setStudents] = useState<StudentAttendanceSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);

  useEffect(() => {
    const abort = new AbortController();
    setLoading(true); setError(""); setStudents([]); setSelectedId(null); setSearch("");
    api.get<StudentAttendanceSummary[]>(`/${role.toLowerCase()}/attendance/students`, { signal: abort.signal })
      .then(({ data }) => { if (!abort.signal.aborted) setStudents(data); })
      .catch(() => { if (!abort.signal.aborted) setError("Students could not be loaded. Access may have changed."); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [role]);

  const query = search.trim().toLowerCase();
  const matching = [...students].sort((a, b) => a.studentUserId - b.studentUserId)
    .filter(student => `${student.studentName ?? ""} ${student.uid ?? ""} ${student.studentUserId}`.toLowerCase().includes(query));
  const selected = students.find(student => student.studentUserId === selectedId);
  const studentLabel = (student: StudentAttendanceSummary) => student.studentName?.trim() || student.uid?.trim() || `Student ${student.studentUserId}`;

  return <DashboardSection className="mt-6" title="Student attendance records" description={role === "MENTOR" ? "Select an assigned student to inspect individual submissions and manage attendance decisions." : "Select a student to inspect individual attendance records and mentor decisions."}>
    {error && <p role="alert" className="mt-4 text-red-700">{error}</p>}
    {loading ? <p role="status" className="mt-4 text-sm text-slate-500">Loading students...</p> : !error && <>
      <div className="mt-5 grid min-w-0 gap-4 md:grid-cols-2">
        <label className="min-w-0 text-sm font-semibold text-slate-900">Search by name, enrollment number or account ID<input type="search" value={search} onChange={event => { setSearch(event.target.value); setSelectedId(null); }} className="input mt-2 w-full min-w-0 font-normal" /></label>
        <label className="min-w-0 text-sm font-semibold text-slate-900">Student<select value={selectedId ?? ""} onChange={event => setSelectedId(event.target.value ? Number(event.target.value) : null)} className="input mt-2 w-full min-w-0 font-normal"><option value="">Select a student</option>{matching.map(student => <option key={student.studentUserId} value={student.studentUserId}>{studentLabel(student)} · {student.uid || "Enrollment unavailable"} · Account {student.studentUserId}</option>)}</select></label>
      </div>
      {!matching.length && <p className="mt-4 text-sm text-slate-500">{students.length ? "No matching students." : role === "MENTOR" ? "No students are currently assigned." : "No students are available."}</p>}
      {selected ? <>
        <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="break-words font-bold text-slate-950">{studentLabel(selected)}</h3><Link className="text-sm font-semibold text-blue-700 hover:underline" href={`/${role.toLowerCase()}/students/${selected.studentUserId}`}>View student profile</Link></div>
          <dl className="mt-3 grid min-w-0 gap-3 text-sm sm:grid-cols-2 xl:grid-cols-5">{[
            ["Account ID", selected.studentUserId], ["UID / Enrollment", selected.uid || "Unavailable"], ["Branch", "Computer Engineering"], ["Section", selected.section ? `Section ${selected.section}` : "Section needs review"], ["Semester", "8th Semester"], ["Company", selected.company || "Unavailable"], ...(role === "ADMIN" ? [["Faculty mentor", selected.facultyMentor || "Unassigned"]] : []),
          ].map(([label, value]) => <div key={label} className="min-w-0"><dt className="text-xs font-semibold text-slate-500">{label}</dt><dd className="mt-1 break-words text-slate-900">{value}</dd></div>)}</dl>
        </div>
        <StudentAttendanceHistory key={`${role}-${selected.studentUserId}`} role={role} studentId={selected.studentUserId} name={studentLabel(selected)} onBack={() => setSelectedId(null)} showSummary={false} recordFilters />
      </> : matching.length > 0 && <p className="mt-5 text-sm text-slate-500">Choose a student to view their saved attendance records.</p>}
    </>}
  </DashboardSection>;
}
