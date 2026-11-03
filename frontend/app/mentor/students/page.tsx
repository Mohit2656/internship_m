"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import { StudentAttendanceSummary, formatAttendancePercentage } from "@/components/AttendanceAbsenceOverview";

type SortField = "uid" | "name" | "branch" | "company" | "attendance";
type SortDirection = "asc" | "desc";

export default function MentorAssignedStudentsPage() {
  const [students, setStudents] = useState<StudentAttendanceSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Search & Filter state
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedBranch, setSelectedBranch] = useState("ALL");
  const [attendanceFilter, setAttendanceFilter] = useState<"ALL" | "LOW" | "NORMAL">("ALL");

  // Sorting state
  const [sortField, setSortField] = useState<SortField>("uid");
  const [sortDirection, setSortDirection] = useState<SortDirection>("asc");

  // Pagination state
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(15);

  useEffect(() => {
    api
      .get<StudentAttendanceSummary[]>("/mentor/attendance/students")
      .then((res) => {
        setStudents(res.data);
      })
      .catch(() => setError("Unable to load assigned students."))
      .finally(() => setLoading(false));
  }, []);

  const branchOptions = useMemo(() => {
    const branches = new Set<string>();
    students.forEach((s) => {
      if (s.branch && s.branch.trim()) {
        branches.add(s.branch.trim());
      }
    });
    return Array.from(branches).sort();
  }, [students]);

  const filteredStudents = useMemo(() => {
    return students.filter((s) => {
      // Search filter
      if (searchTerm.trim()) {
        const query = searchTerm.toLowerCase();
        const matchesName = (s.studentName || "").toLowerCase().includes(query);
        const matchesUid = (s.uid || "").toLowerCase().includes(query);
        const matchesBranch = (s.branch || "").toLowerCase().includes(query);
        const matchesCompany = (s.company || "").toLowerCase().includes(query);
        if (!matchesName && !matchesUid && !matchesBranch && !matchesCompany) {
          return false;
        }
      }

      // Branch filter
      if (selectedBranch !== "ALL" && s.branch !== selectedBranch) {
        return false;
      }

      // Attendance filter
      const pct = s.attendance?.percentage;
      if (attendanceFilter === "LOW") {
        if (pct === null || pct === undefined || pct >= 75) return false;
      } else if (attendanceFilter === "NORMAL") {
        if (pct === null || pct === undefined || pct < 75) return false;
      }

      return true;
    });
  }, [students, searchTerm, selectedBranch, attendanceFilter]);

  const sortedStudents = useMemo(() => {
    const list = [...filteredStudents];
    list.sort((left, right) => {
      let comparison = 0;
      switch (sortField) {
        case "uid":
          if (!left.uid) return right.uid ? 1 : left.studentUserId - right.studentUserId;
          if (!right.uid) return -1;
          comparison = left.uid.localeCompare(right.uid, undefined, { numeric: true, sensitivity: "base" });
          break;
        case "name":
          comparison = (left.studentName || "").localeCompare(right.studentName || "", undefined, { sensitivity: "base" });
          break;
        case "branch":
          comparison = (left.branch || "").localeCompare(right.branch || "", undefined, { sensitivity: "base" });
          break;
        case "company":
          comparison = (left.company || "").localeCompare(right.company || "", undefined, { sensitivity: "base" });
          break;
        case "attendance":
          comparison = (left.attendance?.percentage ?? 0) - (right.attendance?.percentage ?? 0);
          break;
      }
      return sortDirection === "asc" ? comparison : -comparison;
    });
    return list;
  }, [filteredStudents, sortField, sortDirection]);

  // Pagination calculation
  const totalItems = sortedStudents.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const validCurrentPage = Math.min(currentPage, totalPages);
  const startIndex = (validCurrentPage - 1) * pageSize;
  const paginatedStudents = sortedStudents.slice(startIndex, startIndex + pageSize);

  function handleSort(field: SortField) {
    if (sortField === field) {
      setSortDirection((prev) => (prev === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDirection("asc");
    }
    setCurrentPage(1);
  }

  function renderSortIndicator(field: SortField) {
    if (sortField !== field) return <span className="ml-1 text-slate-300">↕</span>;
    return <span className="ml-1 text-blue-700">{sortDirection === "asc" ? "↑" : "↓"}</span>;
  }

  return (
    <ProtectedRoute role="MENTOR">
      <AppFrame>
        <main className="mx-auto max-w-7xl px-5 py-10 sm:px-8">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="text-sm font-semibold text-blue-700">Mentor workspace</p>
              <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950">Assigned Students</h1>
              <p className="mt-1 text-sm text-slate-600">
                Supervise and review internship details, workplaces, and attendance progress for your assigned students.
              </p>
            </div>
            {!loading && (
              <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm shadow-sm">
                <span className="text-slate-500">Total assigned:</span>
                <span className="font-bold text-slate-900">{students.length}</span>
              </div>
            )}
          </div>

          {error ? (
            <p className="mt-8 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>
          ) : loading ? (
            <p className="mt-8 text-slate-500">Loading assigned students…</p>
          ) : (
            <section id="assigned-students" className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              {/* Search & Filter Bar */}
              <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-5">
                <div className="flex flex-1 flex-wrap items-center gap-3 min-w-[280px]">
                  <div className="relative flex-1 min-w-[200px]">
                    <input
                      type="search"
                      value={searchTerm}
                      onChange={(e) => {
                        setSearchTerm(e.target.value);
                        setCurrentPage(1);
                      }}
                      placeholder="Search name, UID, branch, company…"
                      className="w-full rounded-xl border border-slate-300 px-3.5 py-2 text-sm focus:border-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-600"
                    />
                  </div>

                  {branchOptions.length > 0 && (
                    <select
                      value={selectedBranch}
                      onChange={(e) => {
                        setSelectedBranch(e.target.value);
                        setCurrentPage(1);
                      }}
                      className="rounded-xl border border-slate-300 px-3 py-2 text-sm text-slate-700 focus:border-blue-600 focus:outline-none"
                    >
                      <option value="ALL">All Branches ({branchOptions.length})</option>
                      {branchOptions.map((b) => (
                        <option key={b} value={b}>
                          {b}
                        </option>
                      ))}
                    </select>
                  )}

                  <select
                    value={attendanceFilter}
                    onChange={(e) => {
                      setAttendanceFilter(e.target.value as any);
                      setCurrentPage(1);
                    }}
                    className="rounded-xl border border-slate-300 px-3 py-2 text-sm text-slate-700 focus:border-blue-600 focus:outline-none"
                  >
                    <option value="ALL">All Attendance</option>
                    <option value="LOW">Attendance &lt; 75%</option>
                    <option value="NORMAL">Attendance ≥ 75%</option>
                  </select>

                  {(searchTerm || selectedBranch !== "ALL" || attendanceFilter !== "ALL") && (
                    <button
                      type="button"
                      data-button-variant="text"
                      onClick={() => {
                        setSearchTerm("");
                        setSelectedBranch("ALL");
                        setAttendanceFilter("ALL");
                        setCurrentPage(1);
                      }}
                      className="text-xs font-semibold text-blue-700 hover:underline"
                    >
                      Reset filters
                    </button>
                  )}
                </div>

                <div className="flex items-center gap-2 text-xs text-slate-500">
                  <span>Show</span>
                  <select
                    value={pageSize}
                    onChange={(e) => {
                      setPageSize(Number(e.target.value));
                      setCurrentPage(1);
                    }}
                    className="rounded-lg border border-slate-200 px-2 py-1 text-xs"
                  >
                    <option value={10}>10</option>
                    <option value={15}>15</option>
                    <option value={25}>25</option>
                    <option value={50}>50</option>
                  </select>
                  <span>per page</span>
                </div>
              </div>

              {/* Table */}
              <div className="mt-4 overflow-x-auto">
                <table className="w-full min-w-[720px] text-left text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-xs font-semibold uppercase tracking-wider text-slate-600">
                      <th
                        className="cursor-pointer p-3.5 hover:text-blue-700 select-none"
                        onClick={() => handleSort("uid")}
                      >
                        UID {renderSortIndicator("uid")}
                      </th>
                      <th
                        className="cursor-pointer p-3.5 hover:text-blue-700 select-none"
                        onClick={() => handleSort("name")}
                      >
                        Student Name {renderSortIndicator("name")}
                      </th>
                      <th
                        className="cursor-pointer p-3.5 hover:text-blue-700 select-none"
                        onClick={() => handleSort("branch")}
                      >
                        Branch {renderSortIndicator("branch")}
                      </th>
                      <th
                        className="cursor-pointer p-3.5 hover:text-blue-700 select-none"
                        onClick={() => handleSort("company")}
                      >
                        Company {renderSortIndicator("company")}
                      </th>
                      <th
                        className="cursor-pointer p-3.5 hover:text-blue-700 select-none"
                        onClick={() => handleSort("attendance")}
                      >
                        Attendance % {renderSortIndicator("attendance")}
                      </th>
                      <th className="p-3.5 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {paginatedStudents.map((student) => {
                      const isLowAttendance = (student.attendance?.percentage ?? 0) < 75;
                      return (
                        <tr
                          key={student.studentUserId}
                          className="border-b border-slate-100 transition-colors hover:bg-slate-50/70"
                        >
                          <td className="p-3.5 font-medium text-slate-900">{student.uid || "—"}</td>
                          <td className="p-3.5 font-semibold">
                            <Link
                              href={`/mentor/students/${student.studentUserId}`}
                              className="text-slate-950 hover:text-blue-700 hover:underline"
                            >
                              {student.studentName || "Name unavailable"}
                            </Link>
                          </td>
                          <td className="p-3.5 text-slate-600">{student.branch || "—"}</td>
                          <td className="p-3.5 text-slate-600">{student.company || "—"}</td>
                          <td className="p-3.5">
                            <span
                              className={`font-semibold ${
                                isLowAttendance ? "text-amber-700" : "text-blue-700"
                              }`}
                            >
                              {formatAttendancePercentage(student.attendance.percentage)}
                            </span>
                            {isLowAttendance && (
                              <span className="ml-2 inline-block rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-800 border border-amber-200">
                                Low
                              </span>
                            )}
                          </td>
                          <td className="p-3.5 text-right">
                            <Link
                              href={`/mentor/students/${student.studentUserId}`}
                              className="ui-button text-xs"
                            >
                              View Profile
                            </Link>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>

                {paginatedStudents.length === 0 && (
                  <div className="py-12 text-center text-sm text-slate-500">
                    {searchTerm || selectedBranch !== "ALL" || attendanceFilter !== "ALL"
                      ? "No students match your search or filter criteria."
                      : "No students are currently assigned to you."}
                  </div>
                )}
              </div>

              {/* Pagination controls */}
              {totalPages > 1 && (
                <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4 text-xs text-slate-600">
                  <p>
                    Showing <span className="font-semibold text-slate-900">{startIndex + 1}</span> to{" "}
                    <span className="font-semibold text-slate-900">
                      {Math.min(startIndex + pageSize, totalItems)}
                    </span>{" "}
                    of <span className="font-semibold text-slate-900">{totalItems}</span> students
                  </p>
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      data-button-variant="outline"
                      disabled={validCurrentPage <= 1}
                      onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                      className="px-2.5 py-1 text-xs disabled:opacity-40"
                    >
                      Previous
                    </button>
                    <span className="px-2 font-medium">
                      Page {validCurrentPage} of {totalPages}
                    </span>
                    <button
                      type="button"
                      data-button-variant="outline"
                      disabled={validCurrentPage >= totalPages}
                      onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                      className="px-2.5 py-1 text-xs disabled:opacity-40"
                    >
                      Next
                    </button>
                  </div>
                </div>
              )}
            </section>
          )}
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
