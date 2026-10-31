"use client";
import { useEffect, useRef, useState } from "react";
import type { AcademicSession, SessionDeletionPreview } from "@/lib/academic-sessions";

export function SessionDeletionDialog({ session, preview, busy, error, onClose, onConfirm }: { session: AcademicSession; preview: SessionDeletionPreview | null; busy: boolean; error: string; onClose: () => void; onConfirm: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [name, setName] = useState("");
  useEffect(() => { dialog.current?.showModal(); }, []);
  return <dialog ref={dialog} onCancel={e => { e.preventDefault(); if (!busy) onClose(); }} aria-labelledby="session-delete-title" className="m-auto max-h-[90dvh] w-[min(95vw,36rem)] overflow-y-auto rounded-2xl border border-slate-200 bg-white p-6 text-slate-900 shadow-xl backdrop:bg-slate-950/50">
    <form onSubmit={e => { e.preventDefault(); if (!busy && preview?.canDelete && name === session.name) onConfirm(); }}>
      <h2 id="session-delete-title" className="text-xl font-bold">Permanently delete {session.name}?</h2>
      {!preview && !error && <p role="status" className="mt-4 text-sm">Checking associated students and academic records…</p>}
      {preview && <><dl className="mt-4 space-y-2 text-sm"><div className="flex justify-between gap-3"><dt>Student accounts (including inactive)</dt><dd className="font-semibold">{preview.session.studentCount}</dd></div>{Object.entries(preview.records).map(([label, count]) => <div key={label} className="flex justify-between gap-3"><dt>{label}</dt><dd className="font-semibold">{count}</dd></div>)}</dl>
        <p className={`mt-4 rounded-xl p-3 text-sm ${preview.canDelete ? "bg-red-50 text-red-800" : "bg-amber-50 text-amber-950"}`}>{preview.explanation}</p>
        {!preview.canDelete && <p className="mt-3 text-sm text-slate-600">Existing students, internships, attendance, reports and other history remain available through View. {preview.session.status === "CLOSED" ? "This session is already safely closed." : "Use Close Session to retain this session as history."}</p>}
        {preview.canDelete && <label className="mt-4 block text-sm font-semibold">Type {session.name} to confirm permanent deletion<input autoFocus autoComplete="off" required value={name} disabled={busy} onChange={e => setName(e.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 p-3 font-normal" /></label>}
      </>}
      {error && <p role="alert" className="mt-4 text-sm text-red-700">{error}</p>}
      <div className="mt-5 flex flex-wrap justify-end gap-3"><button type="button" data-button-variant="outline" disabled={busy} onClick={onClose}>Cancel</button><button disabled={busy || !preview?.canDelete || name !== session.name}>{busy ? "Deleting…" : "Permanently Delete Session"}</button></div>
    </form>
  </dialog>;
}
