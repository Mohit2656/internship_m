import { WorkflowStatus, workflowLabels } from "@/lib/internship-onboarding";

export function WorkflowBadge({ status }: { status: WorkflowStatus }) {
  const tone = status === "DIARY_ISSUED" || status === "INTERNSHIP_DETAILS_COMPLETE" ? "bg-emerald-100 text-emerald-700" : status === "NOT_SECURED" ? "bg-slate-100 text-slate-700" : "bg-blue-100 text-blue-700";
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ${tone}`}>{workflowLabels[status]}</span>;
}
