import Link from "next/link";
export default function ForgotPasswordPage() {
  return <main className="grid min-h-screen place-items-center bg-slate-50 px-5 py-10"><section className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 shadow-sm"><h1 className="text-2xl font-bold">Forgot your password?</h1><p className="mt-4 leading-7 text-slate-600">Contact your system administrator to request a password reset.</p><p className="mt-3 text-sm leading-6 text-slate-600">After receiving a temporary password securely from your administrator, sign in and create a new password.</p><Link href="/login" className="ui-button mt-6 inline-block">Return to login</Link></section></main>;
}
