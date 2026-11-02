"use client";

import Link from "next/link";
import { Fragment, useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { MentorAccount } from "@/lib/mentor-accounts";
import { StudentAttendanceSummary, formatAttendancePercentage } from "@/components/AttendanceAbsenceOverview";
import { DashboardReportExport } from "@/components/DashboardReportExport";
import { DashboardPage, DashboardSection } from "@/components/DashboardLayout";

function MentorDashboardContent() {
  const [mentor, setMentor] = useState<MentorAccount | null>(null);
  const [students, setStudents] = useState<StudentAttendanceSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [section, setSection] = useState("");

  useEffect(() => {
    Promise.all([
      api.get<MentorAccount>("/mentor/profile"),
      api.get<StudentAttendanceSummary[]>("/mentor/attendance/students")
    ]).then(([profile, assigned]) => {
      setMentor(profile.data); setStudents(assigned.data);
    }).catch(() => setError("Unable to load your mentor dashboard."))
      .finally(() => setLoading(false));
  }, []);
  const sortedStudents = students.filter(student => !section || (section === "UNASSIGNED" ? !student.section : student.section === section)).sort((left, right) => {
    const sectionOrder = (left.section ?? "Z").localeCompare(right.section ?? "Z");
    if (sectionOrder) return sectionOrder;
    if (!left.uid) return right.uid ? 1 : left.studentUserId - right.studentUserId;
    if (!right.uid) return -1;
    return left.uid.localeCompare(right.uid, undefined, { numeric: true, sensitivity: "base" });
  });

  return (
    <DashboardPage
      eyebrow="Mentor workspace"
      title="Mentor Dashboard"
      description="Review your assigned students, internship details and attendance."
      actions={!loading && mentor && !error ? <DashboardReportExport role="MENTOR" /> : undefined}
    >
      {error ? <p role="alert" className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p> : loading || !mentor ? <p role="status" className="mt-6 text-sm text-slate-500">Loading mentor dashboard…</p> : (
        <>
          <DashboardSection title="Mentor Profile" className="mt-6">
            <div className="mt-5 grid min-w-0 gap-4 break-words text-sm sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5 [&>div]:min-w-0">
              <div><p className="text-slate-500">Name</p><p className="mt-1 font-semibold">{mentor.name}</p></div>
              <div><p className="text-slate-500">Employee ID</p><p className="mt-1 font-semibold">{mentor.employeeId}</p></div>
              <div><p className="text-slate-500">Department</p><p className="mt-1 font-semibold">{mentor.department}</p></div>
              <div><p className="text-slate-500">Designation</p><p className="mt-1 font-semibold">{mentor.designation}</p></div>
              <div><p className="text-slate-500">Assigned Students</p><p className="mt-1 text-2xl font-bold">{mentor.assignedStudentCount}</p></div>
            </div>
          </DashboardSection>
          <DashboardSection
            id="assigned-students"
            title="Assigned Students"
            description="Open a student profile for internship and workplace review. Attendance percentages use applicable recorded working days."
            className="mt-6"
          >
            <label className="mt-5 block max-w-xs text-sm font-semibold">Section<select aria-label="Section" className="input mt-2" value={section} onChange={event => setSection(event.target.value)}><option value="">All sections</option><option value="A">Section A</option><option value="B">Section B</option><option value="UNASSIGNED">Section needs review</option></select></label>
            <div className="mt-5 overflow-x-auto"><table className="w-full min-w-[680px] text-left text-sm"><thead><tr>{["UID", "Student Name", "Academic details", "Company", "Attendance %"].map(label => <th key={label} className="p-3">{label}</th>)}</tr></thead>
              <tbody>{sortedStudents.map((student, index) => <Fragment key={student.studentUserId}>{(index === 0 || sortedStudents[index - 1].section !== student.section) && <tr className="bg-slate-50"><th colSpan={5} scope="colgroup" className="px-3 py-2 text-xs font-semibold text-slate-600">{student.section ? `Section ${student.section}` : "Section needs review"}</th></tr>}<tr className="border-t border-slate-100"><td className="p-3 font-medium">{student.uid || "—"}</td><td className="p-3"><Link href={`/mentor/students/${student.studentUserId}`} className="font-semibold text-slate-950 hover:underline">{student.studentName || "Name unavailable"}</Link></td><td className="p-3">Computer Engineering<p className="mt-1 text-xs text-slate-500">{student.section ? `Section ${student.section}` : "Section needs review"} · 8th Semester</p></td><td className="p-3">{student.company || "—"}</td><td className="p-3 font-semibold text-blue-700">{formatAttendancePercentage(student.attendance.percentage)}</td></tr></Fragment>)}</tbody>
            </table>{!sortedStudents.length && <p className="p-3 text-slate-500">No students are currently assigned.</p>}</div>
          </DashboardSection>
        </>
      )}
    </DashboardPage>
  );
}

export default function MentorDashboardPage() {
  return <ProtectedRoute role="MENTOR"><AppFrame><MentorDashboardContent /></AppFrame></ProtectedRoute>;
}
