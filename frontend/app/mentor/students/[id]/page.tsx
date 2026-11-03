"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { MentorResumeReview } from "@/components/MentorResumeReview";
import { LastStudentUpdate } from "@/components/LastStudentUpdate";
import { AppFrame } from "@/components/AppFrame";
import { InternshipLocationManager } from "@/components/InternshipLocationManager";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { DocumentPreviewModal } from "@/components/DocumentPreviewModal";
import { WorkflowBadge } from "@/components/WorkflowBadge";
import { api } from "@/lib/api";
import { MentorStudentProfile } from "@/lib/student-profile";
import { InternshipDetails } from "@/lib/internship-details";
import {
  downloadDocument,
  fileSize,
  InternshipDocument,
  InternshipOnboarding,
} from "@/lib/internship-onboarding";

const Row = ({ label, value }: { label: string; value: string | number | null }) => (
  <div>
    <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</dt>
    <dd className="mt-1 text-sm font-medium text-slate-900">{value || "—"}</dd>
  </div>
);

function MentorStudentProfilePage() {
  const { id } = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const slotParam = searchParams?.get("slot") || searchParams?.get("tab");
  const [student, setStudent] = useState<MentorStudentProfile | null>(null);
  const [internships, setInternships] = useState<InternshipDetails[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<1 | 2>(slotParam === "2" ? 2 : 1);
  const [onboarding, setOnboarding] = useState<InternshipOnboarding | null>(null);
  const [previewDoc, setPreviewDoc] = useState<InternshipDocument | null>(null);
  const [downloadError, setDownloadError] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (slotParam === "2") {
      setSelectedSlot(2);
    } else if (slotParam === "1") {
      setSelectedSlot(1);
    }
  }, [slotParam]);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      api.get<MentorStudentProfile>(`/mentor/students/${id}/profile`).then(({ data }) => data),
      api.get<InternshipDetails[]>(`/mentor/students/${id}/internships`).then(({ data }) => data).catch(() => []),
    ])
      .then(([prof, inters]) => {
        setStudent(prof);
        setInternships(inters);
        if (slotParam === "2") {
          setSelectedSlot(2);
        } else if (slotParam === "1") {
          setSelectedSlot(1);
        } else if (inters.length > 0 && !inters.some((i) => i.internshipNumber === 1)) {
          setSelectedSlot(inters[0].internshipNumber as 1 | 2);
        }
      })
      .catch(() => setError("Student profile could not be loaded."))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => {
    if (!id) return;
    api
      .get<InternshipOnboarding>(`/mentor/students/${id}/internship-onboarding`, {
        params: { internshipNumber: selectedSlot },
      })
      .then(({ data }) => setOnboarding(data))
      .catch(() => setOnboarding(null));
  }, [id, selectedSlot]);

  const activeInternship = internships.find((i) => i.internshipNumber === selectedSlot) ?? null;
  const documentsBasePath = `/mentor/students/${id}/internship-onboarding/documents`;

  async function handleDownload(docId: number, fileName: string) {
    setDownloadError("");
    try {
      await downloadDocument(`${documentsBasePath}/${docId}/download`, fileName);
    } catch {
      setDownloadError("The document could not be downloaded.");
    }
  }

  return (
    <main className="mx-auto max-w-5xl px-5 py-10 sm:px-8">
      <div className="mb-6">
        <Link
          href="/mentor/students"
          className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:border-blue-400 hover:text-blue-700"
        >
          <span aria-hidden="true">←</span>
          <span>Back to Assigned Students</span>
        </Link>
      </div>
      {error ? (
        <p className="mt-8 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>
      ) : loading || !student ? (
        <p className="py-20 text-center text-sm text-slate-500">Loading student profile…</p>
      ) : (
        <>
          <div className="mt-5">
            <p className="text-sm font-semibold text-blue-700">Assigned student</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">{student.studentName}</h1>
          </div>

          {/* Student Profile */}
          <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-lg font-bold text-slate-900">Student Profile</h2>
            <LastStudentUpdate value={student.profileUpdatedAt} />
            <dl className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              <Row label="UID" value={student.uid} />
              <Row label="Branch" value={student.branchLabel} />
              <Row label="Roll Number" value={student.rollNumber} />
              <Row label="Institute Email" value={student.instituteEmail} />
              <Row label="Personal Email" value={student.personalEmail} />
              <Row label="Phone Number" value={student.phoneNumber} />
              <Row label="Academic Year" value={student.academicYear} />
              <Row label="Semester" value={student.semester} />
            </dl>
          </section>

          {/* Professional Profile */}
          <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-lg font-bold text-slate-900">Professional Profile</h2>
            <LastStudentUpdate value={student.professionalUpdatedAt} />
            <dl className="mt-5 grid gap-5 sm:grid-cols-2">
              <Row label="Headline" value={student.professionalHeadline} />
              <Row label="Career Interests" value={student.careerInterests} />
            </dl>
            <p className="mt-6 text-xs font-semibold uppercase tracking-wide text-slate-500">Confirmed Skills</p>
            <LastStudentUpdate value={student.skillsUpdatedAt} />
            {student.confirmedSkills.length ? (
              <div className="mt-2 flex flex-wrap gap-2">
                {student.confirmedSkills.map((skill) => (
                  <span key={skill} className="rounded-full bg-blue-50 px-3 py-1.5 text-xs font-semibold text-blue-800">
                    {skill}
                  </span>
                ))}
              </div>
            ) : (
              <p className="mt-2 text-sm text-slate-500">No confirmed skills</p>
            )}
          </section>

          {/* Resume Review */}
          <MentorResumeReview studentId={Number(id)} />

          {/* Internships & Workplace Locations */}
          <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-bold text-slate-900">Internship Details &amp; Workplace Approval</h2>
                <p className="mt-1 text-sm text-slate-600">
                  Review submitted company details, documents, and approve or reject workplace GPS coordinates.
                </p>
              </div>
              {onboarding?.workflowStatus && <WorkflowBadge status={onboarding.workflowStatus} />}
            </div>

            {/* Tab Controls: only show dual tabs if multiple internships exist */}
            {internships.length > 1 ? (
              <div className="mt-6">
                <div className="inline-flex items-center gap-1 rounded-lg bg-slate-100/90 p-1 border border-slate-200/80 shadow-xs" role="tablist" aria-label="Internship slots">
                  {[1, 2].map((slot) => {
                    const item = internships.find((i) => i.internshipNumber === slot);
                    const isSelected = selectedSlot === slot;
                    return (
                      <button
                        key={slot}
                        type="button"
                        role="tab"
                        id={`tab-mentor-slot-${slot}`}
                        aria-selected={isSelected}
                        onClick={() => setSelectedSlot(slot as 1 | 2)}
                        className={`segmented-tab inline-flex items-center gap-2 rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
                          isSelected
                            ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                            : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
                        }`}
                      >
                        <span>Internship {slot}</span>
                        {item ? (
                          <span
                            className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                              isSelected ? "border border-blue-200 bg-blue-100 text-blue-800" : "bg-slate-200 text-slate-700"
                            }`}
                          >
                            {item.companyName}
                          </span>
                        ) : (
                          <span className={`text-[10px] ${isSelected ? "text-blue-600" : "text-slate-400"}`}>(Not submitted)</span>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            ) : internships.length === 1 ? (
              <div className="mt-5 flex items-center gap-3 rounded-xl bg-slate-50 border border-slate-200 px-4 py-2.5">
                <span className="rounded-md bg-blue-600 px-2 py-0.5 text-xs font-bold text-white uppercase tracking-wider">
                  Internship {internships[0].internshipNumber}
                </span>
                <span className="text-sm font-bold text-slate-900">{internships[0].companyName}</span>
              </div>
            ) : null}

            <div className="mt-6">
              {!activeInternship ? (
                <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center">
                  <p className="text-sm font-medium text-slate-600">
                    No details submitted yet for Internship {selectedSlot}.
                  </p>
                  <p className="mt-1 text-xs text-slate-400">
                    When the student submits internship details, they will appear here for review.
                  </p>
                </div>
              ) : (
                <div className="space-y-6">
                  {/* Detailed Company & Internship Information */}
                  <div>
                    <h3 className="text-sm font-bold uppercase tracking-wider text-slate-500">
                      Company &amp; Internship Information
                    </h3>
                    <LastStudentUpdate value={activeInternship.updatedAt} />
                    <dl className="mt-4 grid gap-4 rounded-xl border border-slate-200 bg-slate-50/70 p-5 sm:grid-cols-2 lg:grid-cols-3">
                      <Row label="Company Name" value={activeInternship.companyName} />
                      <Row
                        label="Company Type"
                        value={
                          activeInternship.companyType === "OTHER" && activeInternship.otherCompanyType
                            ? `Other (${activeInternship.otherCompanyType})`
                            : activeInternship.companyTypeLabel || activeInternship.companyType
                        }
                      />
                      <Row label="Company Phone" value={activeInternship.companyPhone} />
                      <div className="sm:col-span-2 lg:col-span-3">
                        <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">Company Address</dt>
                        <dd className="mt-1 text-sm font-medium text-slate-900">{activeInternship.companyAddress || "—"}</dd>
                      </div>
                      <Row label="Industry Supervisor" value={activeInternship.industrySupervisorName} />
                      <Row label="Supervisor Designation / Role" value={activeInternship.industrySupervisorDesignation} />
                      <Row label="Supervisor Contact" value={activeInternship.industrySupervisorContact} />
                      <Row
                        label="Duration & Dates"
                        value={`${activeInternship.startDate} to ${activeInternship.endDate} (${activeInternship.durationMonths} months, ${activeInternship.totalWeeks} weeks)`}
                      />
                      <Row label="Internship Mode" value={activeInternship.internshipModeLabel || activeInternship.internshipMode} />
                      <Row
                        label="Stipend"
                        value={activeInternship.stipendPerMonth ? `₹${activeInternship.stipendPerMonth}/month` : "Unpaid"}
                      />
                      <Row
                        label="Full-Time Employment"
                        value={activeInternship.fullTimeEmploymentOffered ? "Offered" : "Not Offered"}
                      />
                    </dl>
                  </div>

                  {/* Onboarding & Required Documents */}
                  {onboarding && (
                    <div>
                      <h3 className="text-sm font-bold uppercase tracking-wider text-slate-500">
                        Preparation, Source &amp; Required Documents
                      </h3>
                      <dl className="mt-4 grid gap-4 rounded-xl border border-slate-200 bg-slate-50/70 p-5 sm:grid-cols-3">
                        <Row label="Internship Source" value={onboarding.sourceLabel || onboarding.source || "Not selected"} />
                        <Row
                          label="Diary Issued"
                          value={
                            onboarding.diaryIssued
                              ? `Issued ${onboarding.diaryIssuedAt ? `(${new Date(onboarding.diaryIssuedAt).toLocaleDateString()})` : ""}`
                              : "Not issued"
                          }
                        />
                        <Row
                          label="Documents Completed"
                          value={`${onboarding.documentsCompleted} / ${onboarding.documentsRequired}`}
                        />
                      </dl>

                      {onboarding.documents && onboarding.documents.length > 0 && (
                        <div className="mt-4">
                          <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">Submitted Documents</h4>
                          <div className="mt-2 divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
                            {onboarding.documents.map((doc) => (
                              <div
                                key={doc.documentType}
                                className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
                              >
                                <div>
                                  <p className="text-sm font-semibold text-slate-900">{doc.documentTypeLabel}</p>
                                  <p className="mt-0.5 text-xs text-slate-500">
                                    {doc.uploaded
                                      ? `Uploaded · ${doc.originalFileName || "File"} · ${fileSize(doc.fileSize)}`
                                      : "Not Uploaded"}
                                  </p>
                                </div>
                                {doc.uploaded && doc.id && (
                                  <div className="flex gap-2">
                                    <button
                                      type="button"
                                      data-button-variant="outline"
                                      onClick={() => setPreviewDoc(doc)}
                                      className="rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                                    >
                                      View
                                    </button>
                                    <button
                                      type="button"
                                      data-button-variant="outline"
                                      onClick={() => handleDownload(doc.id!, doc.originalFileName || "document")}
                                      className="rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                                    >
                                      Download
                                    </button>
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                      {downloadError && <p className="mt-2 text-sm text-red-600">{downloadError}</p>}
                    </div>
                  )}

                  {/* Workplace Location & Geofence Review */}
                  <div className="border-t border-slate-200 pt-6">
                    <h3 className="text-sm font-bold uppercase tracking-wider text-slate-500">
                      Workplace Location &amp; Attendance Geofence Review
                    </h3>
                    <div className="mt-3">
                      <InternshipLocationManager
                        key={`loc-${activeInternship.id}`}
                        scope="mentor"
                        studentId={Number(id)}
                        internshipId={activeInternship.id}
                      />
                    </div>
                  </div>
                </div>
              )}
            </div>
          </section>

          {/* Document Preview Modal */}
          {previewDoc?.id && previewDoc.originalFileName && (
            <DocumentPreviewModal
              contentType={previewDoc.contentType}
              fileName={previewDoc.originalFileName}
              viewUrl={`${documentsBasePath}/${previewDoc.id}/view`}
              downloadUrl={`${documentsBasePath}/${previewDoc.id}/download`}
              onClose={() => setPreviewDoc(null)}
            />
          )}
        </>
      )}
    </main>
  );
}

export default function MentorStudentProfileRoute() {
  return (
    <ProtectedRoute role="MENTOR">
      <AppFrame>
        <MentorStudentProfilePage />
      </AppFrame>
    </ProtectedRoute>
  );
}
