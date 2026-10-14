"use client";

import { useRef, useState } from "react";
import { api } from "@/lib/api";

export function MentorRecordDelete({ kind, id, description, onDeleted }: { kind: "attendance" | "monthly-progress"; id: number; description: string; onDeleted: () => void | Promise<void> }) {
  const pending = useRef(false);
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  async function remove() {
    if (pending.current || reason.trim().length < 5) return;
    pending.current = true;
    setBusy(true); setError("");
    try {
      await api.delete(`/mentor/${kind}/${id}`, { data: { reason: reason.trim() } });
      setOpen(false); setReason(""); setSuccess("Selected record deleted. Evidence retained for audit.");
      await onDeleted();
    } catch { setError("Unable to delete or refresh this record. Refresh the list before retrying."); }
    finally { pending.current = false; setBusy(false); }
  }
  return <>
    <button data-button-variant="outline" type="button" disabled={busy} onClick={() => { setOpen(true); setError(""); setSuccess(""); }} className="text-sm">Delete</button>
    {success && <p role="status" className="mt-2 text-sm text-emerald-700">{success}</p>}
    {error && !open && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}
    {open && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4"><section role="dialog" aria-modal="true" aria-label="Confirm record deletion" className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
      <h3 className="text-lg font-bold">Delete selected record?</h3>
      <p className="mt-3 text-sm text-slate-700">{description}</p>
      <p className="mt-2 text-sm text-slate-600">This removes this record from active dashboards and reports. Original evidence is retained for audit and recovery.</p>
      <label className="mt-4 block text-sm font-semibold">Deletion reason<textarea autoFocus required minLength={5} maxLength={500} value={reason} onChange={e => setReason(e.target.value)} disabled={busy} className="mt-2 w-full rounded-lg border border-slate-300 p-2" /></label>
      {error && <p role="alert" className="mt-3 text-sm text-red-700">{error}</p>}
      <div className="mt-5 flex justify-end gap-3"><button data-button-variant="outline" type="button" disabled={busy} onClick={() => { setOpen(false); setReason(""); }} className="text-sm">Cancel</button><button type="button" disabled={busy || reason.trim().length < 5} onClick={() => void remove()} className="text-sm">{busy ? "Deleting..." : "Delete selected record"}</button></div>
    </section></div>}
  </>;
}
