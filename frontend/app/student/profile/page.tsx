"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProfileDetails } from "@/components/ProfileDetails";
import { ProfileForm } from "@/components/ProfileForm";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { ProfilePrefill, StudentProfile } from "@/lib/student-profile";

function ProfileContent() {
  const params = useSearchParams(); const [profile, setProfile] = useState<StudentProfile | null>(null); const [prefill, setPrefill] = useState<ProfilePrefill | null>(null); const [loading, setLoading] = useState(true); const [editing, setEditing] = useState(params.get("edit") === "true");
  useEffect(() => { Promise.all([api.get<StudentProfile>("/student/profile").catch(() => null), api.get<ProfilePrefill>("/student/profile/prefill")]).then(([current, institutional]) => { setProfile(current?.data ?? null); setPrefill(institutional.data); }).finally(() => setLoading(false)); }, []);
  if (loading) return <p className="py-20 text-center text-sm text-slate-500">Loading your profile…</p>;
  return <main className="mx-auto max-w-6xl px-5 py-10 sm:px-8">
    <Link href="/student/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link>
    <div className="mt-5 flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-semibold text-blue-700">Student profile</p><h1 className="mt-2 text-3xl font-bold tracking-tight">{profile ? editing ? "Edit Your Profile" : "Your Student Profile" : "Complete Your Student Profile"}</h1><p className="mt-2 text-slate-600">Institutional information used by the III Cell.</p></div>{profile && !editing && <button onClick={() => setEditing(true)} className="text-sm">Edit Profile</button>}</div>
    <div className="mt-8">{profile && !editing ? <ProfileDetails profile={profile}/> : <ProfileForm existing={profile ?? undefined} institutional={prefill ?? undefined} onCancel={profile ? () => setEditing(false) : undefined} onSaved={saved => { setProfile(saved); setEditing(false); }}/>}</div>
  </main>;
}
export default function StudentProfilePage() { return <ProtectedRoute role="STUDENT"><AppFrame><Suspense fallback={<p className="py-20 text-center text-sm text-slate-500">Loading…</p>}><ProfileContent/></Suspense></AppFrame></ProtectedRoute>; }
