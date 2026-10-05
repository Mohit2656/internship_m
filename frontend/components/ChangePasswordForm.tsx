"use client";

import { FormEvent, useState } from "react";
import { AxiosError } from "axios";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export function ChangePasswordForm({ destination }: { destination: string }) {
  const router = useRouter(); const { refresh } = useAuth();
  const [newPassword, setNewPassword] = useState(""); const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState(""); const [saving, setSaving] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); setError("");
    if (newPassword !== confirmPassword) { setError("Passwords do not match."); return; }
    setSaving(true);
    try { await api.post("/auth/change-password", { newPassword, confirmPassword }); await refresh(); router.replace(destination); }
    catch (cause) { setError((cause as AxiosError<ApiError>).response?.data?.message ?? "Unable to change your password."); }
    finally { setSaving(false); }
  }
  return <main className="mx-auto grid min-h-[calc(100vh-73px)] max-w-lg place-items-center px-5 py-10"><section className="w-full rounded-2xl border border-slate-200 bg-white p-7 shadow-sm"><p className="text-sm font-semibold text-blue-700">Password update required</p><h1 className="mt-2 text-2xl font-bold text-slate-950">Create a new password</h1><p className="mt-3 text-sm leading-6 text-slate-600">You are using your temporary password. Please create a new password to continue.</p><p className="mt-3 text-xs text-slate-500">Use at least 12 characters.</p><form onSubmit={submit} className="mt-6 space-y-4"><label className="block"><span className="mb-2 block text-sm font-semibold">New password</span><input autoComplete="new-password" type="password" minLength={12} maxLength={128} required value={newPassword} onChange={e => setNewPassword(e.target.value)} className="w-full rounded-xl border border-slate-300 px-4 py-3 outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100" /></label><label className="block"><span className="mb-2 block text-sm font-semibold">Confirm new password</span><input autoComplete="new-password" type="password" minLength={12} maxLength={128} required value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} className="w-full rounded-xl border border-slate-300 px-4 py-3 outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100" /></label>{error && <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}<button disabled={saving} className="w-full rounded-xl bg-blue-700 px-4 py-3 font-semibold text-white disabled:opacity-60">{saving ? "Saving…" : "Update password"}</button></form></section></main>;
}
