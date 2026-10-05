"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { StatusBadge } from "@/components/StatusBadge";
import { WorkflowBadge } from "@/components/WorkflowBadge";
import { api } from "@/lib/api";
import { Paged, StudentSummary } from "@/lib/student-profile";

type Option = { value: string; label: string };
const emptyFilters = { search: "", branch: "", academicYear: "", semester: "", internshipStatus: "", internshipSource: "", profileStatus: "", companyType: "", internshipMode: "", paid: "", fullTimeEmploymentOffered: "", attention: "" };

export default function StudentsPage() {
  return <Suspense fallback={<main className="mx-auto max-w-7xl px-5 py-10 text-slate-500 sm:px-8">Loading student directory…</main>}><StudentDirectory /></Suspense>;
}

function StudentDirectory() {
  const searchParams = useSearchParams();
  const [result, setResult] = useState<Paged<StudentSummary> | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  const [branches, setBranches] = useState<Option[]>([]); const [filters, setFilters] = useState(() => ({ ...emptyFilters, attention: searchParams.get("attention") ?? "" })); const [applied, setApplied] = useState(() => ({ ...emptyFilters, attention: searchParams.get("attention") ?? "" }));
  const load = useCallback(async (page = 0) => { setLoading(true); setError(""); try { const { data } = await api.get<Paged<StudentSummary>>("/admin/students", { params: { ...applied, page, size: 20 } }); setResult(data); } catch { setError("Unable to load the student directory."); } finally { setLoading(false); } }, [applied]);
  useEffect(() => { void load(0); }, [load]);
  useEffect(() => { api.get<{branches: Option[]}>("/admin/students/options").then(response => setBranches(response.data.branches)); }, []);
  function submit(event: FormEvent) { event.preventDefault(); setApplied({ ...filters }); }
  return <ProtectedRoute role="ADMIN"><AppFrame><main className="mx-auto max-w-7xl px-5 py-10 sm:px-8">
    <Link href="/admin/dashboard" className="text-sm font-semibold text-blue-700">← Back to dashboard</Link><div className="mt-5"><p className="text-sm font-semibold text-blue-700">III Cell directory</p><h1 className="mt-2 text-3xl font-bold tracking-tight">Students</h1><p className="mt-2 text-slate-600">Search student profiles and monitor internship onboarding progress.</p></div>
    <form onSubmit={submit} className="mt-8 grid gap-3 rounded-2xl border border-slate-200 bg-white p-5 md:grid-cols-3 lg:grid-cols-6">
      <input aria-label="Search students" placeholder="Name, UID, roll or email" value={filters.search} onChange={event => setFilters({...filters, search: event.target.value})} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm md:col-span-2"/>
      <select aria-label="Branch" value={filters.branch} onChange={event => setFilters({...filters, branch: event.target.value})} className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm"><option value="">All branches</option>{branches.map(branch => <option key={branch.value} value={branch.value}>{branch.label}</option>)}</select>
      <input aria-label="Academic year" placeholder="Academic year" value={filters.academicYear} onChange={event => setFilters({...filters, academicYear: event.target.value})} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm"/>
      <select aria-label="Semester" value={filters.semester} onChange={event => setFilters({...filters, semester: event.target.value})} className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm"><option value="">All semesters</option>{[1,2,3,4,5,6,7,8].map(semester => <option key={semester}>{semester}</option>)}</select>
      <select aria-label="Internship status" value={filters.internshipStatus} onChange={event => setFilters({...filters, internshipStatus: event.target.value})} className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm"><option value="">All internship statuses</option><option value="NOT_SECURED">Not Secured</option><option value="INTERNSHIP_SECURED">Internship Secured</option><option value="DOCUMENTS_PENDING">Documents Pending</option><option value="DOCUMENTS_COMPLETE">Documents Complete</option><option value="DIARY_ISSUED">Diary Issued</option><option value="INTERNSHIP_DETAILS_COMPLETE">Internship Details Complete</option></select>
      <select aria-label="Internship source" value={filters.internshipSource} onChange={event => setFilters({...filters, internshipSource: event.target.value})} className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm"><option value="">All sources</option><option value="DEPARTMENT">Department</option><option value="CDC">CDC</option><option value="SELF">Self</option></select>
      <select aria-label="Profile status" value={filters.profileStatus} onChange={event => setFilters({...filters, profileStatus: event.target.value})} className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm"><option value="">All profile statuses</option><option value="COMPLETE">Profile complete</option><option value="INCOMPLETE">Profile incomplete</option></select>
      <select aria-label="Company type" value={filters.companyType} onChange={event => setFilters({...filters, companyType: event.target.value})} className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm"><option value="">All company types</option><option value="IT">IT</option><option value="MANUFACTURING">Manufacturing</option><option value="CORE">Core</option><option value="OTHER">Other</option></select>
      <select aria-label="Internship mode" value={filters.internshipMode} onChange={event => setFilters({...filters, internshipMode: event.target.value})} className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm"><option value="">All modes</option><option value="OFFICE_REPORTING">Office Reporting</option><option value="HYBRID">Hybrid</option><option value="ONLINE">Online</option><option value="COLLEGE_REPORTING">College Reporting</option></select>
      <select aria-label="Paid status" value={filters.paid} onChange={event => setFilters({...filters, paid: event.target.value})} className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm"><option value="">Paid or unpaid</option><option value="true">Paid</option><option value="false">Unpaid</option></select>
      <select aria-label="Full-time employment opportunity" value={filters.fullTimeEmploymentOffered} onChange={event => setFilters({...filters, fullTimeEmploymentOffered: event.target.value})} className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm"><option value="">Full-time opportunity</option><option value="true">Yes</option><option value="false">No</option></select>
      <button className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-semibold text-white">Apply Filters</button>{filters.attention && <button type="button" onClick={() => { setFilters(emptyFilters); setApplied(emptyFilters); }} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-semibold text-slate-700">Clear attention filter</button>}
    </form>
    {error && <p className="mt-5 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}
    <div className="mt-5 overflow-hidden rounded-2xl border border-slate-200 bg-white"><div className="overflow-x-auto"><table className="w-full min-w-[1100px] text-left text-sm"><thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr>{["Student","UID / Roll","Branch","Academic","Profile","Internship","Source","Documents","Diary"].map(header => <th key={header} className="px-4 py-3 font-bold">{header}</th>)}</tr></thead><tbody className="divide-y divide-slate-100">{loading ? <tr><td colSpan={9} className="px-4 py-12 text-center text-slate-500">Loading students…</td></tr> : result?.content.length ? result.content.map(student => <tr key={student.id} className="hover:bg-slate-50">
        <td className="px-4 py-4"><Link href={`/admin/students/${student.id}`} className="font-semibold text-blue-700">{student.studentName ?? "Profile not completed"}</Link><span className="block text-xs text-slate-500">{student.loginEmail}</span></td>
        <td className="px-4 py-4"><span className="block">{student.uid ?? "—"}</span><span className="text-xs text-slate-500">{student.rollNumber ?? "—"}</span></td><td className="px-4 py-4">{student.branchLabel ?? "—"}</td><td className="px-4 py-4"><span className="block">{student.academicYear ?? "—"}</span><span className="text-xs text-slate-500">Semester {student.semester ?? "—"}</span></td><td className="px-4 py-4"><StatusBadge status={student.profileStatus}/></td><td className="px-4 py-4"><WorkflowBadge status={student.internshipStatus}/></td><td className="px-4 py-4">{student.internshipSourceLabel ?? "—"}</td><td className="px-4 py-4 font-semibold">{student.documentsCompleted} / {student.documentsRequired}</td><td className="px-4 py-4">{student.diaryIssued ? <span className="font-semibold text-emerald-700">Issued</span> : "Not issued"}</td>
      </tr>) : <tr><td colSpan={9} className="px-4 py-12 text-center text-slate-500">No students match these filters.</td></tr>}</tbody></table></div>
      {result && result.totalPages > 1 && <div className="flex items-center justify-between border-t border-slate-200 px-5 py-4 text-sm"><span className="text-slate-500">Page {result.page + 1} of {result.totalPages} · {result.totalElements} students</span><div className="flex gap-2"><button disabled={result.first} onClick={() => load(result.page - 1)} className="rounded-lg border px-3 py-2 disabled:opacity-40">Previous</button><button disabled={result.last} onClick={() => load(result.page + 1)} className="rounded-lg border px-3 py-2 disabled:opacity-40">Next</button></div></div>}
    </div>
  </main></AppFrame></ProtectedRoute>;
}
