"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { EditPermission, EditPermissionScope, permissionLabel } from "@/lib/edit-permissions";

export function StudentEditPermissionStatus({ scope, onChange }: { scope: EditPermissionScope; onChange?: (editable: boolean) => void }) {
  const [permission, setPermission] = useState<EditPermission | null>(null);
  const onChangeRef = useRef(onChange); onChangeRef.current = onChange;
  useEffect(() => { api.get<EditPermission[]>("/student/edit-permissions").then(({ data }) => {
    const current = data.find(value => value.scope === scope) ?? null; setPermission(current); if (current) onChangeRef.current?.(current.editable);
  }).catch(() => setPermission(null)); }, [scope]);
  if (!permission) return null;
  return <p className={`mt-3 rounded-xl px-4 py-3 text-sm ${permission.editable ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-900"}`}>{permissionLabel(permission)}</p>;
}

export function MentorEditPermissionPanel({ studentId }: { studentId: number }) {
  const [permissions, setPermissions] = useState<EditPermission[]>([]); const [busy, setBusy] = useState<EditPermissionScope | null>(null); const [error, setError] = useState("");
  const load = useCallback(() => api.get<EditPermission[]>(`/mentor/students/${studentId}/edit-permissions`).then(({ data }) => setPermissions(data)).catch(() => setError("Edit permissions could not be loaded.")), [studentId]);
  useEffect(() => { void load(); }, [load]);
  async function change(scope: EditPermissionScope, enabled: boolean) { setBusy(scope); setError(""); try { const { data } = await api.put<EditPermission[]>(`/mentor/students/${studentId}/edit-permissions`, { scope, enabled }); setPermissions(data); } catch { setError("Edit permission could not be changed."); } finally { setBusy(null); } }
  return <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6"><h2 className="text-lg font-bold">Internship details edit access</h2><p className="mt-2 text-sm text-slate-600">Each grant expires after 24 hours and is consumed by one successful save.</p>{error && <p role="alert" className="mt-3 text-sm text-red-700">{error}</p>}<div className="mt-4 space-y-3">{permissions.filter(permission => permission.scope === "INTERNSHIP_DETAILS").map(permission => <div key={permission.scope} className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 p-4"><div><p className="font-semibold">Internship details</p><p className="mt-1 text-xs text-slate-600">{permissionLabel(permission)}</p></div>{permission.internshipStarted && <button type="button" disabled={busy === permission.scope} data-button-variant={permission.editable ? "outline" : undefined} onClick={() => void change(permission.scope, !permission.editable)} className="text-sm">{permission.editable ? "Revoke Edit Access" : "Grant Edit Access"}</button>}</div>)}</div></section>;
}
