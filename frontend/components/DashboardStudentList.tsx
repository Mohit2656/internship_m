"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { Paged, StudentSummary } from "@/lib/student-profile";

export interface DashboardStudentListProps {
  title: string;
  count: number;
  params: Record<string, string | number | boolean | undefined>;
  sessionId: number | null;
  categoryKey: string;
  onClose: () => void;
}

export function DashboardStudentList({
  title,
  count,
  params,
  sessionId,
  categoryKey,
  onClose,
}: DashboardStudentListProps) {
  const [students, setStudents] = useState<StudentSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(count);

  const fetchStudents = async (targetPage = 0, append = false) => {
    if (append) {
      setLoadingMore(true);
    } else {
      setLoading(true);
      setError("");
      setStudents([]);
    }

    try {
      const queryParams: Record<string, string | number | boolean> = {
        page: targetPage,
        size: 50,
      };
      for (const [k, v] of Object.entries(params)) {
        if (v !== undefined) queryParams[k] = v;
      }
      if (sessionId) {
        queryParams.academicSessionId = sessionId;
      }

      const res = await api.get<Paged<StudentSummary>>("/admin/students", { params: queryParams });
      if (append) {
        setStudents(prev => [...prev, ...res.data.content]);
      } else {
        setStudents(res.data.content);
      }
      setPage(res.data.page);
      setTotalPages(res.data.totalPages);
      setTotalElements(res.data.totalElements);
    } catch {
      setError("Failed to load students for this category.");
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  };

  useEffect(() => {
    fetchStudents(0, false);
  }, [categoryKey, sessionId, JSON.stringify(params)]);

  return (
    <div className="mt-6 border-t border-slate-200 pt-5 dark:border-slate-800">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-base font-bold text-slate-900 sm:text-lg dark:text-slate-100">
          {title} — {totalElements} {totalElements === 1 ? "Student" : "Students"}
        </h3>
        <button
          type="button"
          onClick={onClose}
          className="text-xs font-semibold text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100 underline decoration-slate-300 dark:decoration-slate-600"
        >
          Hide list
        </button>
      </div>

      {loading && (
        <div className="mt-4 flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
          <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-blue-600" />
          <span>Loading students…</span>
        </div>
      )}

      {error && !loading && (
        <div className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-300 flex items-center justify-between">
          <span>{error}</span>
          <button
            type="button"
            onClick={() => fetchStudents(0, false)}
            className="font-semibold underline ml-3"
          >
            Retry
          </button>
        </div>
      )}

      {!loading && !error && students.length === 0 && (
        <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">
          No students found in this category for the selected academic session.
        </p>
      )}

      {!loading && !error && students.length > 0 && (
        <ul className="mt-4 divide-y divide-slate-100 dark:divide-slate-800">
          {students.map((student) => {
            const displayName = student.studentName?.trim() || student.loginEmail || `Student #${student.id}`;
            return (
              <li key={student.id} className="py-2.5 sm:py-3">
                <Link
                  href={`/admin/students/${student.id}?from=dashboard&category=${encodeURIComponent(categoryKey)}${sessionId ? `&sessionId=${sessionId}` : ""}`}
                  className="text-slate-900 hover:text-black dark:text-slate-100 dark:hover:text-white hover:underline text-sm sm:text-base font-medium inline-block transition-colors"
                >
                  {displayName}
                </Link>
              </li>
            );
          })}
        </ul>
      )}

      {!loading && !error && page + 1 < totalPages && (
        <div className="mt-4 pt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
          <span>Showing {students.length} of {totalElements} students</span>
          <button
            type="button"
            onClick={() => fetchStudents(page + 1, true)}
            disabled={loadingMore}
            className="font-semibold text-slate-700 hover:text-black dark:text-slate-300 dark:hover:text-white hover:underline disabled:opacity-50"
          >
            {loadingMore ? "Loading more…" : "Load more students"}
          </button>
        </div>
      )}
    </div>
  );
}
