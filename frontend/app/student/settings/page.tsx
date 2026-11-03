"use client";

import Link from "next/link";
import { FormEvent, Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AxiosError } from "axios";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { ProfileDetails } from "@/components/ProfileDetails";
import { ProfileForm } from "@/components/ProfileForm";
import { ApiError, api } from "@/lib/api";
import { ProfilePrefill, StudentProfile } from "@/lib/student-profile";
import { ProfessionalProfile, SkillProfile } from "@/lib/professional-profile";
import { InternshipOnboarding } from "@/lib/internship-onboarding";
import { InternshipDetails } from "@/lib/internship-details";
import { calculateProfileCompletion } from "@/lib/profile-completion";
import { ProfileCompletionTracker } from "@/components/ProfileCompletionTracker";
import { formatDateTime } from "@/lib/monthly-progress";

type DocumentTemplate = {
  path: string;
  filename: string;
  title: string;
  description: string;
};

const templates: DocumentTemplate[] = [
  {
    path: "/documents/application-for-permission-to-apply-for-internship.docx",
    filename: "Application-for-Permission-to-Apply-for-Internship.docx",
    title: "Application for Permission to Apply for Internship",
    description: "Use this application to request departmental permission before applying for an internship.",
  },
  {
    path: "/documents/department-noc-for-internship.docx",
    filename: "Department-NOC-for-Internship.docx",
    title: "No Objection Certificate (NOC) — Department Format",
    description: "Official department format for requesting permission and a No Objection Certificate for internship.",
  },
  {
    path: "/documents/student-internship-undertaking.docx",
    filename: "Student-Internship-Undertaking.docx",
    title: "Student Internship Undertaking",
    description: "Complete this undertaking to confirm your responsibilities during the internship period.",
  },
];

function SettingsContent() {
  const searchParams = useSearchParams();
  const initialTab = searchParams.get("tab") ?? searchParams.get("section") ?? "profile";
  const [activeSection, setActiveSection] = useState<"profile" | "security" | "documents">(
    initialTab === "security" ? "security" : initialTab === "documents" ? "documents" : "profile"
  );

  // Sync active section when tab query parameter changes
  useEffect(() => {
    const tab = searchParams.get("tab") ?? searchParams.get("section");
    if (tab === "security" || tab === "documents" || tab === "profile") {
      setActiveSection(tab);
    }
  }, [searchParams]);

  // Profile State
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [prefill, setPrefill] = useState<ProfilePrefill | null>(null);
  const [professionalProfile, setProfessionalProfile] = useState<ProfessionalProfile | null>(null);
  const [skillsProfile, setSkillsProfile] = useState<SkillProfile | null>(null);
  const [onboarding, setOnboarding] = useState<InternshipOnboarding | null>(null);
  const [details, setDetails] = useState<InternshipDetails | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [editing, setEditing] = useState(searchParams.get("edit") === "true");
  const [profileError, setProfileError] = useState("");

  // Security State
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [securityError, setSecurityError] = useState("");
  const [securityNotice, setSecurityNotice] = useState("");
  const [securitySaving, setSecuritySaving] = useState(false);

  // Documents State
  const [downloading, setDownloading] = useState<string | null>(null);
  const [docError, setDocError] = useState("");

  useEffect(() => {
    Promise.all([
      api.get<StudentProfile>("/student/profile").catch((c) => {
        if (c.response?.status === 404) return null;
        throw c;
      }),
      api.get<ProfilePrefill>("/student/profile/prefill").catch(() => null),
      api.get<ProfessionalProfile>("/student/professional-profile").catch(() => null),
      api.get<SkillProfile>("/student/professional-profile/skills").catch(() => null),
      api.get<InternshipOnboarding>("/student/internship-onboarding").catch(() => null),
      api.get<InternshipDetails>("/student/internship/details").catch(() => null),
    ])
      .then(([current, institutional, prof, skills, onb, det]) => {
        setProfile(current?.data ?? null);
        setPrefill(institutional?.data ?? null);
        setProfessionalProfile(prof?.data ?? null);
        setSkillsProfile(skills?.data ?? null);
        setOnboarding(onb?.data ?? null);
        setDetails(det?.data ?? null);
      })
      .catch(() => setProfileError("Unable to load profile data."))
      .finally(() => setProfileLoading(false));
  }, []);

  const completion = calculateProfileCompletion({
    profile,
    professionalProfile,
    skillsCount: skillsProfile?.skills?.length ?? 0,
    onboarding,
    details,
  });

  async function handlePasswordChange(e: FormEvent) {
    e.preventDefault();
    if (securitySaving) return;
    setSecurityError("");
    setSecurityNotice("");
    if (newPassword !== confirmPassword) {
      setSecurityError("New passwords do not match.");
      return;
    }
    if (newPassword.length < 12) {
      setSecurityError("Password must be at least 12 characters.");
      return;
    }
    if (new TextEncoder().encode(newPassword).length > 72) {
      setSecurityError("Password exceeds maximum length (72 bytes).");
      return;
    }
    setSecuritySaving(true);
    try {
      await api.post("/auth/change-password", { newPassword, confirmPassword });
      setNewPassword("");
      setConfirmPassword("");
      setSecurityNotice("Password updated successfully.");
    } catch (cause) {
      setSecurityError(
        (cause as AxiosError<ApiError>).response?.data?.message ?? "Unable to update password."
      );
    } finally {
      setSecuritySaving(false);
    }
  }

  async function downloadTemplate(template: DocumentTemplate) {
    setDownloading(template.path);
    setDocError("");
    try {
      const response = await fetch(template.path);
      if (!response.ok) throw new Error("Template unavailable");
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = template.filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch {
      setDocError(`The file "${template.title}" is currently unavailable.`);
    } finally {
      setDownloading(null);
    }
  }

  return (
    <main className="mx-auto max-w-5xl px-5 py-10 sm:px-8">
      <Link href="/student/dashboard" className="text-sm font-semibold text-blue-700">
        ← Back to dashboard
      </Link>
      <div className="mt-5 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-blue-700">Account &amp; Preferences</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Settings</h1>
          <p className="mt-2 text-slate-600">
            Manage your personal profile, account security, and official document downloads.
          </p>
        </div>
      </div>

      <div className="mt-8">
        <div className="inline-flex items-center gap-1 rounded-lg bg-slate-100/90 p-1 border border-slate-200/80 shadow-xs" role="tablist" aria-label="Settings sections">
          <button
            type="button"
            role="tab"
            id="tab-profile"
            aria-selected={activeSection === "profile"}
            aria-controls="panel-profile"
            onClick={() => setActiveSection("profile")}
            className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              activeSection === "profile"
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
            }`}
          >
            My Profile
          </button>
          <button
            type="button"
            role="tab"
            id="tab-security"
            aria-selected={activeSection === "security"}
            aria-controls="panel-security"
            onClick={() => setActiveSection("security")}
            className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              activeSection === "security"
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
            }`}
          >
            Security
          </button>
          <button
            type="button"
            role="tab"
            id="tab-documents"
            aria-selected={activeSection === "documents"}
            aria-controls="panel-documents"
            onClick={() => setActiveSection("documents")}
            className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              activeSection === "documents"
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
            }`}
          >
            Documents &amp; Templates
          </button>
        </div>

        {/* SECTION 1: MY PROFILE */}
        {activeSection === "profile" && (
          <div id="panel-profile" role="tabpanel" aria-labelledby="tab-profile" className="mt-6 space-y-6">
            <ProfileCompletionTracker completion={completion} />

            <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h2 className="text-xl font-bold text-slate-900">Institutional Profile</h2>
                  <p className="mt-1 text-sm text-slate-600">
                    Your institutional details used by the III Cell and faculty mentors.
                  </p>
                </div>
                {profile && !editing && (
                  <button onClick={() => setEditing(true)} className="ui-button text-sm">
                    Edit Profile
                  </button>
                )}
              </div>

              {profileError && (
                <p role="alert" className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">
                  {profileError}
                </p>
              )}

              {profileLoading ? (
                <p className="mt-6 text-sm text-slate-500">Loading profile details…</p>
              ) : profile && !editing ? (
                <div className="mt-6">
                  <ProfileDetails profile={profile} />
                </div>
              ) : (
                <div className="mt-6">
                  <ProfileForm
                    existing={profile ?? undefined}
                    institutional={prefill ?? undefined}
                    onCancel={profile ? () => setEditing(false) : undefined}
                    onSaved={(saved) => {
                      setProfile(saved);
                      setEditing(false);
                    }}
                  />
                </div>
              )}
            </section>

            {/* Professional Profile & Resume Summary */}
            <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h2 className="text-xl font-bold text-slate-900">Professional Resume</h2>
                  <p className="mt-1 text-sm text-slate-600">
                    Resume and career preferences used for AI opportunity matching.
                  </p>
                </div>
                <Link href="/student/professional-profile" className="ui-button text-sm">
                  {professionalProfile?.resume ? "Manage Resume" : "Upload Resume"}
                </Link>
              </div>
              {professionalProfile?.resume ? (
                <div className="mt-5 rounded-xl bg-slate-50 p-4 text-sm">
                  <p className="font-semibold text-slate-900">
                    {professionalProfile.resume.originalFilename}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    Uploaded on {formatDateTime(professionalProfile.resume.uploadedAt)}
                  </p>
                </div>
              ) : (
                <p className="mt-4 text-sm text-slate-500">
                  No resume uploaded yet. Upload a PDF or DOCX resume to unlock opportunity recommendations.
                </p>
              )}
            </section>
          </div>
        )}

        {/* SECTION 2: SECURITY */}
        {activeSection === "security" && (
          <div id="panel-security" role="tabpanel" aria-labelledby="tab-security" className="mt-6">
            <section className="max-w-xl rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
              <h2 className="text-xl font-bold text-slate-900">Change Password</h2>
              <p className="mt-1 text-sm text-slate-600">
                Update your account password. Use at least 12 characters.
              </p>

              {securityNotice && (
                <p role="status" className="mt-4 rounded-xl bg-emerald-50 p-4 text-sm font-semibold text-emerald-800">
                  {securityNotice}
                </p>
              )}
              {securityError && (
                <p role="alert" className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">
                  {securityError}
                </p>
              )}

              <form onSubmit={handlePasswordChange} className="mt-6 space-y-4">
                <label className="block">
                  <span className="mb-2 block text-sm font-semibold">New password</span>
                  <input
                    type="password"
                    autoComplete="new-password"
                    minLength={12}
                    maxLength={128}
                    required
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    className="w-full rounded-xl border border-slate-300 px-4 py-3 text-sm outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100"
                  />
                </label>
                <label className="block">
                  <span className="mb-2 block text-sm font-semibold">Confirm new password</span>
                  <input
                    type="password"
                    autoComplete="new-password"
                    minLength={12}
                    maxLength={128}
                    required
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    className="w-full rounded-xl border border-slate-300 px-4 py-3 text-sm outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100"
                  />
                </label>
                <div className="pt-2">
                  <button disabled={securitySaving} className="ui-button w-full text-sm">
                    {securitySaving ? "Saving…" : "Update Password"}
                  </button>
                </div>
              </form>
            </section>
          </div>
        )}

        {/* SECTION 3: DOCUMENTS & TEMPLATES */}
        {activeSection === "documents" && (
          <div id="panel-documents" role="tabpanel" aria-labelledby="tab-documents" className="mt-6 space-y-6">
            <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
              <h2 className="text-xl font-bold text-slate-900">Official Internship Document Templates</h2>
              <p className="mt-1 text-sm text-slate-600">
                Download official, editable Word templates for departmental permission, NOC, and undertakings.
              </p>

              {docError && (
                <p role="alert" className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">
                  {docError}
                </p>
              )}

              <div className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
                {templates.map((template) => (
                  <article
                    key={template.path}
                    className="flex flex-col justify-between rounded-xl border border-slate-200 bg-slate-50/50 p-5"
                  >
                    <div>
                      <span className="rounded-md bg-blue-100 px-2 py-1 text-xs font-bold text-blue-800">
                        DOCX
                      </span>
                      <h3 className="mt-3 text-base font-bold text-slate-900">{template.title}</h3>
                      <p className="mt-2 text-xs leading-5 text-slate-600">{template.description}</p>
                    </div>
                    <button
                      type="button"
                      disabled={downloading === template.path}
                      onClick={() => downloadTemplate(template)}
                      className="ui-button mt-5 text-xs"
                    >
                      {downloading === template.path ? "Downloading…" : "Download Template"}
                    </button>
                  </article>
                ))}
              </div>
            </section>
          </div>
        )}
      </div>
    </main>
  );
}

export default function StudentSettingsPage() {
  return (
    <ProtectedRoute role="STUDENT">
      <AppFrame>
        <Suspense fallback={<p className="py-20 text-center text-sm text-slate-500">Loading settings…</p>}>
          <SettingsContent />
        </Suspense>
      </AppFrame>
    </ProtectedRoute>
  );
}
