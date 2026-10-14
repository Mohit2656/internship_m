"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { InternshipOnboardingDetails } from "@/components/InternshipOnboardingDetails";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { InternshipOnboarding } from "@/lib/internship-onboarding";

type AssignedStudent = { userId: number; name: string | null; uid: string | null };

function MentorDocumentsContent() {
  const [students, setStudents] = useState<AssignedStudent[]>([]);
  const [selected, setSelected] = useState<AssignedStudent | null>(null);
  const [onboarding, setOnboarding] = useState<InternshipOnboarding | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    api.get<AssignedStudent[]>("/mentor/students")
      .then(response => setStudents(response.data))
      .catch(() => setError("Assigned students could not be loaded."))
      .finally(() => setLoading(false));
  }, []);

  async function choose(student: AssignedStudent) {
    setSelected(student); setOnboarding(null); setError("");
    try {
      const response = await api.get<InternshipOnboarding>(`/mentor/students/${student.userId}/internship-onboarding`);
      setOnboarding(response.data);
    } catch {
      setError("The student's internship documents could not be loaded.");
    }
  }

  return <main className="mx-auto max-w-6xl px-5 py-10 sm:px-8">
    <Link href="/mentor/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link>
    <p className="mt-6 text-sm font-semibold text-blue-700">Assigned students</p>
    <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Internship Documents</h1>
    <p className="mt-2 text-slate-600">View or download documents for students currently assigned to you.</p>
    {error && <p className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}
    {loading ? <p className="mt-8 text-sm text-slate-500">Loading assigned students…</p> : students.length === 0 ? <p className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-sm text-slate-500">No students are currently assigned.</p> : <div className="mt-8 grid gap-6 lg:grid-cols-[18rem_1fr]">
      <section className="h-fit rounded-2xl border border-slate-200 bg-white p-4"><h2 className="px-2 pb-3 font-bold">Students</h2><div className="space-y-2">{students.map(student => <button data-button-variant="outline" key={student.userId} aria-pressed={selected?.userId === student.userId} onClick={() => choose(student)} className={`w-full rounded-xl px-3 py-3 text-left text-sm ${selected?.userId === student.userId ? "bg-blue-50 text-blue-900" : "hover:bg-slate-50"}`}><span className="block font-semibold">{student.name ?? "Profile incomplete"}</span><span className="mt-1 block text-xs text-slate-500">{student.uid ?? "UID unavailable"}</span></button>)}</div></section>
      <div>{selected && !onboarding ? <p className="rounded-2xl border border-slate-200 bg-white p-8 text-sm text-slate-500">Loading documents…</p> : onboarding && selected ? <InternshipOnboardingDetails onboarding={onboarding} documentBasePath={`/mentor/students/${selected.userId}/internship-onboarding/documents`}/> : <p className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-sm text-slate-500">Select a student to view document status.</p>}</div>
    </div>}
  </main>;
}

export default function MentorDocumentsPage() {
  return <ProtectedRoute role="MENTOR"><AppFrame><MentorDocumentsContent/></AppFrame></ProtectedRoute>;
}
