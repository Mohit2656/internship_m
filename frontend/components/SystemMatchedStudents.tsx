"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { InterestedStudents, SystemMatchedStudents as MatchResponse } from "@/lib/opportunities";

export function SystemMatchedStudents({ opportunityId }: { opportunityId: string }) {
  const [data, setData] = useState<MatchResponse | null>(null);
  const [interested, setInterested] = useState<InterestedStudents | null>(null);
  const [tab, setTab] = useState<"MATCHED" | "INTERESTED">("MATCHED");
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      api.get<MatchResponse>(`/admin/opportunities/${opportunityId}/system-matched-students`, { signal: controller.signal }),
      api.get<InterestedStudents>(`/admin/opportunities/${opportunityId}/interested-students`, { signal: controller.signal }),
    ]).then(([matches, interests]) => { setData(matches.data); setInterested(interests.data); })
      .catch(() => { if (!controller.signal.aborted) setError("System-matched students could not be loaded."); });
    return () => controller.abort();
  }, [opportunityId]);

  return <section className="mt-8 rounded-2xl border border-blue-200 bg-white p-6">
    <h2 className="text-xl font-bold">Opportunity Students</h2>
    <div role="tablist" aria-label="Opportunity students" className="mt-4 inline-flex rounded-xl bg-slate-100 p-1">
      <button type="button" role="tab" aria-selected={tab === "MATCHED"} onClick={() => setTab("MATCHED")} className={`px-3 py-2 text-xs font-bold ${tab === "MATCHED" ? "bg-white text-blue-700 shadow-sm" : "bg-transparent text-slate-600"}`}>System-Matched Students</button>
      <button type="button" role="tab" aria-selected={tab === "INTERESTED"} onClick={() => setTab("INTERESTED")} className={`px-3 py-2 text-xs font-bold ${tab === "INTERESTED" ? "bg-white text-blue-700 shadow-sm" : "bg-transparent text-slate-600"}`}>Interested Students</button>
    </div>
    {tab === "MATCHED" ? <>
    <p className="mt-3 text-sm text-slate-600">Students shown here match at least 50% of the required skills. They are not applicants.</p>
    {error ? <p role="alert" className="mt-4 text-sm text-red-700">{error}</p>
      : !data ? <p role="status" className="mt-4 text-sm text-slate-500">Loading skill matches…</p>
      : !data.available ? <p className="mt-4 rounded-xl bg-amber-50 px-4 py-3 text-sm font-semibold text-amber-900">{data.message || "Skill match unavailable"}</p>
      : data.students.length === 0 ? <p className="mt-4 text-sm text-slate-500">No students currently meet the 50% required-skill threshold.</p>
      : <div className="mt-5 overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm"><thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="p-3">Student</th><th className="p-3">Matched skills</th><th className="p-3">Missing required skills</th><th className="p-3">Match</th></tr></thead><tbody>{data.students.map(student => <tr key={student.studentId} className="border-b border-slate-100 align-top"><td className="p-3"><Link href={`/admin/students/${student.studentId}`} className="font-semibold text-blue-700 hover:underline">{student.studentName}</Link><p className="mt-1 text-xs text-slate-500">UID {student.uid}</p></td><td className="p-3">{student.matchedSkills.join(", ")}</td><td className="p-3">{student.missingRequiredSkills.join(", ") || "None"}</td><td className="p-3 font-bold">{student.matchPercentage.toFixed(1)}%</td></tr>)}</tbody></table></div>}
    </> : <>
      <p className="mt-3 text-sm text-slate-600">Students who expressed interest are listed independently of skill match. They are not applicants.</p>
      {!interested ? <p role="status" className="mt-4 text-sm text-slate-500">Loading interested students…</p>
        : interested.students.length === 0 ? <p className="mt-4 text-sm text-slate-500">No students have expressed interest yet.</p>
        : <div className="mt-5 overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="p-3">Student</th><th className="p-3">Match</th></tr></thead><tbody>{interested.students.map(student => <tr key={student.studentId} className="border-b border-slate-100"><td className="p-3"><Link href={`/admin/students/${student.studentId}`} className="font-semibold text-blue-700 hover:underline">{student.studentName}</Link><p className="mt-1 text-xs text-slate-500">{student.uid ? `UID ${student.uid}` : "UID unavailable"}</p></td><td className="p-3 font-bold">{student.matchPercentage == null ? "Skill match unavailable" : `${student.matchPercentage.toFixed(1)}%`}</td></tr>)}</tbody></table></div>}
    </>}
  </section>;
}
