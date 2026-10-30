"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { downloadResume, ProfessionalProfile, readableFileSize, resumeError } from "@/lib/professional-profile";
import { formatDateTime } from "@/lib/monthly-progress";
import { ResumeCorrectionEditor } from "@/components/ResumeCorrectionEditor";

export function MentorResumeReview({ studentId }: { studentId: number }) {
  const [profile, setProfile] = useState<ProfessionalProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setProfile(null); setError("");
    api.get<ProfessionalProfile>(`/mentor/students/${studentId}/professional-profile`, { signal: abort.signal })
      .then(({ data }) => { if (!abort.signal.aborted) setProfile(data); })
      .catch(cause => { if (!abort.signal.aborted) setError(resumeError(cause, "Assigned student's resume could not be loaded.")); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [studentId]);
  async function download() {
    if (!profile?.resume || busy) return;
    setBusy(true); setError("");
    try { await downloadResume(`/mentor/students/${studentId}/resume/download`, profile.resume.originalFilename); }
    catch (cause) { setError(resumeError(cause, "Resume could not be downloaded. Assignment access may have changed.")); }
    finally { setBusy(false); }
  }
  return <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6"><h2 className="text-lg font-bold">Student resume review</h2>{error && <p role="alert" className="mt-3 text-sm text-red-700">{error}</p>}{loading ? <p role="status" className="mt-3 text-sm text-slate-500">Loading resume…</p> : profile?.resume ? <><div className="mt-4 flex flex-wrap items-center justify-between gap-4"><div><p className="font-semibold">{profile.resume.originalFilename}</p><p className="mt-1 text-xs text-slate-500">Uploaded {formatDateTime(profile.resume.uploadedAt)} · {readableFileSize(profile.resume.fileSize)}</p></div><button type="button" data-button-variant="outline" disabled={busy} onClick={() => void download()} className="text-sm">{busy ? "Downloading…" : "View / Download resume"}</button></div>{profile.resume.parsingStatus === "PARSED" ? <div className="mt-5"><ResumeCorrectionEditor key={profile.resume.id} readOnly endpoint={`/mentor/students/${studentId}/resume/analysis/corrections`} resumeId={profile.resume.id} /></div> : <p className="mt-4 text-sm text-slate-500">Parsed information is available after the student completes resume analysis.</p>}</> : !error && <p className="mt-3 text-sm text-slate-500">No resume uploaded.</p>}</section>;
}
