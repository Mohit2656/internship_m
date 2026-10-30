"use client";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { publishStudentAccountsChanged, StudentDeletionPreview, StudentDeletionResult } from "@/lib/student-deletion";

export function DeleteStudentButton({ student, onDeleted }: { student: { id: number; studentName: string | null; uid: string | null; loginEmail: string }; onDeleted: (result: StudentDeletionResult) => void | Promise<void> }) {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [preview, setPreview] = useState<StudentDeletionPreview | null>(null);
  const [reason, setReason] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const dialog = useRef<HTMLDialogElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const request = useRef<AbortController | null>(null);
  const busy = useRef(false);
  useEffect(() => { if (open) dialog.current?.showModal(); }, [open]);
  useEffect(() => () => { request.current?.abort(); }, []);
  function close() { if (busy.current) return; request.current?.abort(); setOpen(false); setPreview(null); setReason(""); setConfirmed(false); setError(""); trigger.current?.focus(); }
  async function start() {
    request.current?.abort(); const controller = new AbortController(); request.current = controller;
    setOpen(true); setPreview(null); setReason(""); setConfirmed(false); setError("");
    try { const { data } = await api.get<StudentDeletionPreview>(`/admin/students/${student.id}/deletion-preview`, { signal: controller.signal }); if (!controller.signal.aborted) setPreview(data); }
    catch (cause) { if (!controller.signal.aborted) setError((cause as { response?: { data?: { message?: string } } }).response?.data?.message || "Related student records could not be checked. No account has been changed."); }
  }
  async function remove() {
    if (busy.current || !preview?.active || !confirmed || reason.trim().length < 5 || reason.trim().length > 500) return;
    busy.current = true; setPending(true); setError("");
    try {
      const { data } = await api.delete<StudentDeletionResult>(`/admin/students/${preview.studentId}`, { data: { confirmed: true, confirmationEmail: preview.email, reason: reason.trim() } });
      publishStudentAccountsChanged(); setOpen(false); await onDeleted(data);
    } catch (cause) { setError((cause as { response?: { data?: { message?: string } } }).response?.data?.message || "The student could not be deleted. Refresh the record and try again."); }
    finally { busy.current = false; setPending(false); }
  }
  if (user?.role !== "ADMIN" || user.id === student.id) return null;
  return <><button ref={trigger} type="button" data-button-variant="outline" className="mt-3 ml-2 text-sm text-red-700" onClick={() => void start()}>Delete Student</button>
    {open && <dialog ref={dialog} onCancel={e => { e.preventDefault(); close(); }} aria-labelledby={`delete-student-${student.id}`} className="m-auto max-h-[90dvh] w-[min(95vw,38rem)] overflow-y-auto rounded-2xl border border-slate-200 bg-white p-6 text-slate-900 shadow-xl backdrop:bg-slate-950/50">
      <form onSubmit={e => { e.preventDefault(); void remove(); }}><h2 id={`delete-student-${student.id}`} className="text-xl font-bold">Delete Student</h2>
        <dl className="mt-4 space-y-2 break-words text-sm"><div><dt className="text-xs text-slate-500">Student</dt><dd className="font-semibold">{preview?.name ?? student.studentName ?? "Name not recorded"}</dd></div><div><dt className="text-xs text-slate-500">UID</dt><dd>{preview?.uid ?? student.uid ?? "UID not recorded"}</dd></div><div><dt className="text-xs text-slate-500">Email</dt><dd>{preview?.email ?? student.loginEmail}</dd></div></dl>
        <p className="mt-4 rounded-xl bg-amber-50 p-3 text-sm text-amber-950">This deactivates the student account and invalidates existing logins. Historical attendance, GPS evidence, internship records, reports, approvals, assignments, resumes, matching history and files will be retained.</p>
        {!preview && !error && <p role="status" className="mt-4 text-sm text-slate-500">Checking related records…</p>}
        {preview && <><details className="mt-4 rounded-xl border border-slate-200 p-3 text-sm"><summary className="cursor-pointer font-semibold">Related records that will be preserved</summary><dl className="mt-3 space-y-2">{Object.entries(preview.dependencies).map(([label, count]) => <div key={label} className="flex justify-between gap-3"><dt>{label}</dt><dd className="font-semibold">{count}</dd></div>)}</dl><p className="mt-3 text-xs text-slate-500">{preview.applicationsNote}</p></details>{!preview.active && <p className="mt-4 text-sm text-amber-800">This student is already deactivated. Reload the directory.</p>}</>}
        <label className="mt-4 block text-sm font-semibold">Reason for deletion (required)<textarea autoFocus required minLength={5} maxLength={500} disabled={pending} value={reason} onChange={e => setReason(e.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 p-3 font-normal" /></label>
        <label className="mt-4 flex items-start gap-2 text-sm"><input type="checkbox" checked={confirmed} disabled={pending} onChange={e => setConfirmed(e.target.checked)} className="mt-1" />I confirm that this is the correct student and want to deactivate their account while preserving their historical records.</label>
        {error && <p role="alert" className="mt-4 text-sm text-red-700">{error}</p>}
        <div className="mt-5 flex flex-wrap justify-end gap-3"><button type="button" data-button-variant="outline" disabled={pending} onClick={close}>Cancel</button><button disabled={pending || !preview?.active || !confirmed || reason.trim().length < 5 || reason.trim().length > 500}>{pending ? "Deleting…" : "Confirm Delete Student"}</button></div>
      </form>
    </dialog>}
  </>;
}
