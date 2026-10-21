"use client";

import Link from "next/link";
import Image from "next/image";
import { FormEvent, Suspense, useEffect, useState } from "react";
import { AxiosError } from "axios";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { api, ApiError, AuthUser } from "@/lib/api";
import { useAuth } from "@/lib/auth";

const loginOptions = [
  { role: "STUDENT", label: "Student Login", icon: "student" },
  { role: "ADMIN", label: "IIIC Incharge Login", icon: "admin" },
  { role: "MENTOR", label: "Faculty Mentor Login", icon: "mentor" },
] as const;

function RoleIcon({ type }: { type: (typeof loginOptions)[number]["icon"] }) {
  if (type === "student") return <svg viewBox="0 0 32 32" fill="none" aria-hidden="true"><path d="m3 11 13-6 13 6-13 6-13-6Z"/><path d="M8.5 14v7.5c4.5 3 10.5 3 15 0V14M28.5 11v9"/><path d="M10 27h12"/></svg>;
  if (type === "admin") return <svg viewBox="0 0 32 32" fill="none" aria-hidden="true"><circle cx="16" cy="9" r="4.5"/><circle cx="6.5" cy="12.5" r="3.5"/><circle cx="25.5" cy="12.5" r="3.5"/><path d="M8.5 26v-2.5a7.5 7.5 0 0 1 15 0V26H8.5Z"/><path d="M1.5 26v-2a5 5 0 0 1 6-4.9M30.5 26v-2a5 5 0 0 0-6-4.9"/></svg>;
  return <svg viewBox="0 0 32 32" fill="none" aria-hidden="true"><circle cx="16" cy="8" r="5"/><path d="M7.5 27v-5a8.5 8.5 0 0 1 17 0v5H7.5Z"/><path d="m12.5 17 3.5 4 3.5-4M16 21v6M7.5 22H4v5h5M24.5 22H28v5h-5"/></svg>;
}

export default function LoginPage() {
  return <Suspense><LoginContent /></Suspense>;
}

function LoginContent() {
  const landing = usePathname() === "/";
  const selectedRole = useSearchParams().get("role");
  const selectedOption = loginOptions.find(option => option.role === selectedRole);
  const router = useRouter();
  const { user, loading, refresh } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const destination = (account: AuthUser) => account.role === "ADMIN" ? "/admin/dashboard" : account.role === "MENTOR" ? account.mustChangePassword ? "/mentor/change-password" : "/mentor/dashboard" : account.mustChangePassword ? "/student/change-password" : "/student/dashboard";

  useEffect(() => {
    if (!loading && user) router.replace(destination(user));
    else if (!loading && !landing && !selectedOption) router.replace("/");
  }, [loading, user, router, landing, selectedOption]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!selectedOption || submitting) return;
    setError(""); setSubmitting(true);
    try {
      const { data } = await api.post<AuthUser>("/auth/login", { email, password, role: selectedOption.role });
      await refresh(); router.replace(destination(data));
    } catch (cause) {
      const response = (cause as AxiosError<ApiError>).response;
      setError(response?.status === 403 ? "This account cannot sign in here. Check that it is active and choose the correct login option." : response?.data?.message ?? "Unable to sign in. Please try again.");
    } finally { setSubmitting(false); }
  }

  if (user) return <main className="grid min-h-screen place-items-center"><p className="text-sm text-slate-500">Checking your session…</p></main>;

  return <main className="flex min-h-[100svh] flex-col overflow-hidden bg-[#f4f8ff]">
    <header className="login-header relative z-20 border-b border-blue-100 bg-white px-3 shadow-[0_8px_30px_rgba(15,48,94,0.06)] sm:px-8">
      <div className="mx-auto flex h-[96px] max-w-[1440px] items-center justify-between gap-4 lg:h-[120px]">
        <div className="flex min-w-0 items-center gap-3 sm:gap-5">
          <Image src="/branding/college-logo.png" alt="St. Vincent Pallotti College official seal" width={150} height={150} priority className="h-14 w-14 shrink-0 rounded-full object-contain sm:h-[90px] sm:w-[90px]" />
          <Image src="/branding/college-wordmark.png" alt="St. Vincent Pallotti College of Engineering and Technology, Nagpur — An Autonomous Institution" width={650} height={128} priority className="h-auto min-w-0 flex-1 object-contain object-left sm:max-w-[560px]" />
        </div>
        <div className="hidden items-center gap-8 lg:flex"><span className="h-12 w-px bg-blue-200"/><p className="whitespace-nowrap text-sm font-semibold uppercase tracking-[0.32em] text-[#405b82]">Arise &amp; Shine</p></div>
      </div>
    </header>

    <div className="grid flex-1 lg:min-h-0 lg:grid-cols-[51.5%_48.5%]">
      <section className="relative isolate flex min-h-[430px] overflow-hidden bg-[#0b2a52] px-7 py-12 text-white sm:px-12 lg:min-h-0 lg:px-[clamp(3rem,5.6vw,5.5rem)] lg:py-[clamp(2.5rem,6vh,4.5rem)]">
        <div className="absolute inset-0 -z-20 bg-[radial-gradient(circle_at_16%_8%,rgba(77,148,245,0.48),transparent_36%),linear-gradient(135deg,#082750_0%,#0b3b76_54%,#2463ac_100%)]" />
        <div className="absolute -left-36 -top-36 -z-10 h-80 w-80 rounded-full border border-white/20 bg-white/[0.04]" />
        <div className="absolute -bottom-52 -right-24 -z-10 h-[430px] w-[430px] rounded-full border border-white/15 bg-white/[0.04]" />
        <div className="absolute right-[-8%] top-1/2 -z-10 h-[min(66vw,620px)] w-[min(66vw,620px)] -translate-y-1/2 overflow-hidden rounded-full border border-white/10 opacity-[0.11]">
          <Image src="/branding/college-logo.png" alt="" aria-hidden="true" fill sizes="620px" className="scale-110 object-cover grayscale" />
        </div>
        <div className="absolute inset-x-7 top-12 h-px bg-gradient-to-r from-white/55 via-white/15 to-transparent sm:inset-x-12 lg:inset-x-[clamp(3rem,6vw,6.5rem)]" />

        <div className="flex w-full flex-col justify-between">
          <div className="max-w-3xl pt-4 lg:pt-[clamp(1rem,4vh,3rem)]">
            <div className="mb-5 h-1 w-14 rounded-full bg-[#ffc85b]" />
            <p className="text-xs font-semibold uppercase tracking-[0.34em] text-blue-100 sm:text-sm">Department of</p>
            <p className="mt-2 font-[Georgia,serif] text-[clamp(1.35rem,2.25vw,2rem)] font-semibold leading-tight text-white">Computer Science and Engineering</p>
            <div className="mt-6 h-1 w-16 rounded-full bg-[#ffc85b]" />
            <h1 className="mt-7 max-w-[700px] font-[Georgia,serif] text-[clamp(2.75rem,5.1vw,4.75rem)] font-bold leading-[0.98] tracking-[-0.035em] text-[#ffcd67] drop-shadow-[0_3px_12px_rgba(0,0,0,0.18)]">Internship<br />Management System</h1>
            <p className="mt-8 text-sm font-medium tracking-[0.11em] text-blue-50 sm:text-base">Learn&nbsp; <span className="text-[#ffc85b]">•</span> &nbsp;Intern&nbsp; <span className="text-[#ffc85b]">•</span> &nbsp;Grow&nbsp; <span className="text-[#ffc85b]">•</span> &nbsp;Build the Future</p>
          </div>
          <div className="mt-12 flex items-center gap-4 text-sm text-blue-100/80 lg:mt-8">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl border border-white/15 bg-white/[0.06]" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" className="h-6 w-6" stroke="currentColor" strokeWidth="1.6"><path d="m3 9 9-4 9 4-9 4-9-4Z"/><path d="M7 11.2v4.3c2.8 2 7.2 2 10 0v-4.3M21 9v6"/></svg></span>
            <span className="h-9 w-px bg-white/25" />
            <p>A platform for opportunities,<br />progress and possibilities.</p>
          </div>
        </div>
      </section>

      <section className="login-panel relative flex items-center justify-center overflow-hidden px-5 py-12 sm:px-10 lg:py-8">
        <div className="absolute -right-40 -top-52 h-[430px] w-[430px] rounded-full bg-blue-100/55 blur-sm" />
        <div className="absolute -bottom-48 left-12 h-[380px] w-[380px] rounded-full bg-white/80" />
        <div className="relative w-full max-w-[630px] rounded-[1.75rem] border border-white/90 bg-white/85 p-7 shadow-[0_28px_80px_rgba(41,91,160,0.15)] backdrop-blur-xl sm:p-10 lg:p-[clamp(2.25rem,4vw,3.75rem)]">
          <p className="text-base font-bold text-[#0870e6] sm:text-lg">Welcome back</p>
          <div className="mt-4 h-1 w-16 rounded-full bg-[#f5b844]" />
          <h2 className="mt-7 text-3xl font-bold tracking-tight text-slate-950 sm:text-[2.65rem]">{landing ? "Choose your login" : selectedOption?.label ?? "Sign in to your account"}</h2>
          <p className="mt-3 text-sm leading-6 text-slate-600 sm:text-base">{landing ? "Select your role to sign in to your account." : "Use the email and password provided by your institution."}</p>

          {landing ? <nav aria-label="Login options" className="mt-8 space-y-4">
            {loginOptions.map(option => <Link key={option.role} href={`/login?role=${option.role}`} className="login-role-button ui-button group w-full">
              <span className="login-role-icon"><RoleIcon type={option.icon} /></span>
              <span className="login-role-divider" />
              <span className="login-role-label">{option.label}</span>
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" className="login-role-chevron"><path d="m9 5 7 7-7 7" /></svg>
            </Link>)}
          </nav> : <form onSubmit={submit} className="mt-8 space-y-5">
            <label className="block"><span className="mb-2 block text-sm font-semibold text-slate-900">Email address</span><span className="relative block"><span className="pointer-events-none absolute inset-y-0 left-4 grid place-items-center text-slate-500" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" className="h-5 w-5" stroke="currentColor" strokeWidth="1.8"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m4 7 8 6 8-6"/></svg></span><input autoComplete="email" type="email" required value={email} onChange={event => setEmail(event.target.value)} className="w-full rounded-xl border border-slate-300/90 bg-blue-50/45 py-3.5 pl-12 pr-4 outline-none transition placeholder:text-slate-400 focus:border-blue-600 focus:bg-white focus:ring-4 focus:ring-blue-100" placeholder="you@institution.edu" /></span></label>
            <label className="block"><span className="mb-2 block text-sm font-semibold text-slate-900">Password</span><span className="relative block"><span className="pointer-events-none absolute inset-y-0 left-4 grid place-items-center text-slate-500" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" className="h-5 w-5" stroke="currentColor" strokeWidth="1.8"><rect x="5" y="10" width="14" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v2"/></svg></span><input autoComplete="current-password" type={showPassword ? "text" : "password"} required minLength={1} maxLength={128} value={password} onChange={event => setPassword(event.target.value)} className="w-full rounded-xl border border-slate-300/90 bg-blue-50/45 py-3.5 pl-12 pr-12 outline-none transition placeholder:text-slate-400 focus:border-blue-600 focus:bg-white focus:ring-4 focus:ring-blue-100" placeholder="Enter your password" /><button data-button-variant="icon" type="button" onClick={() => setShowPassword(value => !value)} className="absolute inset-y-0 right-3 grid w-9 place-items-center" aria-label={showPassword ? "Hide password" : "Show password"}><svg viewBox="0 0 24 24" fill="none" className="h-5 w-5" stroke="currentColor" strokeWidth="1.8"><path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z"/><circle cx="12" cy="12" r="2.5"/>{showPassword && <path d="m4 4 16 16"/>}</svg></button></span></label>
            <Link href="/" className="block text-sm font-semibold text-blue-700">Choose a different login option</Link>
            <Link href="/forgot-password" className="block text-sm font-semibold text-blue-700">Forgot Password?</Link>
            {error && <p role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
            <button type="submit" disabled={submitting} className="w-full">{submitting ? "Signing in…" : "Sign in"}</button>
          </form>}
          <div className="mt-9 flex items-center gap-4 text-center text-xs leading-5 text-slate-600"><span className="h-px flex-1 bg-blue-200"/><p>Accounts are created and managed by the III Cell.</p><span className="h-px flex-1 bg-blue-200"/></div>
        </div>
      </section>
    </div>
  </main>;
}
