"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { MentorAccount } from "@/lib/mentor-accounts";

type Props = { mentor: MentorAccount; onDeactivated: (mentor: MentorAccount) => void };

function deletionError(cause: unknown) {
  const response = (cause as { response?: { status?: number; data?: { code?: string } } })?.response;
  const status = response?.status;
  if (status === 401) return "Your session has expired. Sign in again.";
  if (status === 403) return "You do not have permission to delete mentor accounts.";
  if (status === 404) return response?.data?.code === "MENTOR_NOT_FOUND"
    ? "This mentor account or its profile could not be found. Refresh the list."
    : "Mentor deletion is unavailable on the server. Please contact your system administrator.";
  if (status === 409) return "This account cannot be deleted. Refresh the list before trying again.";
  return "Unable to confirm deletion. Refresh the list before trying again; the account may already be deactivated.";
}

export function DeleteMentorAccountButton({ mentor, onDeactivated }: Props) {
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const busy = useRef(false);
  const mounted = useRef(true);
  const trigger = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const status = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);
  useEffect(() => { if (open) dialog.current?.showModal(); }, [open]);
  useEffect(() => { if (success) status.current?.focus(); }, [success]);

  function close() {
    if (busy.current) return;
    setOpen(false); setError(""); trigger.current?.focus();
  }

  async function remove() {
    if (busy.current || !mentor.active) return;
    busy.current = true; setPending(true); setError("");
    try {
      const { data } = await api.delete<MentorAccount>(`/admin/mentor-accounts/${mentor.userId}`);
      if (!mounted.current) return;
      setOpen(false);
      setSuccess(`${data.name}'s account was deactivated. Login is disabled; student assignments and historical records were retained.`);
      onDeactivated(data);
    } catch (cause) {
      if (mounted.current) setError(deletionError(cause));
    } finally {
      busy.current = false;
      if (mounted.current) setPending(false);
    }
  }

  const titleId = `delete-mentor-title-${mentor.userId}`;
  const descriptionId = `delete-mentor-description-${mentor.userId}`;

  return <>
    {mentor.active && <button ref={trigger} data-button-variant="outline" type="button" disabled={pending} onClick={() => { setError(""); setOpen(true); }} className="mt-3 text-sm">Delete</button>}
    {success && <p ref={status} tabIndex={-1} role="status" className="mt-3 rounded-lg bg-blue-50 px-4 py-3 text-sm text-blue-800">{success}</p>}
    {open && <dialog ref={dialog} onCancel={event => { event.preventDefault(); close(); }} aria-labelledby={titleId} aria-describedby={descriptionId} aria-busy={pending} className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-2xl border border-slate-200 bg-white p-6 shadow-xl backdrop:bg-slate-950/50">
      <h2 id={titleId} className="text-xl font-bold">Delete Mentor Account</h2>
      <p className="mt-3 break-words font-semibold">{mentor.name}</p>
      <p className="break-words text-sm text-slate-600">{mentor.instituteEmail}</p>
      <div id={descriptionId} className="mt-4 space-y-3 text-sm text-slate-600">
        <p>Deleting this account will deactivate it, prevent future login, and remove it from the mentor list. Creating it again requires completing the Create Mentor form.</p>
        <p>The mentor will no longer be able to review reports. {mentor.assignedStudentCount} student{mentor.assignedStudentCount === 1 ? " is" : "s are"} currently assigned. These assignments will remain in place and student data will stay unchanged.</p>
        <p>Student accounts, attendance, reports, approval history, audit records, and historical mentor attribution will be preserved.</p>
      </div>
      {error && <p role="alert" className="mt-4 text-sm text-red-700">{error}</p>}
      <div className="mt-5 flex flex-wrap justify-end gap-3">
        <button autoFocus data-button-variant="outline" type="button" disabled={pending} onClick={close}>Cancel</button>
        <button type="button" disabled={pending} onClick={() => void remove()}>{pending ? "Deleting..." : "Confirm deletion"}</button>
      </div>
    </dialog>}
  </>;
}
