"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { MentorResumeReview } from "@/components/MentorResumeReview";
import { LastStudentUpdate } from "@/components/LastStudentUpdate";
import { AppFrame } from "@/components/AppFrame";
import { InternshipLocationManager } from "@/components/InternshipLocationManager";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { MentorStudentProfile } from "@/lib/student-profile";

const Row = ({ label, value }: { label: string; value: string | number | null }) => <div><dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</dt><dd className="mt-1 text-sm font-medium text-slate-900">{value || "—"}</dd></div>;

function MentorStudentProfilePage() {
  const { id } = useParams<{ id: string }>();
  const [student, setStudent] = useState<MentorStudentProfile | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { api.get<MentorStudentProfile>(`/mentor/students/${id}/profile`).then(({ data }) => setStudent(data)).catch(() => setError("Student profile could not be loaded.")); }, [id]);
  return <main className="mx-auto max-w-5xl px-5 py-10 sm:px-8"><Link href="/mentor/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link>{error ? <p className="mt-8 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p> : !student ? <p className="py-20 text-center text-sm text-slate-500">Loading student profile…</p> : <><div className="mt-5"><p className="text-sm font-semibold text-blue-700">Assigned student</p><h1 className="mt-2 text-3xl font-bold tracking-tight">{student.studentName}</h1></div><section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6"><h2 className="text-lg font-bold">Student Profile</h2><LastStudentUpdate value={student.profileUpdatedAt}/><dl className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3"><Row label="UID" value={student.uid}/><Row label="Branch" value={student.branchLabel}/><Row label="Roll Number" value={student.rollNumber}/><Row label="Institute Email" value={student.instituteEmail}/><Row label="Personal Email" value={student.personalEmail}/><Row label="Phone Number" value={student.phoneNumber}/><Row label="Academic Year" value={student.academicYear}/><Row label="Semester" value={student.semester}/></dl></section><section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6"><h2 className="text-lg font-bold">Professional Profile</h2><LastStudentUpdate value={student.professionalUpdatedAt}/><dl className="mt-5 grid gap-5 sm:grid-cols-2"><Row label="Headline" value={student.professionalHeadline}/><Row label="Career Interests" value={student.careerInterests}/></dl><p className="mt-6 text-xs font-semibold uppercase tracking-wide text-slate-500">Confirmed Skills</p><LastStudentUpdate value={student.skillsUpdatedAt}/>{student.confirmedSkills.length ? <div className="mt-2 flex flex-wrap gap-2">{student.confirmedSkills.map(skill => <span key={skill} className="rounded-full bg-blue-50 px-3 py-1.5 text-xs font-semibold text-blue-800">{skill}</span>)}</div> : <p className="mt-2 text-sm text-slate-500">No confirmed skills</p>}</section><MentorResumeReview studentId={Number(id)} /><div className="mt-6"><InternshipLocationManager scope="mentor" studentId={Number(id)}/></div></>}</main>;
}

export default function MentorStudentProfileRoute() { return <ProtectedRoute role="MENTOR"><AppFrame><MentorStudentProfilePage /></AppFrame></ProtectedRoute>; }
