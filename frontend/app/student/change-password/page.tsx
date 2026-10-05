"use client";

import { AppFrame } from "@/components/AppFrame";
import { ChangePasswordForm } from "@/components/ChangePasswordForm";
import { ProtectedRoute } from "@/components/ProtectedRoute";

export default function ChangePasswordPage() { return <ProtectedRoute role="STUDENT"><AppFrame><ChangePasswordForm destination="/student/dashboard" /></AppFrame></ProtectedRoute>; }
