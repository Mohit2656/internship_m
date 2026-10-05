"use client";

import { Role } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useRouter } from "next/navigation";
import { usePathname } from "next/navigation";
import { useEffect } from "react";

export function ProtectedRoute({ role, children }: { role: Role; children: React.ReactNode }) {
  const { user, loading } = useAuth(); const router = useRouter(); const pathname = usePathname();
  const home = (accountRole: Role) => accountRole === "ADMIN" ? "/admin/dashboard" : accountRole === "MENTOR" ? "/mentor/dashboard" : "/student/dashboard";
  const changePassword = (accountRole: Role) => accountRole === "MENTOR" ? "/mentor/change-password" : "/student/change-password";
  useEffect(() => {
    if (loading) return;
    if (!user) router.replace("/login");
    else if (user.role !== role) router.replace(home(user.role));
    else if ((user.role === "STUDENT" || user.role === "MENTOR") && user.mustChangePassword && pathname !== changePassword(user.role)) router.replace(changePassword(user.role));
  }, [loading, user, role, router, pathname]);
  const pendingRedirect = Boolean(user && user.role === role && (user.role === "STUDENT" || user.role === "MENTOR") && user.mustChangePassword && pathname !== changePassword(user.role));
  if (loading || !user || user.role !== role || pendingRedirect) return <main className="grid min-h-screen place-items-center"><p className="text-sm text-slate-500">Checking your session…</p></main>;
  return <>{children}</>;
}
