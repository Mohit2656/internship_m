"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { AdminProfessionalProfile } from "@/components/AdminProfessionalProfile";
import { AppFrame } from "@/components/AppFrame";
import { HybridScheduleManager } from "@/components/HybridScheduleManager";
import { InternshipDetailsView } from "@/components/InternshipDetailsView";
import { InternshipLocationManager } from "@/components/InternshipLocationManager";
import { InternshipOnboardingDetails } from "@/components/InternshipOnboardingDetails";
import { ProfileDetails } from "@/components/ProfileDetails";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { StatusBadge } from "@/components/StatusBadge";
import { api } from "@/lib/api";
import { StudentDetail } from "@/lib/student-profile";

export default function StudentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [student, setStudent] = useState<StudentDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    api.get<StudentDetail>(`/admin/students/${id}`).then(response => setStudent(response.data))
      .catch(() => setError("Student record could not be loaded.")).finally(() => setLoading(false));
  }, [id]);

  return <ProtectedRoute role="ADMIN"><AppFrame><main className="mx-auto max-w-6xl px-5 py-10 sm:px-8">
    <Link href="/admin/students" className="text-sm font-semibold text-blue-700">← Back to students</Link>
    {loading ? <p className="py-20 text-center text-sm text-slate-500">Loading student record…</p> : error || !student ? <p className="mt-8 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p> : <>
      <div className="mt-5 flex flex-wrap items-start justify-between gap-4"><div><p className="text-sm font-semibold text-blue-700">Student record</p><h1 className="mt-2 text-3xl font-bold tracking-tight">{student.profile?.studentName ?? "Profile not completed"}</h1><p className="mt-2 text-slate-600">Login account: {student.loginEmail}</p></div><StatusBadge status={student.profileStatus}/></div>
      <div className="mt-8 space-y-6">
        {student.profile ? <ProfileDetails profile={student.profile} fullAadhaar={student.aadhaarNumber}/> : <section className="rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center"><h2 className="font-bold">No student profile yet</h2><p className="mt-2 text-sm text-slate-500">This student has not submitted their institutional information.</p></section>}
        <AdminProfessionalProfile studentId={student.id}/>
        <InternshipOnboardingDetails onboarding={student.internshipOnboarding} studentId={student.id}/>
        {student.internshipDetails ? <><InternshipDetailsView details={student.internshipDetails}/>{(student.internshipDetails.internshipMode === "OFFICE_REPORTING" || student.internshipDetails.internshipMode === "COLLEGE_REPORTING" || student.internshipDetails.internshipMode === "HYBRID") && <InternshipLocationManager studentId={student.id}/>} {student.internshipDetails.internshipMode === "HYBRID" && <HybridScheduleManager studentId={student.id}/>}</> : <section className="rounded-2xl border border-dashed border-slate-300 bg-white p-8"><p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">Internship Details</p><h2 className="mt-2 font-bold">Pending</h2><p className="mt-2 text-sm text-slate-600">The student has not completed their internship information.</p></section>}
      </div>
    </>}
  </main></AppFrame></ProtectedRoute>;
}
