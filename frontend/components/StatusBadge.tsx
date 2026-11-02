import { ProfileStatus } from "@/lib/student-profile";
export function StatusBadge({ status }: { status: ProfileStatus }) {
  return <span className="ui-status" data-tone={status === "COMPLETE" ? "success" : "warning"}>{status === "COMPLETE" ? "Complete" : "Incomplete"}</span>;
}

