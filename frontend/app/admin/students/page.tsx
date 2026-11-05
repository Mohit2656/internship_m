"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { StudentAttendanceHistory, StudentAttendanceSummary, formatAttendancePercentage } from "@/components/AttendanceAbsenceOverview";
import { DeleteStudentButton } from "@/components/DeleteStudentButton";
import { ResetPasswordButton } from "@/components/ResetPasswordButton";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { WorkflowBadge } from "@/components/WorkflowBadge";
import { api } from "@/lib/api";
import { Paged, StudentSummary } from "@/lib/student-profile";
import { StudentDeletionResult } from "@/lib/student-deletion";

type Option = { value: string; label: string };

const emptyFilters = {
  academicSessionId: "",
  search: "",
  branch: "",
  academicYear: "",
  semester: "",
  internshipStatus: "",
  internshipSource: "",
  profileStatus: "",
  companyType: "",
  internshipMode: "",
  paid: "",
  fullTimeEmploymentOffered: "",
  attention: "",
};

function StudentRowActions({
  student,
  onViewAttendance,
  onDeleted,
}: {
  student: StudentSummary;
  onViewAttendance: () => void;
  onDeleted: (result: StudentDeletionResult) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", handleClick);
    document.addEventListener("keydown", handleKey);
    return () => {
      document.removeEventListener("mousedown", handleClick);
      document.removeEventListener("keydown", handleKey);
    };
  }, [open]);

  return (
    <div className="relative inline-flex items-center gap-1.5" ref={menuRef}>
      <button
        type="button"
        aria-label={`Actions for ${student.studentName || student.loginEmail}`}
        aria-expanded={open}
        onClick={() => setOpen((prev) => !prev)}
        className="compact-btn border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
      >
        Actions <span aria-hidden="true" className="text-[10px]">▾</span>
      </button>

      {open && (
        <div
          role="menu"
          className="absolute right-0 top-full z-30 mt-1 w-44 rounded-xl border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-700 dark:bg-slate-800"
        >
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              onViewAttendance();
            }}
            className="dropdown-item-btn"
          >
            View Attendance
          </button>
          <div onClick={() => setOpen(false)}>
            <ResetPasswordButton
              userId={student.id}
              name={student.studentName ?? student.loginEmail}
              email={student.loginEmail}
              className="dropdown-item-btn"
            />
          </div>
          <div className="my-1 border-t border-slate-100 dark:border-slate-700" />
          <div onClick={() => setOpen(false)}>
            <DeleteStudentButton
              student={student}
              onDeleted={onDeleted}
              className="dropdown-item-btn text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/40"
            />
          </div>
        </div>
      )}
    </div>
  );
}

function StudentDirectory() {
  const searchParams = useSearchParams();
  const [attendance, setAttendance] = useState<Record<number, StudentAttendanceSummary>>({});
  const [attendanceError, setAttendanceError] = useState("");
  const [notice, setNotice] = useState("");
  const [selected, setSelected] = useState<StudentSummary | null>(null);

  useEffect(() => {
    api
      .get<StudentAttendanceSummary[]>("/admin/attendance/students")
      .then(({ data }) => setAttendance(Object.fromEntries(data.map((row) => [row.studentUserId, row]))))
      .catch(() => setAttendanceError("Attendance summaries could not be loaded."));
  }, []);

  const [result, setResult] = useState<Paged<StudentSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [branches, setBranches] = useState<Option[]>([]);
  const [pageSize, setPageSize] = useState(20);
  const [showMoreFilters, setShowMoreFilters] = useState(false);

  const [filters, setFilters] = useState(() => ({
    ...emptyFilters,
    academicSessionId: searchParams.get("academicSessionId") ?? "",
    attention: searchParams.get("attention") ?? "",
  }));

  const [applied, setApplied] = useState(() => ({
    ...emptyFilters,
    academicSessionId: searchParams.get("academicSessionId") ?? "",
    attention: searchParams.get("attention") ?? "",
  }));

  const load = useCallback(
    async (page = 0, size = pageSize) => {
      setLoading(true);
      setError("");
      try {
        const { data } = await api.get<Paged<StudentSummary>>("/admin/students", {
          params: { ...applied, page, size },
        });
        setResult(data);
      } catch {
        setError("Unable to load the student directory.");
      } finally {
        setLoading(false);
      }
    },
    [applied, pageSize]
  );

  useEffect(() => {
    void load(0, pageSize);
  }, [load, pageSize]);

  useEffect(() => {
    api.get<{ branches: Option[] }>("/admin/students/options").then((response) => setBranches(response.data.branches));
  }, []);

  function submit(event: FormEvent) {
    event.preventDefault();
    setApplied({ ...filters });
  }

  function handleResetFilters() {
    setFilters(emptyFilters);
    setApplied(emptyFilters);
  }

  const moreFiltersCount = useMemo(() => {
    let count = 0;
    if (filters.semester) count++;
    if (filters.internshipSource) count++;
    if (filters.profileStatus) count++;
    if (filters.companyType) count++;
    if (filters.internshipMode) count++;
    if (filters.paid) count++;
    if (filters.fullTimeEmploymentOffered) count++;
    return count;
  }, [filters]);

  const hasActiveFilters = Boolean(
    filters.search ||
      filters.branch ||
      filters.internshipStatus ||
      filters.academicYear ||
      moreFiltersCount > 0 ||
      filters.attention
  );

  return (
    <ProtectedRoute role="ADMIN">
      <AppFrame>
        <main className="mx-auto max-w-7xl px-5 py-10 sm:px-8">
          <Link href="/admin/dashboard" className="text-sm font-semibold text-blue-700 hover:underline">
            ← Back to dashboard
          </Link>

          {/* Heading area matching Mentor Assigned Students reference */}
          <div className="mt-4 flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="text-sm font-semibold text-blue-700">III Cell workspace</p>
              <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950 dark:text-slate-100">
                Student Directory
              </h1>
              <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
                Search student profiles, review academic records, and monitor internship onboarding progress.
              </p>
            </div>
            {!loading && result && (
              <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm shadow-sm dark:border-slate-800 dark:bg-slate-900">
                <span className="text-slate-500 dark:text-slate-400">Total students:</span>
                <span className="font-bold text-slate-900 dark:text-slate-100">{result.totalElements}</span>
              </div>
            )}
          </div>

          {notice && (
            <p role="status" className="mt-5 rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-400">
              {notice}
            </p>
          )}
          {error && (
            <p className="mt-5 rounded-xl bg-red-50 p-4 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-400">
              {error}
            </p>
          )}
          {attendanceError && (
            <p role="alert" className="mt-4 text-red-700 dark:text-red-400">
              {attendanceError}
            </p>
          )}

          {/* Expanded student attendance history modal */}
          {selected && (
            <div className="mt-6">
              <StudentAttendanceHistory
                key={selected.id}
                role="ADMIN"
                studentId={selected.id}
                name={selected.studentName || selected.uid || "Student"}
                onBack={() => setSelected(null)}
              />
            </div>
          )}

          {/* Table Container styled exactly like Mentor Assigned Students */}
          <section id="student-directory" className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            {/* Search and Filters Bar */}
            <form onSubmit={submit} className="border-b border-slate-100 pb-5 dark:border-slate-800">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex flex-1 flex-wrap items-center gap-2.5 min-w-[280px]">
                  {/* Search Input */}
                  <div className="relative flex-1 min-w-[200px]">
                    <input
                      type="search"
                      aria-label="Search students"
                      placeholder="Search name, UID, roll or email…"
                      value={filters.search}
                      onChange={(event) => setFilters({ ...filters, search: event.target.value })}
                      className="w-full rounded-xl border border-slate-300 px-3.5 py-2 text-sm focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
                    />
                  </div>

                  {/* Branch Filter */}
                  <select
                    aria-label="Branch"
                    value={filters.branch}
                    onChange={(event) => setFilters({ ...filters, branch: event.target.value })}
                    className="rounded-xl border border-slate-300 px-3 py-2 text-sm text-slate-700 focus:border-blue-600 focus:outline-none dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                  >
                    <option value="">All branches ({branches.length})</option>
                    {branches.map((b) => (
                      <option key={b.value} value={b.value}>
                        {b.label}
                      </option>
                    ))}
                  </select>

                  {/* Internship Status Filter */}
                  <select
                    aria-label="Internship status"
                    value={filters.internshipStatus}
                    onChange={(event) => setFilters({ ...filters, internshipStatus: event.target.value })}
                    className="rounded-xl border border-slate-300 px-3 py-2 text-sm text-slate-700 focus:border-blue-600 focus:outline-none dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                  >
                    <option value="">All internship statuses</option>
                    <option value="NOT_SECURED">Not Secured</option>
                    <option value="INTERNSHIP_SECURED">Internship Secured</option>
                    <option value="DOCUMENTS_PENDING">Documents Pending</option>
                    <option value="DOCUMENTS_COMPLETE">Documents Complete</option>
                    <option value="DIARY_ISSUED">Diary Issued</option>
                    <option value="INTERNSHIP_DETAILS_COMPLETE">Internship Details Complete</option>
                  </select>

                  {/* Academic Year Filter */}
                  <input
                    aria-label="Academic year"
                    placeholder="Academic year"
                    value={filters.academicYear}
                    onChange={(event) => setFilters({ ...filters, academicYear: event.target.value })}
                    className="w-36 rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-blue-600 focus:outline-none dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
                  />

                  {/* More Filters Toggle */}
                  <button
                    type="button"
                    onClick={() => setShowMoreFilters((prev) => !prev)}
                    className="flex items-center gap-1.5 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-750"
                  >
                    <span>More Filters</span>
                    <span className="text-[10px]">{showMoreFilters ? "▲" : "▼"}</span>
                    {moreFiltersCount > 0 && (
                      <span className="rounded-full bg-blue-100 px-1.5 py-0.2 text-[10px] font-bold text-blue-700 dark:bg-blue-900/50 dark:text-blue-300">
                        {moreFiltersCount}
                      </span>
                    )}
                  </button>

                  {/* Action buttons */}
                  <button type="submit" className="ui-button text-xs font-semibold px-4 py-2">
                    Apply Filters
                  </button>

                  {hasActiveFilters && (
                    <button
                      type="button"
                      data-button-variant="text"
                      onClick={handleResetFilters}
                      className="text-xs font-semibold text-blue-700 hover:underline dark:text-blue-400"
                    >
                      Reset filters
                    </button>
                  )}
                </div>

                {/* Page Size Selector */}
                <div className="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
                  <span>Show</span>
                  <select
                    value={pageSize}
                    onChange={(e) => {
                      const newSize = Number(e.target.value);
                      setPageSize(newSize);
                    }}
                    className="rounded-lg border border-slate-200 px-2 py-1 text-xs dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                  >
                    <option value={10}>10</option>
                    <option value={20}>20</option>
                    <option value={50}>50</option>
                  </select>
                  <span>per page</span>
                </div>
              </div>

              {/* Expandable More Filters Section */}
              {showMoreFilters && (
                <div className="mt-4 grid gap-3 rounded-xl border border-slate-200 bg-slate-50/70 p-4 sm:grid-cols-2 md:grid-cols-4 dark:border-slate-800 dark:bg-slate-850">
                  <div>
                    <label className="block text-[11px] font-semibold text-slate-600 dark:text-slate-400">Semester</label>
                    <select
                      aria-label="Semester"
                      value={filters.semester}
                      onChange={(event) => setFilters({ ...filters, semester: event.target.value })}
                      className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-1.5 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                    >
                      <option value="">All semesters</option>
                      {[1, 2, 3, 4, 5, 6, 7, 8].map((s) => (
                        <option key={s} value={String(s)}>
                          Semester {s}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-[11px] font-semibold text-slate-600 dark:text-slate-400">Internship source</label>
                    <select
                      aria-label="Internship source"
                      value={filters.internshipSource}
                      onChange={(event) => setFilters({ ...filters, internshipSource: event.target.value })}
                      className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-1.5 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                    >
                      <option value="">All sources</option>
                      <option value="DEPARTMENT">Department</option>
                      <option value="CDC">CDC</option>
                      <option value="SELF">Self</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-[11px] font-semibold text-slate-600 dark:text-slate-400">Profile status</label>
                    <select
                      aria-label="Profile status"
                      value={filters.profileStatus}
                      onChange={(event) => setFilters({ ...filters, profileStatus: event.target.value })}
                      className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-1.5 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                    >
                      <option value="">All profile statuses</option>
                      <option value="COMPLETE">Profile complete</option>
                      <option value="INCOMPLETE">Profile incomplete</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-[11px] font-semibold text-slate-600 dark:text-slate-400">Company type</label>
                    <select
                      aria-label="Company type"
                      value={filters.companyType}
                      onChange={(event) => setFilters({ ...filters, companyType: event.target.value })}
                      className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-1.5 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                    >
                      <option value="">All company types</option>
                      <option value="IT">IT</option>
                      <option value="MANUFACTURING">Manufacturing</option>
                      <option value="CORE">Core</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-[11px] font-semibold text-slate-600 dark:text-slate-400">Internship mode</label>
                    <select
                      aria-label="Internship mode"
                      value={filters.internshipMode}
                      onChange={(event) => setFilters({ ...filters, internshipMode: event.target.value })}
                      className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-1.5 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                    >
                      <option value="">All modes</option>
                      <option value="OFFICE_REPORTING">Office Reporting</option>
                      <option value="HYBRID">Hybrid</option>
                      <option value="ONLINE">Online</option>
                      <option value="COLLEGE_REPORTING">College Reporting</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-[11px] font-semibold text-slate-600 dark:text-slate-400">Paid status</label>
                    <select
                      aria-label="Paid status"
                      value={filters.paid}
                      onChange={(event) => setFilters({ ...filters, paid: event.target.value })}
                      className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-1.5 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                    >
                      <option value="">Paid or unpaid</option>
                      <option value="true">Paid</option>
                      <option value="false">Unpaid</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-[11px] font-semibold text-slate-600 dark:text-slate-400">Full-time opportunity</label>
                    <select
                      aria-label="Full-time employment opportunity"
                      value={filters.fullTimeEmploymentOffered}
                      onChange={(event) => setFilters({ ...filters, fullTimeEmploymentOffered: event.target.value })}
                      className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-1.5 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                    >
                      <option value="">Full-time opportunity</option>
                      <option value="true">Yes</option>
                      <option value="false">No</option>
                    </select>
                  </div>
                </div>
              )}
            </form>

            {/* Redesigned Clean Main Table */}
            <div className="mt-4 overflow-x-auto min-h-[300px]">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-xs font-semibold uppercase tracking-wider text-slate-600 dark:border-slate-800 dark:text-slate-400">
                    <th className="p-3">UID</th>
                    <th className="p-3">Student Name</th>
                    <th className="p-3">Branch</th>
                    <th className="p-3">Company</th>
                    <th className="p-3">Attendance %</th>
                    <th className="p-3">Internship Status</th>
                    <th className="p-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {loading ? (
                    <tr>
                      <td colSpan={7} className="py-12 text-center text-slate-500 dark:text-slate-400">
                        Loading students…
                      </td>
                    </tr>
                  ) : result?.content.length ? (
                    result.content.map((student) => {
                      const attSummary = attendance[student.id];
                      const pct = attSummary?.attendance.percentage ?? null;
                      const isLowAttendance = pct !== null && pct < 75;

                      return (
                        <tr
                          key={student.id}
                          className="border-b border-slate-100 transition-colors hover:bg-slate-50/70 dark:border-slate-800 dark:hover:bg-slate-800/50"
                        >
                          <td className="p-3 font-medium text-slate-900 dark:text-slate-200">
                            {student.uid || "—"}
                          </td>
                          <td className="p-3 font-semibold">
                            <Link
                              href={`/admin/students/${student.id}`}
                              className="text-slate-950 hover:text-blue-700 hover:underline dark:text-slate-100 dark:hover:text-blue-400"
                            >
                              {student.studentName || student.loginEmail || "Name unavailable"}
                            </Link>
                          </td>
                          <td className="p-3 text-slate-600 dark:text-slate-400">
                            {student.branchLabel || "—"}
                          </td>
                          <td className="p-3 text-slate-600 dark:text-slate-400">
                            {attSummary?.company || "—"}
                          </td>
                          <td className="p-3">
                            <button
                              type="button"
                              onClick={() => setSelected(student)}
                              className="attendance-pct-btn"
                              title="Click to view student attendance history"
                            >
                              <span
                                className={
                                  isLowAttendance
                                    ? "font-semibold text-amber-700 dark:text-amber-400"
                                    : "font-semibold text-slate-900 dark:text-slate-100"
                                }
                              >
                                {formatAttendancePercentage(pct)}
                              </span>
                              {isLowAttendance && (
                                <span className="inline-block rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-800 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800">
                                  Low
                                </span>
                              )}
                            </button>
                          </td>
                          <td className="p-3">
                            <WorkflowBadge status={student.internshipStatus} />
                          </td>
                          <td className="p-3 text-right">
                            <StudentRowActions
                              student={student}
                              onViewAttendance={() => setSelected(student)}
                              onDeleted={async () => {
                                setNotice(`${student.studentName ?? student.loginEmail} was deactivated. Historical records have been preserved.`);
                                if (selected?.id === student.id) setSelected(null);
                                setAttendance((previous) => {
                                  const next = { ...previous };
                                  delete next[student.id];
                                  return next;
                                });
                                await load(result.content.length === 1 && result.page > 0 ? result.page - 1 : result.page);
                              }}
                            />
                          </td>
                        </tr>
                      );
                    })
                  ) : (
                    <tr>
                      <td colSpan={7} className="py-12 text-center text-slate-500 dark:text-slate-400">
                        No students match these filters.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls matching Mentor Assigned Students */}
            {result && result.totalPages > 1 && (
              <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4 text-xs text-slate-600 dark:border-slate-800 dark:text-slate-400">
                <p>
                  Showing <span className="font-semibold text-slate-900 dark:text-slate-100">{result.page * pageSize + 1}</span> to{" "}
                  <span className="font-semibold text-slate-900 dark:text-slate-100">
                    {Math.min((result.page + 1) * pageSize, result.totalElements)}
                  </span>{" "}
                  of <span className="font-semibold text-slate-900 dark:text-slate-100">{result.totalElements}</span> students
                </p>
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    data-button-variant="outline"
                    disabled={result.first || loading}
                    onClick={() => load(result.page - 1, pageSize)}
                    className="px-2.5 py-1 text-xs disabled:opacity-40"
                  >
                    Previous
                  </button>
                  <span className="px-2 font-medium">
                    Page {result.page + 1} of {result.totalPages}
                  </span>
                  <button
                    type="button"
                    data-button-variant="outline"
                    disabled={result.last || loading}
                    onClick={() => load(result.page + 1, pageSize)}
                    className="px-2.5 py-1 text-xs disabled:opacity-40"
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </section>
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}

export default function StudentsPage() {
  return (
    <Suspense fallback={<main className="mx-auto max-w-7xl px-5 py-10 text-slate-500 sm:px-8">Loading student directory…</main>}>
      <StudentDirectory />
    </Suspense>
  );
}
