"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";

export function AppFrame({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth(); const router = useRouter();
  async function signOut() { await logout(); router.replace("/login"); }
  const home = user?.role === "ADMIN" ? "/admin/dashboard" : user?.role === "MENTOR" ? "/mentor/dashboard" : "/student/dashboard";
  return <div className="min-h-screen bg-slate-50">
    <header className="border-b border-slate-200 bg-white"><div className="mx-auto flex max-w-7xl items-center justify-between px-5 py-4 sm:px-8">
      <Link href={home} className="flex items-center gap-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-blue-700 text-xs font-bold text-white">IMS</span><span><span className="block font-semibold text-slate-900">Internship Management System</span><span className="block text-xs text-slate-500">{user?.roleLabel}</span></span></Link>
      <div className="flex flex-wrap items-center justify-end gap-3">{user?.role === "ADMIN" && <><Link href="/admin/monthly-progress" className="text-sm font-semibold text-blue-700 hover:text-blue-900">Monthly progress</Link><Link href="/admin/opportunities" className="text-sm font-semibold text-blue-700 hover:text-blue-900">Opportunities</Link><Link href="/admin/student-accounts" className="text-sm font-semibold text-blue-700 hover:text-blue-900">Student accounts</Link><Link href="/admin/mentor-accounts" className="text-sm font-semibold text-blue-700 hover:text-blue-900">Mentor accounts</Link></>}{user?.role === "MENTOR" && <Link href="/mentor/monthly-progress" className="text-sm font-semibold text-blue-700 hover:text-blue-900">Monthly progress</Link>}{user?.role === "STUDENT" && <Link href="/student/monthly-progress" className="text-sm font-semibold text-blue-700 hover:text-blue-900">Monthly progress</Link>}{(user?.role === "ADMIN" || user?.role === "MENTOR") && <><Link href={`/${user.role.toLowerCase()}/reports`} className="text-sm font-semibold text-blue-700 hover:text-blue-900">Reports</Link><Link href={`/${user.role.toLowerCase()}/attendance`} className="text-sm font-semibold text-blue-700 hover:text-blue-900">Attendance</Link></>}<button data-button-variant="outline" onClick={signOut} className="text-sm">Sign out</button></div>
    </div></header>
    {children}
  </div>;
}
