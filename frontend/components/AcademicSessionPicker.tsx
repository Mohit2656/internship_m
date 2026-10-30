import Link from "next/link";
import { AcademicSession } from "@/lib/academic-sessions";
export function AcademicSessionPicker({ sessions, selected, onChange }: { sessions: AcademicSession[]; selected: number | null; onChange: (id: number) => void }) {
  return <div className="flex flex-wrap items-center gap-3"><label className="text-sm font-semibold">Academic session <select aria-label="Academic session" value={selected ?? ""} onChange={e => onChange(Number(e.target.value))} className="ml-2 rounded-xl border border-slate-300 bg-white px-3 py-2"><option value="" disabled>Select a session</option>{sessions.map(s => <option key={s.id} value={s.id}>{s.name}{s.active ? " (Active)" : ""}</option>)}</select></label>{!sessions.some(s => s.active) && <Link href="/admin/settings/academic-sessions" className="text-sm font-semibold text-amber-800">Activate a session in Settings</Link>}</div>;
}
