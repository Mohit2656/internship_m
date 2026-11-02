import { WorkflowStatus, workflowLabels } from "@/lib/internship-onboarding";

export function WorkflowBadge({ status }: { status: WorkflowStatus }) {
  const tone = status === "DIARY_ISSUED" || status === "INTERNSHIP_DETAILS_COMPLETE" ? "success" : status === "NOT_SECURED" ? "neutral" : "info";
  return <span className="ui-status" data-tone={tone}>{workflowLabels[status]}</span>;
}
