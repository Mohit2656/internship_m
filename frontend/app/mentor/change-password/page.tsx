"use client";

import { AppFrame } from "@/components/AppFrame";
import { ChangePasswordForm } from "@/components/ChangePasswordForm";
import { ProtectedRoute } from "@/components/ProtectedRoute";

export default function MentorChangePasswordPage() {
  return <ProtectedRoute role="MENTOR"><AppFrame><ChangePasswordForm destination="/mentor/dashboard" /></AppFrame></ProtectedRoute>;
}
