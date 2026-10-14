"use client";

import { useEffect, useState } from "react";
import { LastStudentUpdate } from "@/components/LastStudentUpdate";
import { api } from "@/lib/api";
import { downloadResume, ProfessionalProfile, readableFileSize, resumeError, SkillProfile } from "@/lib/professional-profile";

export function AdminProfessionalProfile({ studentId }: { studentId: number }) {
  const [profile, setProfile] = useState<ProfessionalProfile | null>(null);
  const [skills, setSkills] = useState<SkillProfile["skills"]>([]);
  const [loading, setLoading] = useState(true);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    Promise.all([api.get<ProfessionalProfile>(`/admin/students/${studentId}/professional-profile`), api.get<SkillProfile["skills"]>(`/admin/students/${studentId}/professional-profile/skills`)])
      .then(([professional, confirmed]) => { setProfile(professional.data); setSkills(confirmed.data); }).catch(() => setError("Professional profile could not be loaded."))
      .finally(() => setLoading(false));
  }, [studentId]);
  async function download() {
    if (!profile?.resume) return;
    setDownloading(true); setError("");
    try { await downloadResume(`/admin/students/${studentId}/resume/download`, profile.resume.originalFilename); }
    catch (cause) { setError(resumeError(cause, "Resume could not be downloaded.")); }
    finally { setDownloading(false); }
  }
  return <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8"><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Professional Profile</p>{loading ? <p className="mt-3 text-sm text-slate-500">Loading…</p> : error ? <p className="mt-3 text-sm text-red-700">{error}</p> : <><LastStudentUpdate value={profile?.updatedAt ?? profile?.createdAt}/><div className="mt-5 grid gap-5 sm:grid-cols-2"><div><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Headline</p><p className="mt-2 text-sm font-semibold">{profile?.headline ?? "Not added"}</p></div><div><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Career Interests</p><p className="mt-2 whitespace-pre-wrap text-sm">{profile?.careerInterests ?? "Not added"}</p></div></div><div className="mt-6"><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Confirmed Skills</p><LastStudentUpdate value={profile?.skillsUpdatedAt}/>{skills.length ? <div className="mt-2 flex flex-wrap gap-2">{skills.map(skill => <span key={skill.id} className="rounded-full bg-blue-50 px-3 py-1.5 text-xs font-semibold text-blue-800">{skill.displayName}</span>)}</div> : <p className="mt-2 text-sm text-slate-500">No confirmed skills</p>}</div><div className="mt-6 rounded-xl bg-slate-50 p-4"><p className="text-sm font-bold">Resume</p>{profile?.resume && <LastStudentUpdate value={profile.resume.uploadedAt}/>} {profile?.resume ? <div className="mt-2 flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-slate-600">{profile.resume.originalFilename} · {readableFileSize(profile.resume.fileSize)}</p><button data-button-variant="outline" disabled={downloading} onClick={download} className="text-xs">{downloading ? "Downloading…" : "Download"}</button></div> : <p className="mt-2 text-sm text-slate-500">No resume uploaded</p>}</div></>}</section>;
}
