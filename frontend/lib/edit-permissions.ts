export type EditPermissionScope = "INTERNSHIP_DETAILS" | "STUDENT_PROFILE";
export type EditPermission = {
  scope: EditPermissionScope; internshipStarted: boolean; editable: boolean;
  status: "NOT_REQUIRED" | "LOCKED" | "ACTIVE" | "CONSUMED" | "REVOKED" | "EXPIRED";
  expiresAt: string | null;
};

export function permissionLabel(permission: EditPermission): string {
  if (!permission.internshipStarted) return "Editable until the internship starts";
  if (permission.editable) return `One edit available until ${new Date(permission.expiresAt!).toLocaleString()}`;
  return permission.status === "EXPIRED" ? "Locked — previous access expired" : "Locked — ask your assigned mentor for edit access";
}
