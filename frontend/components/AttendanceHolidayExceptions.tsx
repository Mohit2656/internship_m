"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AttendanceException } from "@/lib/attendance";
import { formatNumericUid } from "@/lib/uid-formatter";

export function AttendanceHolidayExceptions({ onChanged }: { onChanged?: () => void }) {
  const [exceptions, setExceptions] = useState<AttendanceException[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [processingId, setProcessingId] = useState<number | null>(null);
  const [remarks, setRemarks] = useState<Record<number, string>>({});

  useEffect(() => {
    loadExceptions();
  }, []);

  function loadExceptions() {
    setLoading(true);
    setError("");
    api.get<AttendanceException[]>("/mentor/attendance/exceptions")
      .then(({ data }) => setExceptions(data))
      .catch(() => setError("Failed to load company holiday exception requests."))
      .finally(() => setLoading(false));
  }

  async function review(id: number, status: "APPROVED" | "REJECTED") {
    setProcessingId(id);
    setActionError("");
    try {
      await api.post(`/mentor/attendance/exceptions/${id}/review`, {
        status,
        remarks: remarks[id] || (status === "APPROVED" ? "Approved by mentor" : "Rejected by mentor")
      });
      loadExceptions();
      onChanged?.();
    } catch (err: unknown) {
      setActionError("Failed to update holiday request.");
    } finally {
      setProcessingId(null);
    }
  }

  const holidayExceptions = exceptions.filter(e => e.exceptionType === "COMPANY_HOLIDAY");

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-bold text-slate-900">Company Holiday Requests</h3>
          <p className="mt-1 text-sm text-slate-500">
            Review and approve student requests for company-specific holidays. Approved holidays are excused and will not count as absences.
          </p>
        </div>
        <button
          type="button"
          data-button-variant="outline"
          onClick={loadExceptions}
          className="text-xs"
        >
          Refresh
        </button>
      </div>

      {actionError && <p role="alert" className="mt-3 text-xs text-red-700">{actionError}</p>}
      {error && <p role="alert" className="mt-4 text-sm text-red-700">{error}</p>}

      {loading ? (
        <p className="mt-6 text-sm text-slate-500">Loading holiday requests…</p>
      ) : holidayExceptions.length === 0 ? (
        <p className="mt-6 text-sm text-slate-500">No company holiday requests found.</p>
      ) : (
        <div className="mt-6 overflow-x-auto">
          <table className="w-full min-w-[700px] text-left text-sm text-slate-900">
            <thead>
              <tr className="border-b border-slate-200 text-xs font-semibold uppercase tracking-wider text-slate-600">
                <th className="p-3">Student</th>
                <th className="p-3">Company</th>
                <th className="p-3">Holiday Date</th>
                <th className="p-3">Reason</th>
                <th className="p-3">Status</th>
                <th className="p-3">Actions / Remarks</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {holidayExceptions.map(item => {
                const isPending = item.status === "PENDING";
                const isProcessing = processingId === item.id;
                return (
                  <tr key={item.id} className="align-top">
                    <td className="p-3 font-medium">
                      <p className="font-semibold text-slate-900">{item.studentName || "Student"}</p>
                      <p className="text-xs text-slate-500">{formatNumericUid(item.uid)}</p>
                    </td>
                    <td className="p-3">
                      <p className="font-medium text-slate-800">{item.companyName || "—"}</p>
                      <span className="text-xs text-slate-500">Internship {item.internshipNumber}</span>
                    </td>
                    <td className="p-3 whitespace-nowrap font-medium text-slate-900">{item.exceptionDate}</td>
                    <td className="p-3 max-w-[240px]">
                      <p className="text-slate-800">{item.reason}</p>
                    </td>
                    <td className="p-3 whitespace-nowrap">
                      <span
                        className={`inline-block rounded-full px-2.5 py-1 text-xs font-semibold ${
                          item.status === "APPROVED"
                            ? "bg-emerald-50 text-emerald-800"
                            : item.status === "REJECTED"
                            ? "bg-rose-50 text-rose-800"
                            : "bg-amber-50 text-amber-800"
                        }`}
                      >
                        {item.status === "APPROVED" ? "Approved" : item.status === "REJECTED" ? "Rejected" : "Pending Review"}
                      </span>
                    </td>
                    <td className="p-3">
                      {isPending ? (
                        <div className="space-y-2">
                          <input
                            type="text"
                            placeholder="Mentor remarks (optional)"
                            value={remarks[item.id] || ""}
                            onChange={e => setRemarks(r => ({ ...r, [item.id]: e.target.value }))}
                            className="w-full rounded-lg border border-slate-300 px-2 py-1 text-xs text-slate-800"
                          />
                          <div className="flex gap-2">
                            <button
                              type="button"
                              disabled={isProcessing}
                              onClick={() => review(item.id, "APPROVED")}
                              className="rounded-lg bg-emerald-600 px-3 py-1 text-xs font-semibold text-white hover:bg-emerald-700"
                            >
                              Approve
                            </button>
                            <button
                              type="button"
                              disabled={isProcessing}
                              onClick={() => review(item.id, "REJECTED")}
                              className="rounded-lg bg-rose-600 px-3 py-1 text-xs font-semibold text-white hover:bg-rose-700"
                            >
                              Reject
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="text-xs text-slate-500">
                          {item.reviewRemarks ? <p className="italic">&ldquo;{item.reviewRemarks}&rdquo;</p> : null}
                          {item.reviewedByName && <p className="mt-1">Reviewed by {item.reviewedByName}</p>}
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
