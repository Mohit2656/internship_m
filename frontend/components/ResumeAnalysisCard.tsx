"use client";

import { ResumeAnalysis } from "@/lib/professional-profile";

type Props = { hasResume: boolean; loading: boolean; analysis: ResumeAnalysis | null; onAnalyze: () => void };

function EmptyList({ label }: { label: string }) {
  return <p className="mt-2 text-sm text-slate-500">No {label.toLowerCase()} detected.</p>;
}

export function ResumeAnalysisCard({ hasResume, loading, analysis, onAnalyze }: Props) {
  const status = analysis?.status ?? "NOT_PARSED";
  return <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
    <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">AI Resume Analysis</p>
    {!hasResume ? <><h2 className="mt-2 text-xl font-bold">Upload a resume first</h2><p className="mt-2 text-sm text-slate-600">A current PDF or DOCX resume is required for local analysis.</p></>
      : loading || status === "PENDING" ? <><h2 className="mt-2 text-xl font-bold">Analyzing your resume…</h2><p className="mt-2 text-sm text-slate-600">Local DistilBERT analysis may take a moment. Please keep this page open.</p></>
      : status === "FAILED" ? <><h2 className="mt-2 text-xl font-bold">We couldn&apos;t analyze this resume.</h2><p className="mt-2 text-sm text-slate-600">Your resume is unchanged. You can safely retry the analysis.</p><button onClick={onAnalyze} className="mt-5 text-sm">Try Again</button></>
      : status === "NOT_PARSED" ? <><h2 className="mt-2 text-xl font-bold">Your resume has not been analyzed yet.</h2><p className="mt-2 text-sm text-slate-600">Run the local AI model to extract candidate skills, experience, education, and certifications.</p><button onClick={onAnalyze} className="mt-5 text-sm">Analyze Resume</button></>
      : <div className="mt-2">
        <h2 className="text-xl font-bold">Analysis complete</h2>
        <div className="mt-6 grid gap-7 sm:grid-cols-2">
          <div><h3 className="text-sm font-bold uppercase tracking-wide text-slate-500">Skills</h3>{analysis!.skills.length ? <div className="mt-3 flex flex-wrap gap-2">{analysis!.skills.map(skill => <span key={skill.toLowerCase()} className="rounded-full bg-blue-50 px-3 py-1 text-sm font-semibold text-blue-800">{skill}</span>)}</div> : <EmptyList label="Skills" />}</div>
          <div><h3 className="text-sm font-bold uppercase tracking-wide text-slate-500">Certifications</h3>{analysis!.certifications.length ? <ul className="mt-3 space-y-2 text-sm text-slate-800">{analysis!.certifications.map(item => <li key={item.toLowerCase()}>{item}</li>)}</ul> : <EmptyList label="Certifications" />}</div>
          <div><h3 className="text-sm font-bold uppercase tracking-wide text-slate-500">Experience</h3>{analysis!.experience.length ? <div className="mt-3 space-y-4">{analysis!.experience.map((item, index) => <div key={`${item.title}-${item.company}-${index}`}><p className="text-sm font-semibold text-slate-900">{item.title ?? "Role not detected"}</p><p className="text-sm text-slate-600">{item.company ?? "Company not detected"}</p>{(item.startDate || item.endDate) && <p className="text-xs text-slate-500">{[item.startDate, item.endDate].filter(Boolean).join(" – ")}</p>}</div>)}</div> : <EmptyList label="Experience" />}</div>
          <div><h3 className="text-sm font-bold uppercase tracking-wide text-slate-500">Education</h3>{analysis!.education.length ? <div className="mt-3 space-y-4">{analysis!.education.map((item, index) => <div key={`${item.degree}-${item.institution}-${index}`}><p className="text-sm font-semibold text-slate-900">{[item.degree, item.field].filter(Boolean).join(" in ") || "Qualification detected"}</p><p className="text-sm text-slate-600">{item.institution ?? "Institution not detected"}</p></div>)}</div> : <EmptyList label="Education" />}</div>
        </div>
        <p className="mt-7 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900">AI-generated information may contain mistakes. Review the extracted information before adding it to your professional profile.</p>
      </div>}
  </section>;
}
