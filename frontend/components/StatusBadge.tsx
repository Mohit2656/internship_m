import { ProfileStatus } from "@/lib/student-profile";
export function StatusBadge({ status }: { status: ProfileStatus }) {
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ${status === "COMPLETE" ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-800"}`}>{status === "COMPLETE" ? "Complete" : "Incomplete"}</span>;
}

