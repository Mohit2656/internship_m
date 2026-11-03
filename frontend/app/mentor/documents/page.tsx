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
  const [studentInternships, setStudentInternships] = useState<any[]>([]);
  const [internshipTab, setInternshipTab] = useState<1 | 2>(1);
  const [onboarding, setOnboarding] = useState<InternshipOnboarding | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .get<AssignedStudent[]>("/mentor/students")
      .then((response) => setStudents(response.data))
      .catch(() => setError("Assigned students could not be loaded."))
      .finally(() => setLoading(false));
  }, []);

  async function loadSlotDocuments(student: AssignedStudent, slot: 1 | 2) {
    setOnboarding(null);
    setError("");
    try {
      const response = await api.get<InternshipOnboarding>(
        `/mentor/students/${student.userId}/internship-onboarding`,
        { params: { internshipNumber: slot } }
      );
      setOnboarding(response.data);
    } catch {
      setError(`The student's Internship ${slot} documents could not be loaded.`);
    }
  }

  async function choose(student: AssignedStudent) {
    setSelected(student);
    setInternshipTab(1);
    setOnboarding(null);
    setError("");
    try {
      const [intersRes, onbRes] = await Promise.all([
        api.get<any[]>(`/mentor/students/${student.userId}/internships`).catch(() => ({ data: [] })),
        api.get<InternshipOnboarding>(`/mentor/students/${student.userId}/internship-onboarding`, {
          params: { internshipNumber: 1 }
        })
      ]);
      setStudentInternships(intersRes.data || []);
      setOnboarding(onbRes.data);
    } catch {
      setError("The student's Internship 1 documents could not be loaded.");
    }
  }

  function switchTab(slot: 1 | 2) {
    setInternshipTab(slot);
    if (selected) {
      void loadSlotDocuments(selected, slot);
    }
  }

  return (
    <main className="mx-auto max-w-6xl px-5 py-10 sm:px-8">
      <Link href="/mentor/dashboard" className="text-sm font-semibold text-blue-700">
        ← Back to dashboard
      </Link>
      <p className="mt-6 text-sm font-semibold text-blue-700">Assigned students</p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Internship Documents</h1>
      <p className="mt-2 text-slate-600">
        View or download documents for students currently assigned to you for both primary and secondary internships.
      </p>

      {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}

      {loading ? (
        <p className="mt-8 text-sm text-slate-500">Loading assigned students…</p>
      ) : students.length === 0 ? (
        <p className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-sm text-slate-500">
          No students are currently assigned.
        </p>
      ) : (
        <div className="mt-8 grid gap-6 lg:grid-cols-[18rem_1fr] items-start">
          {/* Assigned Students Selection Panel */}
          <section className="h-fit rounded-2xl border border-slate-200/90 bg-white p-3.5 shadow-xs">
            <div className="flex items-center justify-between px-2 pb-2.5 border-b border-slate-100">
              <h2 className="text-xs font-bold uppercase tracking-wider text-slate-500">Assigned Students</h2>
              <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-600">
                {students.length}
              </span>
            </div>
            <div className="mt-2 space-y-1.5 max-h-[calc(100vh-16rem)] overflow-y-auto pr-0.5" role="listbox" aria-label="Assigned students list">
              {students.map((student) => {
                const isSelected = selected !== null && selected.userId === student.userId;
                return (
                  <button
                    key={student.userId}
                    type="button"
                    role="option"
                    aria-selected={isSelected}
                    onClick={() => void choose(student)}
                    className={`student-select-btn group w-full text-left transition-all ${
                      isSelected
                        ? "!bg-blue-50/90 !border-blue-200 !border-l-[4px] !border-l-blue-600 !text-blue-950 shadow-xs"
                        : "!bg-white !border-slate-200 hover:!bg-slate-50 hover:!border-slate-300 !text-slate-800"
                    }`}
                  >
                    <div className="min-w-0 flex-1">
                      <p
                        className={`truncate text-sm ${
                          isSelected ? "font-bold text-blue-950" : "font-semibold text-slate-800 group-hover:text-slate-950"
                        }`}
                      >
                        {student.name ?? "Profile incomplete"}
                      </p>
                      <p
                        className={`truncate text-xs ${
                          isSelected ? "text-blue-700/80 font-medium" : "text-slate-500"
                        }`}
                      >
                        {student.uid ?? `ID: ${student.userId}`}
                      </p>
                    </div>
                    {isSelected && (
                      <span
                        className="ml-2 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white shadow-xs"
                        aria-hidden="true"
                      >
                        ✓
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </section>

          {/* Document Content Panel */}
          <div>
            {selected && (
              <div className="mb-6">
                {studentInternships.length > 1 ? (
                  <div className="inline-flex items-center gap-1 rounded-lg bg-slate-100/90 p-1 border border-slate-200/80 shadow-xs" role="tablist" aria-label="Internship documents selection">
                    <button
                      type="button"
                      role="tab"
                      id="tab-mentor-doc-1"
                      aria-selected={internshipTab === 1}
                      onClick={() => switchTab(1)}
                      className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
                        internshipTab === 1
                          ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                          : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
                      }`}
                    >
                      Internship 1 Documents
                    </button>
                    <button
                      type="button"
                      role="tab"
                      id="tab-mentor-doc-2"
                      aria-selected={internshipTab === 2}
                      onClick={() => switchTab(2)}
                      className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
                        internshipTab === 2
                          ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                          : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
                      }`}
                    >
                      Internship 2 Documents
                    </button>
                  </div>
                ) : (
                  <div className="inline-flex items-center gap-2 rounded-lg bg-slate-100/80 px-3 py-1.5 text-xs font-semibold text-slate-700 border border-slate-200">
                    <span className="h-2 w-2 rounded-full bg-blue-600" />
                    Internship 1 Documents
                  </div>
                )}
              </div>
            )}

            {selected && !onboarding ? (
              <p className="rounded-2xl border border-slate-200 bg-white p-8 text-sm text-slate-500">
                Loading Internship {internshipTab} documents…
              </p>
            ) : onboarding && selected ? (
              <InternshipOnboardingDetails
                key={`student-${selected.userId}-slot-${internshipTab}`}
                onboarding={onboarding}
                documentBasePath={`/mentor/students/${selected.userId}/internship-onboarding/documents`}
              />
            ) : (
              <p className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-sm text-slate-500">
                Select a student to view document status.
              </p>
            )}
          </div>
        </div>
      )}
    </main>
  );
}

export default function MentorDocumentsPage() {
  return (
    <ProtectedRoute role="MENTOR">
      <AppFrame>
        <MentorDocumentsContent />
      </AppFrame>
    </ProtectedRoute>
  );
}
