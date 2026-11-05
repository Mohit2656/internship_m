"use client";

import Link from "next/link";
import { Suspense, useEffect, useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import { AdminProfessionalProfile } from "@/components/AdminProfessionalProfile";
import { AppFrame } from "@/components/AppFrame";
import { HybridScheduleManager } from "@/components/HybridScheduleManager";
import { InternshipCompletionPanel } from "@/components/InternshipCompletionPanel";
import { InternshipDetailsView } from "@/components/InternshipDetailsView";
import { InternshipLocationManager } from "@/components/InternshipLocationManager";
import { InternshipOnboardingDetails } from "@/components/InternshipOnboardingDetails";
import { ProfileDetails } from "@/components/ProfileDetails";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { StatusBadge } from "@/components/StatusBadge";
import { api } from "@/lib/api";
import { StudentDetail } from "@/lib/student-profile";

function StudentDetailContent() {
  const { id } = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const [student, setStudent] = useState<StudentDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const from = searchParams?.get("from");
  const category = searchParams?.get("category");
  const sessionId = searchParams?.get("sessionId");
  const fromAttendance = from === "attendance";
  const fromDashboard = from === "dashboard" || Boolean(category);

  const backLinkHref = fromAttendance
    ? "/admin/attendance"
    : fromDashboard
    ? (category
        ? `/admin/dashboard?category=${encodeURIComponent(category)}${sessionId ? `&sessionId=${encodeURIComponent(sessionId)}` : ""}`
        : "/admin/dashboard")
    : "/admin/students";

  const backLinkLabel = fromAttendance
    ? "← Back to Attendance"
    : fromDashboard
    ? "← Back to Student Overview"
    : "← Back to Student Directory"; // Preserves default Back to students fallback

  useEffect(() => {
    api.get<StudentDetail>(`/admin/students/${id}`).then(response => setStudent(response.data))
      .catch((err) => {
        if (err?.response?.status === 404) {
          setError("Student record not found.");
        } else if (err?.response?.status === 403) {
          setError("You do not have permission to view this student's record.");
        } else {
          setError(err?.response?.data?.message || "Student record could not be loaded.");
        }
      }).finally(() => setLoading(false));
  }, [id]);

  return <ProtectedRoute role="ADMIN"><AppFrame activeSection={fromAttendance ? "/admin/attendance" : undefined}><main className="mx-auto max-w-6xl px-5 py-10 sm:px-8">
    <Link href={backLinkHref} className="text-sm font-semibold text-blue-700 hover:underline inline-flex items-center gap-1">{backLinkLabel}</Link>
    {loading ? <p className="py-20 text-center text-sm text-slate-500">Loading student record…</p> : error || !student ? <p className="mt-8 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p> : <>
      <div className="mt-5 flex flex-wrap items-start justify-between gap-4"><div><p className="text-sm font-semibold text-blue-700">Student record</p><h1 className="mt-2 text-3xl font-bold tracking-tight">{student.profile?.studentName ?? "Profile not completed"}</h1><p className="mt-2 text-slate-600">Login account: {student.loginEmail}</p></div><StatusBadge status={student.profileStatus}/></div>
      <div className="mt-8 space-y-6">
        {student.profile ? <ProfileDetails profile={student.profile} fullAadhaar={student.aadhaarNumber}/> : <section className="rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center"><h2 className="font-bold">No student profile yet</h2><p className="mt-2 text-sm text-slate-500">This student has not submitted their institutional information.</p></section>}
        <AdminProfessionalProfile studentId={student.id}/>
        <InternshipOnboardingDetails onboarding={student.internshipOnboarding} studentId={student.id}/>
        {student.internshipDetails ? <><InternshipDetailsView details={student.internshipDetails}/><InternshipCompletionPanel studentId={student.id}/><InternshipLocationManager studentId={student.id}/> {student.internshipDetails.internshipMode === "HYBRID" && <HybridScheduleManager studentId={student.id}/>}</> : <section className="rounded-2xl border border-dashed border-slate-300 bg-white p-8"><p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">Internship Details</p><h2 className="mt-2 font-bold">Pending</h2><p className="mt-2 text-sm text-slate-600">The student has not completed their internship information.</p></section>}
      </div>
    </>}
  </main></AppFrame></ProtectedRoute>;
}

export default function StudentDetailPage() {
  return (
    <Suspense fallback={<p className="py-20 text-center text-sm text-slate-500">Loading student record…</p>}>
      <StudentDetailContent />
    </Suspense>
  );
}
