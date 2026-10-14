"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { api } from "@/lib/api";
import { resetErrorMessage, PasswordResetResponse } from "@/lib/password-reset";

export function ResetPasswordButton({ userId, name, email, disabled = false }: { userId: number; name: string; email: string; disabled?: boolean }) {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [copyStatus, setCopyStatus] = useState("");
  const busy = useRef(false);
  const generation = useRef(0);
  const pathname = usePathname();
  const trigger = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    generation.current++;
    busy.current = false; setOpen(false); setPassword(""); setPending(false); setError(""); setCopyStatus("");
    return () => { generation.current++; };
  }, [pathname]);
  useEffect(() => { if (open) dialog.current?.showModal(); }, [open]);
  function close() {
    if (busy.current) return;
    generation.current++; setPassword(""); setError(""); setCopyStatus(""); setOpen(false); trigger.current?.focus();
  }
  async function reset() {
    if (busy.current || password) return;
    busy.current = true; setPending(true); setError("");
    const requestGeneration = generation.current;
    try {
      const { data } = await api.post<PasswordResetResponse>(`/admin/users/${userId}/reset-password`);
      if (requestGeneration === generation.current) setPassword(data.temporaryPassword);
    } catch (cause) {
      if (requestGeneration === generation.current) setError(resetErrorMessage(cause));
    } finally {
      if (requestGeneration === generation.current) { busy.current = false; setPending(false); }
    }
  }
  async function copy() {
    const requestGeneration = generation.current;
    try { await navigator.clipboard.writeText(password); if (requestGeneration === generation.current) setCopyStatus("Copied. Share securely, then close this dialog."); }
    catch { if (requestGeneration === generation.current) setCopyStatus("Unable to copy. Select the password and copy it manually."); }
  }
  return <>
    <button data-button-variant="outline" ref={trigger} type="button" disabled={disabled} onClick={() => setOpen(true)} className="mt-3 text-sm">Reset Password</button>
    {open && <dialog ref={dialog} onCancel={event => { event.preventDefault(); close(); }} aria-labelledby={`reset-title-${userId}`} className="m-auto w-[calc(100%-2rem)] max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl backdrop:bg-slate-950/50">
      <h2 id={`reset-title-${userId}`} className="text-xl font-bold">Reset password</h2>
      <p className="mt-3 break-words font-semibold">{name}</p><p className="break-words text-sm text-slate-600">{email}</p>
      {password ? <><p className="mt-4 text-sm">Temporary password (shown only here):</p><code className="mt-2 block select-all break-all rounded-lg bg-slate-100 p-3">{password}</code><p className="mt-3 text-sm text-amber-800">Share this securely with the user. Do not leave it visible on screen. The user must change it at their next login.</p><button type="button" onClick={() => void copy()} className="mt-4">Copy password</button><p role="status" className="mt-2 text-sm">{copyStatus}</p></> : <p className="mt-4 text-sm text-slate-600">This replaces the current password and invalidates existing sessions. The user must change the temporary password before continuing.</p>}
      {error && <p role="alert" className="mt-4 text-sm text-red-700">{error}</p>}
      <div className="mt-5 flex justify-end gap-3"><button data-button-variant="outline" type="button" disabled={pending} onClick={close} className="">{password ? "Close and clear" : "Cancel"}</button>{!password && <button type="button" disabled={pending} onClick={() => void reset()} className="">{pending ? "Resetting..." : "Confirm reset"}</button>}</div>
    </dialog>}
  </>;
}
