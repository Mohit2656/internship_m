export type PasswordResetResponse = { userId: number; email: string; temporaryPassword: string; mustChangePassword: boolean };
export function resetErrorMessage(cause: unknown): string {
  const status = (cause as { response?: { status?: number } })?.response?.status;
  if (status === 401) return "Your session has expired. Sign in again.";
  if (status === 403) return "You do not have permission to reset passwords.";
  if (status === 404) return "This account no longer exists.";
  if (status === 409) return "Only active student and mentor accounts can be reset.";
  return "Unable to confirm the reset. Check with the user before trying again; the server may have completed it.";
}
