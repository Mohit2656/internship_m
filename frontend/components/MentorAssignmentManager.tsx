"use client";

import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { api, ApiError } from "@/lib/api";
import { MentorAccount } from "@/lib/mentor-accounts";
import { AssignmentStudent, MentorAssignmentWorkspace } from "@/lib/mentor-assignments";

type Props = {
  mentor: MentorAccount;
  onClose: () => void;
  onCountChanged: (mentorUserId: number, count: number) => void;
};

function identity(student: AssignmentStudent) {
  return student.name || student.uid || `Student #${student.userId}`;
}

function StudentFacts({ student }: { student: AssignmentStudent }) {
  return (
    <span className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
      <span>UID: {student.uid || "Not available"}</span>
      <span>Branch: {student.branchLabel || "Not available"}</span>
      <span>Semester: {student.semester ?? "Not available"}</span>
      <span>Profile: {student.profileStatus === "COMPLETE" ? "Complete" : "Incomplete"}</span>
    </span>
  );
}

export function MentorAssignmentManager({ mentor, onClose, onCountChanged }: Props) {
  const [workspace, setWorkspace] = useState<MentorAssignmentWorkspace | null>(null);
  const [selected, setSelected] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true); setError("");
    api.get<MentorAssignmentWorkspace>(`/admin/mentors/${mentor.userId}/students`)
      .then(response => { if (active) setWorkspace(response.data); })
      .catch(() => { if (active) setError("Unable to load student assignments."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [mentor.userId]);

  function applyWorkspace(next: MentorAssignmentWorkspace) {
    setWorkspace(next);
    onCountChanged(mentor.userId, next.mentor.assignedStudentCount);
  }

  function toggle(studentUserId: number) {
    setSelected(current => current.includes(studentUserId)
      ? current.filter(id => id !== studentUserId)
      : [...current, studentUserId]);
  }

  async function assignSelected() {
    if (selected.length === 0) return;
    setSaving(true); setError(""); setSuccess("");
    try {
      const { data } = await api.post<MentorAssignmentWorkspace>(`/admin/mentors/${mentor.userId}/students`, { studentUserIds: selected });
      applyWorkspace(data); setSelected([]);
      setSuccess("Selected students were assigned successfully. Existing assignments were safely reassigned.");
    } catch (cause) {
      setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Unable to assign the selected students.");
    } finally { setSaving(false); }
  }

  async function remove(student: AssignmentStudent) {
    setSaving(true); setError(""); setSuccess("");
    try {
      const { data } = await api.delete<MentorAssignmentWorkspace>(`/admin/mentors/${mentor.userId}/students/${student.userId}`);
      applyWorkspace(data); setSuccess(`${identity(student)} is now unassigned. Assignment history was retained.`);
    } catch (cause) {
      setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Unable to remove the assignment.");
    } finally { setSaving(false); }
  }

  return (
    <section className="mt-8 rounded-2xl border border-blue-200 bg-blue-50/40 p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><p className="text-sm font-semibold text-blue-700">Student assignment</p><h2 className="mt-1 text-2xl font-bold">{mentor.name}</h2><p className="mt-1 text-sm text-slate-600">{mentor.department} · {mentor.designation}</p></div>
        <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold">Close</button>
      </div>
      {loading ? <p className="mt-6 text-sm text-slate-500">Loading assignments…</p> : workspace && (
        <>
          <div className="mt-6 grid gap-3 sm:grid-cols-3">
            <div className="rounded-xl bg-white p-4"><p className="text-xs uppercase tracking-wide text-slate-500">Total students</p><p className="mt-1 text-2xl font-bold">{workspace.totalStudents}</p></div>
            <div className="rounded-xl bg-white p-4"><p className="text-xs uppercase tracking-wide text-slate-500">Assigned</p><p className="mt-1 text-2xl font-bold">{workspace.assignedStudentsCount}</p></div>
            <div className="rounded-xl bg-white p-4"><p className="text-xs uppercase tracking-wide text-slate-500">Unassigned</p><p className="mt-1 text-2xl font-bold">{workspace.unassignedStudentsCount}</p></div>
          </div>
          {error && <p role="alert" className="mt-5 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
          {success && <p role="status" className="mt-5 rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{success}</p>}
          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <div className="rounded-xl border border-slate-200 bg-white p-5">
              <h3 className="font-bold">Currently assigned ({workspace.assignedStudents.length})</h3>
              {workspace.assignedStudents.length === 0 ? <p className="mt-4 text-sm text-slate-500">No students are assigned to this mentor.</p> : <ul className="mt-4 divide-y divide-slate-100">{workspace.assignedStudents.map(student => <li key={student.userId} className="flex items-start justify-between gap-4 py-4 first:pt-0 last:pb-0"><span><span className="font-semibold">{identity(student)}</span><StudentFacts student={student} /></span><button type="button" disabled={saving} onClick={() => void remove(student)} className="text-sm font-semibold text-red-700 disabled:opacity-50">Remove</button></li>)}</ul>}
            </div>
            <div className="rounded-xl border border-slate-200 bg-white p-5">
              <h3 className="font-bold">Available for assignment</h3>
              <p className="mt-1 text-xs text-slate-500">Students assigned elsewhere may be selected to reassign them transactionally.</p>
              {workspace.availableStudents.length === 0 ? <p className="mt-4 text-sm text-slate-500">No other students are available.</p> : <ul className="mt-4 max-h-96 divide-y divide-slate-100 overflow-y-auto pr-1">{workspace.availableStudents.map(student => <li key={student.userId} className="py-4 first:pt-0 last:pb-0"><label className="flex cursor-pointer items-start gap-3"><input type="checkbox" checked={selected.includes(student.userId)} onChange={() => toggle(student.userId)} className="mt-1 h-4 w-4" /><span><span className="font-semibold">{identity(student)}</span><StudentFacts student={student} />{student.currentMentorName && <span className="mt-1 block text-xs font-medium text-amber-700">Currently assigned to {student.currentMentorName}</span>}</span></label></li>)}</ul>}
              <button type="button" disabled={saving || selected.length === 0} onClick={() => void assignSelected()} className="mt-5 w-full rounded-xl bg-blue-700 px-4 py-3 font-semibold text-white disabled:opacity-50">{saving ? "Saving…" : `Assign Selected (${selected.length})`}</button>
            </div>
          </div>
        </>
      )}
      {!loading && error && !workspace && <p role="alert" className="mt-6 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
    </section>
  );
}
