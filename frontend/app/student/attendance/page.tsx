"use client";

import axios from "axios";
import Link from "next/link";
import { useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api } from "@/lib/api";
import {
  Attendance,
  attendanceLabel,
  isAutomaticAbsence,
  livenessLabel,
} from "@/lib/attendance";
import { LocationVerificationResult } from "@/components/LocationVerificationResult";
import { AttendanceReviewFeedback } from "@/components/AttendanceReviewPanel";
import { AttendanceLocations } from "@/components/AttendanceLocations";
import { StudentLateAttendanceRequests } from "@/components/StudentLateAttendanceRequests";
import { InternshipDetails } from "@/lib/internship-details";
import {
  getSelectedInternship,
  setSelectedInternship,
  getStatusBadgeClass,
  deriveInternshipStatus,
  InternshipStatusBadge,
} from "@/lib/internship-selection";

function StudentAttendanceContent() {
  const [activeTab, setActiveTab] = useState<1 | 2>(1);
  const [internships, setInternships] = useState<InternshipDetails[]>([]);
  const [records, setRecords] = useState<Attendance[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState<"ALL" | "PRESENT" | "ABSENT" | "PENDING">("ALL");
  const [dateSearch, setDateSearch] = useState("");
  const [attendanceRefresh, setAttendanceRefresh] = useState(0);

  const details1 = internships.find((i) => i.internshipNumber === 1) ?? null;
  const details2 = internships.find((i) => i.internshipNumber === 2) ?? null;

  const status1: InternshipStatusBadge = deriveInternshipStatus(details1);
  const status2: InternshipStatusBadge = deriveInternshipStatus(details2);

  // Load available internships on mount
  useEffect(() => {
    setActiveTab(getSelectedInternship());
    api
      .get<InternshipDetails[]>("/student/internship/all")
      .then(({ data }) => setInternships(data))
      .catch(() => {});
  }, []);

  // Fetch attendance records whenever activeTab or internships list changes
  useEffect(() => {
    const abort = new AbortController();
    setLoading(true);
    setError("");

    const targetInternship = activeTab === 1 ? details1 : details2;

    if (!targetInternship) {
      setRecords([]);
      setLoading(false);
      return;
    }

    api
      .get<Attendance[]>("/student/attendance", {
        params: { internshipId: targetInternship.id },
        signal: abort.signal,
      })
      .then(({ data }) => {
        setRecords(
          [...data].sort((a, b) => b.attendanceDate.localeCompare(a.attendanceDate))
        );
      })
      .catch((cause) => {
        if (!abort.signal.aborted) {
          if (axios.isAxiosError(cause) && (cause.response?.status === 409 || cause.response?.status === 404)) {
            setRecords([]);
          } else {
            setError("Attendance history could not be loaded. Please try again.");
          }
        }
      })
      .finally(() => {
        if (!abort.signal.aborted) setLoading(false);
      });

    return () => abort.abort();
  }, [activeTab, details1?.id, details2?.id, attendanceRefresh]);

  function handleTabChange(tab: 1 | 2) {
    setActiveTab(tab);
    setSelectedInternship(tab);
    setFilter("ALL");
    setDateSearch("");
  }

  const total = records.length;
  const absentCount = records.filter(
    (r) => r.attendanceResult === "ABSENT" || isAutomaticAbsence(r)
  ).length;
  const presentCount = records.filter((r) => r.attendanceResult === "PRESENT").length;
  const pendingCount = records.filter(
    (r) => r.attendanceResult === "PENDING_MENTOR_REVIEW"
  ).length;
  const attendancePercentage =
    total > 0 ? ((presentCount / total) * 100).toFixed(1) : "—";

  const filtered = records.filter((record) => {
    if (filter === "PRESENT" && record.attendanceResult !== "PRESENT") return false;
    if (
      filter === "ABSENT" &&
      record.attendanceResult !== "ABSENT" &&
      !isAutomaticAbsence(record)
    )
      return false;
    if (
      filter === "PENDING" &&
      record.attendanceResult !== "PENDING_MENTOR_REVIEW"
    )
      return false;
    if (dateSearch.trim() && !record.attendanceDate.includes(dateSearch.trim())) {
      return false;
    }
    return true;
  });

  const activeInternship = activeTab === 1 ? details1 : details2;

  return (
      <AppFrame>
        <main className="mx-auto min-w-0 max-w-7xl px-5 py-8 sm:px-8">
          {/* Breadcrumb Navigation */}
          <nav aria-label="Breadcrumb" className="mb-4 text-xs font-semibold text-slate-500">
            <Link href="/student/dashboard" className="text-blue-700 hover:underline">
              Dashboard
            </Link>
            <span className="mx-2">/</span>
            <span className="text-slate-700">Attendance History</span>
          </nav>

          {/* Page Header */}
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="text-sm font-semibold text-blue-700">Daily verification</p>
              <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950">
                Attendance History
              </h1>
              <p className="mt-2 text-sm text-slate-600 max-w-2xl">
                Review your complete daily attendance log, liveness checks, workplace GPS verification,
                and mentor approval history.
              </p>
            </div>
            <Link
              href="/student/dashboard"
              className="ui-button text-xs font-medium"
              data-button-variant="outline"
            >
              ← Back to Dashboard
            </Link>
          </div>

          {/* Internship Tabs */}
          <div className="mt-6 flex border-b border-slate-200">
            <button
              id="tab-attendance-slot-1"
              role="tab"
              aria-selected={activeTab === 1}
              onClick={() => handleTabChange(1)}
              className={`flex items-center gap-2 border-b-2 px-5 py-3 text-sm font-semibold transition-colors ${
                activeTab === 1
                  ? "border-blue-600 text-blue-700"
                  : "border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700"
              }`}
            >
              <span>Internship 1</span>
              {details1 && (
                <span className={`rounded-full border px-2 py-0.5 text-xs font-semibold ${getStatusBadgeClass(status1)}`}>
                  {status1}
                </span>
              )}
            </button>
            <button
              id="tab-attendance-slot-2"
              role="tab"
              aria-selected={activeTab === 2}
              onClick={() => handleTabChange(2)}
              className={`flex items-center gap-2 border-b-2 px-5 py-3 text-sm font-semibold transition-colors ${
                activeTab === 2
                  ? "border-blue-600 text-blue-700"
                  : "border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700"
              }`}
            >
              <span>Internship 2</span>
              <span className={`rounded-full border px-2 py-0.5 text-xs font-semibold ${getStatusBadgeClass(status2)}`}>
                {status2}
              </span>
            </button>
          </div>

          {/* Active Internship Info banner */}
          {activeInternship && (
            <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 p-3 text-xs text-slate-600 border border-slate-200">
              <div>
                <span className="font-semibold text-slate-900">{activeInternship.companyName}</span>
                <span className="mx-2 text-slate-300">•</span>
                <span>{activeInternship.internshipModeLabel}</span>
                <span className="mx-2 text-slate-300">•</span>
                <span>{activeInternship.startDate} to {activeInternship.endDate}</span>
              </div>
              <span className="font-medium text-slate-500">Internship ID: {activeInternship.id}</span>
            </div>
          )}

          <StudentLateAttendanceRequests internship={activeInternship} onAttendanceChanged={() => setAttendanceRefresh(value => value + 1)} />

          {/* Attendance Stats Cards */}
          <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
            <div className="rounded-2xl border border-slate-200 bg-white p-5">
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                Total Days
              </p>
              <p className="mt-2 text-2xl font-bold text-slate-900">{total}</p>
              <p className="mt-1 text-xs text-slate-500">Recorded working days</p>
            </div>
            <div className="rounded-2xl border border-emerald-200/80 bg-emerald-50/40 p-5">
              <p className="text-xs font-semibold uppercase tracking-wider text-emerald-800">
                Present
              </p>
              <p className="mt-2 text-2xl font-bold text-emerald-700">{presentCount}</p>
              <p className="mt-1 text-xs text-emerald-600">Verified attendances</p>
            </div>
            <div className="rounded-2xl border border-red-200/80 bg-red-50/40 p-5">
              <p className="text-xs font-semibold uppercase tracking-wider text-red-800">
                Absent
              </p>
              <p className="mt-2 text-2xl font-bold text-red-700">{absentCount}</p>
              <p className="mt-1 text-xs text-red-600">Unsubmitted or absent</p>
            </div>
            <div className="rounded-2xl border border-blue-200/80 bg-blue-50/40 p-5">
              <p className="text-xs font-semibold uppercase tracking-wider text-blue-800">
                Attendance %
              </p>
              <p className="mt-2 text-2xl font-bold text-blue-700">
                {attendancePercentage === "—" ? "—" : `${attendancePercentage}%`}
              </p>
              <p className="mt-1 text-xs text-blue-600">Cumulative rate</p>
            </div>
          </div>

          {/* Main Attendance Table Section */}
          <section
            id="daily-attendance"
            className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 sm:p-7 shadow-sm"
          >
            {/* Filter Bar */}
            <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-5">
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => setFilter("ALL")}
                  className={`rounded-xl px-3 py-1.5 text-xs font-semibold transition-colors ${
                    filter === "ALL"
                      ? "bg-slate-900 text-white"
                      : "bg-slate-100 text-slate-700 hover:bg-slate-200"
                  }`}
                >
                  All ({total})
                </button>
                <button
                  type="button"
                  onClick={() => setFilter("PRESENT")}
                  className={`rounded-xl px-3 py-1.5 text-xs font-semibold transition-colors ${
                    filter === "PRESENT"
                      ? "bg-emerald-700 text-white"
                      : "bg-emerald-50 text-emerald-800 hover:bg-emerald-100"
                  }`}
                >
                  Present ({presentCount})
                </button>
                <button
                  type="button"
                  onClick={() => setFilter("ABSENT")}
                  className={`rounded-xl px-3 py-1.5 text-xs font-semibold transition-colors ${
                    filter === "ABSENT"
                      ? "bg-red-700 text-white"
                      : "bg-red-50 text-red-800 hover:bg-red-100"
                  }`}
                >
                  Absent ({absentCount})
                </button>
                {pendingCount > 0 && (
                  <button
                    type="button"
                    onClick={() => setFilter("PENDING")}
                    className={`rounded-xl px-3 py-1.5 text-xs font-semibold transition-colors ${
                      filter === "PENDING"
                        ? "bg-amber-600 text-white"
                        : "bg-amber-50 text-amber-800 hover:bg-amber-100"
                    }`}
                  >
                    Pending Review ({pendingCount})
                  </button>
                )}
              </div>

              <div className="flex items-center gap-2">
                <label className="sr-only" htmlFor="date-filter-input">
                  Filter by date
                </label>
                <input
                  id="date-filter-input"
                  type="text"
                  placeholder="Filter by date (YYYY-MM)..."
                  value={dateSearch}
                  onChange={(e) => setDateSearch(e.target.value)}
                  className="rounded-xl border border-slate-300 px-3 py-1.5 text-xs outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100 w-48"
                />
                {dateSearch && (
                  <button
                    type="button"
                    onClick={() => setDateSearch("")}
                    className="text-xs text-slate-500 hover:text-slate-800"
                  >
                    Clear
                  </button>
                )}
              </div>
            </div>

            {/* Error & Loading States */}
            {error && (
              <p role="alert" className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">
                {error}
              </p>
            )}

            {loading ? (
              <p className="mt-6 text-sm text-slate-500">Loading attendance history…</p>
            ) : !activeInternship ? (
              <div className="py-12 text-center">
                <p className="text-sm font-semibold text-slate-700">
                  Internship {activeTab} Details Not Configured
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  Attendance records will become available once Internship {activeTab} details are submitted and approved.
                </p>
                <Link
                  href="/student/internship/details"
                  className="ui-button mt-4 inline-block text-xs"
                >
                  Go to Internship Details
                </Link>
              </div>
            ) : filtered.length === 0 ? (
              <div className="py-12 text-center">
                <p className="text-sm font-semibold text-slate-700">No attendance records found</p>
                <p className="mt-1 text-xs text-slate-500">
                  {dateSearch || filter !== "ALL"
                    ? "Try adjusting your filter or search query."
                    : `Daily records for ${activeInternship.companyName} will appear as your attendance is logged.`}
                </p>
              </div>
            ) : (
              /* Horizontally scrollable attendance table */
              <div className="mt-5 w-full overflow-x-auto">
                <table className="w-full min-w-[800px] text-left text-sm text-slate-900 border-collapse">
                  <thead>
                    <tr className="border-b border-slate-200 text-xs font-bold uppercase tracking-wider text-slate-500">
                      <th className="p-3 whitespace-nowrap">Date</th>
                      <th className="p-3">Status &amp; Verification</th>
                      <th className="p-3 whitespace-nowrap">Recorded By</th>
                      <th className="p-3">Location &amp; Liveness</th>
                      <th className="p-3 whitespace-nowrap">Mentor Approval</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {filtered.map((record) => {
                      const isAbsent =
                        record.attendanceResult === "ABSENT" || isAutomaticAbsence(record);
                      const isPending =
                        record.attendanceResult === "PENDING_MENTOR_REVIEW";

                      return (
                        <tr
                          key={record.id}
                          className="hover:bg-slate-50/50 transition-colors align-top"
                        >
                          <td className="p-3 font-semibold text-slate-900 whitespace-nowrap">
                            {record.attendanceDate}
                          </td>
                          <td className="p-3">
                            <span
                              className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-bold ${
                                isAbsent
                                  ? "bg-red-50 text-red-700"
                                  : isPending
                                  ? "bg-amber-50 text-amber-800"
                                  : "bg-emerald-50 text-emerald-800"
                              }`}
                            >
                              {attendanceLabel(record)}
                            </span>
                            {record.attendanceResult === "PRESENT" && (
                              <div className="mt-1">
                                <LocationVerificationResult evidence={record.evidence} />
                              </div>
                            )}
                            {record.evidence?.dailyWorkSummary && (
                              <p className="mt-2 max-w-sm whitespace-pre-wrap text-xs text-slate-600 italic">
                                &ldquo;{record.evidence.dailyWorkSummary}&rdquo;
                              </p>
                            )}
                          </td>
                          <td className="p-3 text-xs text-slate-600 whitespace-nowrap">
                            {isAutomaticAbsence(record)
                              ? "Automatic — deadline elapsed"
                              : record.submissionType === "LATE_REQUEST"
                              ? "Approved late request — manual decision"
                              : "Student submission"}
                            {record.submittedLate && (
                              <span className="mt-1 block font-semibold text-blue-700">
                                Late submission
                              </span>
                            )}
                          </td>
                          <td className="p-3 text-xs text-slate-600">
                            <span className="font-medium text-slate-700">
                              {livenessLabel(record.livenessStatus)}
                            </span>
                            {record.evidence && (
                              <div className="mt-1">
                                <AttendanceLocations evidence={record.evidence} />
                              </div>
                            )}
                          </td>
                          <td className="p-3 text-xs whitespace-nowrap">
                            <AttendanceReviewFeedback record={record} />
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </main>
      </AppFrame>
  );
}

export default function StudentAttendancePage() {
  return <ProtectedRoute role="STUDENT"><StudentAttendanceContent /></ProtectedRoute>;
}
