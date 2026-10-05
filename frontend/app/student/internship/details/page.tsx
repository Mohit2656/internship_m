"use client";

import axios from "axios";
import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { InternshipDetailsForm } from "@/components/InternshipDetailsForm";
import { InternshipDetailsView } from "@/components/InternshipDetailsView";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { ApiError, api } from "@/lib/api";
import { emptyInternshipDetails, InternshipDetails, InternshipDetailsFormData, toFormData } from "@/lib/internship-details";
import { InternshipOnboarding } from "@/lib/internship-onboarding";

export default function InternshipDetailsPage() {
  const [onboarding, setOnboarding] = useState<InternshipOnboarding | null>(null); const [details, setDetails] = useState<InternshipDetails | null>(null);
  const [loading, setLoading] = useState(true); const [editing, setEditing] = useState(false); const [saving, setSaving] = useState(false);
  const [error, setError] = useState(""); const [fieldErrors, setFieldErrors] = useState<Record<string,string>>({});
  useEffect(() => { Promise.all([api.get<InternshipOnboarding>("/student/internship-onboarding").then(response => response.data), api.get<InternshipDetails>("/student/internship").then(response => response.data).catch(cause => { if (axios.isAxiosError(cause) && cause.response?.status === 404) return null; throw cause; })]).then(([workflow, internship]) => { setOnboarding(workflow); setDetails(internship); setEditing(Boolean(internship && window.location.hash === "#edit")); }).catch(() => setError("Internship details could not be loaded.")).finally(() => setLoading(false)); }, []);
  async function submit(data: InternshipDetailsFormData) { setSaving(true); setError(""); setFieldErrors({}); const payload = {...data, otherCompanyType: data.companyType === "OTHER" ? data.otherCompanyType : null, durationMonths: Number(data.durationMonths), totalWeeks: Number(data.totalWeeks), stipendPerMonth: Number(data.stipendPerMonth), fullTimeEmploymentOffered: data.fullTimeEmploymentOffered === "true"}; try { const response = details ? await api.put<InternshipDetails>("/student/internship", payload) : await api.post<InternshipDetails>("/student/internship", payload); setDetails(response.data); setEditing(false); window.history.replaceState(null, "", window.location.pathname); setOnboarding(current => current ? {...current, workflowStatus: "INTERNSHIP_DETAILS_COMPLETE"} : current); } catch (cause) { if (axios.isAxiosError<ApiError>(cause)) { setError(cause.response?.data?.message ?? "Internship details could not be saved."); setFieldErrors(cause.response?.data?.fieldErrors ?? {}); } else setError("Internship details could not be saved."); } finally { setSaving(false); } }
  const allowed = onboarding?.workflowStatus === "DIARY_ISSUED" || onboarding?.workflowStatus === "INTERNSHIP_DETAILS_COMPLETE";
  return <ProtectedRoute role="STUDENT"><AppFrame><main className="mx-auto max-w-5xl px-5 py-10 sm:px-8"><Link href="/student/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link><div className="mt-5"><p className="text-sm font-semibold text-blue-700">Internship registration</p><h1 className="mt-2 text-3xl font-bold tracking-tight">{details ? "Internship Details" : "Complete Internship Details"}</h1><p className="mt-2 text-slate-600">Enter the information recorded in your institutional internship diary.</p></div>
    {error && <p className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}
    {loading ? <p className="py-20 text-center text-sm text-slate-500">Loading internship details…</p> : !allowed ? <section className="mt-8 rounded-2xl border border-amber-200 bg-amber-50 p-8"><h2 className="font-bold text-amber-900">Internship diary required</h2><p className="mt-2 text-sm text-amber-800">Complete Phase 2.2 and confirm receipt of your internship diary before entering internship details.</p><Link href="/student/internship" className="mt-5 inline-block rounded-xl bg-amber-800 px-5 py-3 text-sm font-semibold text-white">Return to Internship Onboarding</Link></section> : details && !editing ? <div className="mt-8"><InternshipDetailsView details={details}/><div className="mt-5 flex gap-3"><button onClick={() => setEditing(true)} className="rounded-xl bg-blue-700 px-5 py-3 text-sm font-semibold text-white">Edit Details</button><Link href="/student/dashboard" className="rounded-xl border border-slate-300 px-5 py-3 text-sm font-semibold">Back to Dashboard</Link></div></div> : <div className="mt-8"><InternshipDetailsForm key={details?.updatedAt ?? "new"} initial={details ? toFormData(details) : emptyInternshipDetails} saving={saving} fieldErrors={fieldErrors} onSubmit={submit} onCancel={details ? () => setEditing(false) : undefined}/></div>}
  </main></AppFrame></ProtectedRoute>;
}
