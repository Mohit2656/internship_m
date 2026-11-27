"use client";

import { ChangeEvent, FormEvent, useEffect, useState } from "react";
import { SkillProfileCard } from "@/components/SkillProfileCard";
import { notifyStudentCompletionChanged } from "@/components/StudentCompletionGate";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/monthly-progress";
import {
  downloadResume,
  ProfessionalProfile,
  readableFileSize,
  ResumeAnalysis,
  resumeAccept,
  resumeError,
  resumeMaxBytes,
} from "@/lib/professional-profile";

/**
 * Reusable Professional Profile, Resume, and Skills workspace panel.
 * Shared by `/student/professional-profile` and the inline `/student/settings` switcher.
 */
export function ProfessionalProfilePanel({
  onProfileUpdated,
  onSkillsUpdated,
}: {
  onProfileUpdated?: (profile: ProfessionalProfile) => void;
  onSkillsUpdated?: () => void;
}) {
  const [profile, setProfile] = useState<ProfessionalProfile | null>(null);
  const [headline, setHeadline] = useState("");
  const [careerInterests, setCareerInterests] = useState("");
  const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [skillRefreshKey, setSkillRefreshKey] = useState(0);

  useEffect(() => {
    api.get<ProfessionalProfile>("/student/professional-profile")
      .then(({ data }) => {
        setProfile(data);
        setHeadline(data.headline ?? "");
        setCareerInterests(data.careerInterests ?? "");
        setEditing(!data.id);
      })
      .catch(() => setError("Your professional profile could not be loaded."))
      .finally(() => setLoading(false));
  }, []);

  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy("profile");
    setError("");
    setNotice("");
    try {
      const { data } = await api.put<ProfessionalProfile>("/student/professional-profile", {
        headline,
        careerInterests,
      });
      setProfile(data);
      setHeadline(data.headline ?? "");
      setCareerInterests(data.careerInterests ?? "");
      setEditing(false);
      setNotice("Professional profile saved.");
      onProfileUpdated?.(data);
      notifyStudentCompletionChanged();
    } catch (cause) {
      setError(resumeError(cause, "Your professional profile could not be saved."));
    } finally {
      setBusy("");
    }
  }

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    const extension = file.name.split(".").pop()?.toLowerCase();
    if (extension !== "pdf" && extension !== "docx") {
      setError("Only PDF and DOCX resumes are allowed.");
      return;
    }
    if (file.size > resumeMaxBytes) {
      setError("Resumes must be 5 MB or smaller.");
      return;
    }
    setBusy("resume");
    setError("");
    setNotice("");
    const body = new FormData();
    body.append("file", file);
    const replacing = Boolean(profile?.resume);
    try {
      const { data } = await api.post<ProfessionalProfile>("/student/resume", body, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      setProfile(data);
      onProfileUpdated?.(data);
      notifyStudentCompletionChanged();
      if (data.resume) {
        try {
          await api.post<ResumeAnalysis>("/student/resume/analysis");
          setSkillRefreshKey((value) => value + 1);
          onSkillsUpdated?.();
          setNotice(
            replacing
              ? "Resume updated and recognized skills synchronized."
              : "Resume uploaded and recognized skills synchronized."
          );
        } catch (cause) {
          setError(
            resumeError(
              cause,
              "Resume uploaded, but recognized skills could not be synchronized. Re-upload the resume to retry."
            )
          );
        }
      }
    } catch (cause) {
      setError(resumeError(cause, "Your resume could not be uploaded."));
    } finally {
      setBusy("");
    }
  }

  async function download() {
    if (!profile?.resume) return;
    setBusy("download");
    setError("");
    try {
      await downloadResume("/student/resume/download", profile.resume.originalFilename);
    } catch (cause) {
      setError(resumeError(cause, "Your resume could not be downloaded."));
    } finally {
      setBusy("");
    }
  }

  if (loading) {
    return <p className="py-12 text-center text-sm text-slate-500">Loading professional profile…</p>;
  }

  return (
    <div className="space-y-6">
      {(error || notice) && (
        <p
          role={error ? "alert" : "status"}
          className={`rounded-xl px-4 py-3 text-sm ${
            error ? "bg-red-50 text-red-700" : "bg-emerald-50 text-emerald-800"
          }`}
        >
          {error || notice}
        </p>
      )}
      <section className="rounded-2xl border border-slate-200 bg-white p-5 sm:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">
              Professional summary
            </p>
            <h2 className="mt-2 text-xl font-bold">Headline &amp; Career Interests</h2>
          </div>
          {profile?.id && !editing && (
            <button data-button-variant="outline" onClick={() => setEditing(true)} className="text-sm">
              Edit
            </button>
          )}
        </div>
        {editing ? (
          <form onSubmit={save} className="mt-6 space-y-5">
            <label className="block text-sm font-semibold">
              Headline
              <span className="mt-1 block text-xs font-normal text-slate-500">
                A short professional introduction, up to 160 characters.
              </span>
              <input
                className="input mt-2"
                maxLength={160}
                value={headline}
                onChange={(event) => setHeadline(event.target.value)}
                placeholder="Backend Developer | Java | Spring Boot"
              />
            </label>
            <label className="block text-sm font-semibold">
              Career Interests
              <span className="mt-1 block text-xs font-normal text-slate-500">
                Describe the roles and technical areas you want to pursue.
              </span>
              <textarea
                className="input mt-2 min-h-28 resize-y"
                maxLength={1000}
                value={careerInterests}
                onChange={(event) => setCareerInterests(event.target.value)}
                placeholder="Backend Development, DevOps, Cloud Engineering"
              />
            </label>
            <div className="flex flex-wrap gap-3">
              <button disabled={busy === "profile"} className="text-sm">
                {busy === "profile" ? "Saving…" : "Save Profile"}
              </button>
              {profile?.id && (
                <button
                  data-button-variant="outline"
                  type="button"
                  onClick={() => {
                    setEditing(false);
                    setHeadline(profile.headline ?? "");
                    setCareerInterests(profile.careerInterests ?? "");
                  }}
                  className="text-sm"
                >
                  Cancel
                </button>
              )}
            </div>
          </form>
        ) : (
          <dl className="mt-6 grid gap-5 sm:grid-cols-2">
            <div>
              <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">Headline</dt>
              <dd className="mt-2 text-sm font-semibold text-slate-900">
                {profile?.headline ?? "Not added"}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Career Interests
              </dt>
              <dd className="mt-2 whitespace-pre-wrap text-sm text-slate-700">
                {profile?.careerInterests ?? "Not added"}
              </dd>
            </div>
          </dl>
        )}
      </section>
      <section className="rounded-2xl border border-slate-200 bg-white p-5 sm:p-8">
        <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Resume</p>
        <div className="mt-2 flex flex-wrap items-start justify-between gap-5">
          <div>
            <h2 className="text-xl font-bold">
              {profile?.resume ? "Current Resume" : "No resume uploaded"}
            </h2>
            {profile?.resume ? (
              <>
                <p className="mt-2 text-sm font-semibold text-slate-900">
                  {profile.resume.originalFilename}
                </p>
                <p className="mt-1 text-sm text-slate-600">
                  Uploaded {formatDateTime(profile.resume.uploadedAt)} ·{" "}
                  {readableFileSize(profile.resume.fileSize)}
                </p>
              </>
            ) : (
              <p className="mt-2 text-sm text-slate-600">
                Upload a PDF or DOCX resume. Maximum file size: 5 MB.
              </p>
            )}
          </div>
          <div className="flex flex-wrap gap-3">
            {profile?.resume && (
              <button
                data-button-variant="outline"
                disabled={!!busy}
                onClick={download}
                className="text-sm"
              >
                {busy === "download" ? "Downloading…" : "View / Download"}
              </button>
            )}
            <label
              className={`cursor-pointer rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-semibold text-white ${
                busy ? "pointer-events-none opacity-50" : ""
              }`}
            >
              {busy === "resume" ? "Uploading…" : profile?.resume ? "Re-upload Resume" : "Upload Resume"}
              <input type="file" accept={resumeAccept} onChange={upload} className="sr-only" />
            </label>
          </div>
        </div>
      </section>
      <SkillProfileCard refreshKey={skillRefreshKey} onSkillsUpdated={onSkillsUpdated} />
    </div>
  );
}

