"use client";

import { useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";

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

function DocumentIcon() {
  return <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="h-7 w-7">
    <path strokeLinecap="round" strokeLinejoin="round" d="M6.75 2.75h6.7l3.8 3.8v14.7H6.75z" />
    <path strokeLinecap="round" strokeLinejoin="round" d="M13.25 2.75v4h4M9.25 11h5.5M9.25 14.25h5.5M9.25 17.5h3.25" />
  </svg>;
}

export default function StudentDocumentsPage() {
  const [downloading, setDownloading] = useState<string | null>(null);
  const [error, setError] = useState("");

  async function download(template: DocumentTemplate) {
    setDownloading(template.path);
    setError("");
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
      setError(`The file “${template.title}” is currently unavailable. Please try again later or contact the III Cell.`);
    } finally {
      setDownloading(null);
    }
  }

  return <ProtectedRoute role="STUDENT"><AppFrame><main className="mx-auto max-w-6xl px-5 py-10 sm:px-8">
    <p className="text-sm font-semibold text-blue-700">Student workspace</p>
    <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Internship Documents &amp; Templates</h1>
    <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-600">Download official, editable Word templates for your internship application and documentation.</p>

    <section className="mt-8 rounded-2xl border border-blue-200 bg-blue-50 p-5 sm:p-6" aria-labelledby="document-instructions">
      <h2 id="document-instructions" className="text-base font-bold text-slate-950">Before you begin</h2>
      <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-700">Download the required document, open it in Microsoft Word or Google Docs, fill in your details, and obtain the necessary signatures before submission.</p>
    </section>

    {error && <p role="alert" className="mt-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</p>}

    <div className="mt-6 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
      {templates.map(template => <section key={template.path} className="flex min-h-72 flex-col rounded-2xl border border-slate-200 bg-white p-6 sm:p-7">
        <div className="flex items-start justify-between gap-4">
          <span className="grid h-14 w-14 shrink-0 place-items-center rounded-2xl bg-blue-50 text-blue-700"><DocumentIcon /></span>
          <span className="rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-bold tracking-wide text-blue-700">DOCX</span>
        </div>
        <h2 className="mt-5 text-lg font-bold text-slate-950">{template.title}</h2>
        <p className="mt-3 flex-1 text-sm leading-6 text-slate-600">{template.description}</p>
        <button type="button" className="mt-6 inline-flex w-full items-center justify-center gap-2 sm:w-auto" disabled={downloading !== null} onClick={() => void download(template)}>
          <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-5 w-5"><path strokeLinecap="round" strokeLinejoin="round" d="M12 3v12m0 0 4-4m-4 4-4-4M5 20h14" /></svg>
          {downloading === template.path ? "Downloading…" : "Download DOCX"}
        </button>
      </section>)}
    </div>
  </main></AppFrame></ProtectedRoute>;
}
