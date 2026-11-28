"use client";

import { useState } from "react";
import { InternshipDetails } from "@/lib/internship-details";
import { LastStudentUpdate } from "@/components/LastStudentUpdate";
import { api } from "@/lib/api";
import { LoadingSpinner } from "@/components/LoadingSpinner";

export function InternshipDetailsView({
  details,
  isAdmin = false,
  onUpdated,
}: {
  details: InternshipDetails;
  isAdmin?: boolean;
  onUpdated?: () => void;
}) {
  const [actionType, setActionType] = useState<"terminate" | "delete" | null>(null);
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const companyType = details.companyType === "OTHER"
    ? details.otherCompanyType ?? "Other"
    : (details.companyTypeLabel || details.companyType || "Not specified");

  const isTerminated = details.lifecycleStatus === "TERMINATED_EARLY" || Boolean(details.terminatedAt);
  const isDeleted = Boolean(details.deletedAt);

  async function handleAdminAction() {
    if (!actionType) return;
    if (reason.trim().length < 5) {
      setError("Please provide a reason of at least 5 characters.");
      return;
    }
    setSubmitting(true);
    setError("");
    setSuccess("");
    try {
      if (actionType === "terminate") {
        await api.post(`/admin/internships/${details.id}/terminate`, { reason: reason.trim() });
        setSuccess("Internship successfully terminated early. Future obligations and attendance halted.");
      } else if (actionType === "delete") {
        await api.delete(`/admin/internships/${details.id}`, { params: { reason: reason.trim() } });
        setSuccess("Internship marked as deleted. Historical records preserved.");
      }
      setActionType(null);
      setReason("");
      if (onUpdated) onUpdated();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg || "Failed to execute administrative action. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">
              Internship Details (Slot {details.internshipNumber ?? 1})
            </p>
            {isDeleted && (
              <span className="rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-bold text-red-700">
                Deleted
              </span>
            )}
            {!isDeleted && isTerminated && (
              <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-bold text-amber-800">
                Terminated Early
              </span>
            )}
          </div>
          <h2 className="mt-2 text-xl font-bold">{details.companyName || "Untitled Internship"}</h2>
        </div>

        {isAdmin && !isDeleted && (
          <div className="flex flex-wrap items-center gap-2">
            {!isTerminated && (
              <button
                type="button"
                onClick={() => { setActionType("terminate"); setReason(""); setError(""); }}
                className="rounded-xl border border-amber-300 bg-amber-50 px-3 py-1.5 text-xs font-semibold text-amber-900 hover:bg-amber-100"
              >
                End Internship Early
              </button>
            )}
            <button
              type="button"
              onClick={() => { setActionType("delete"); setReason(""); setError(""); }}
              className="rounded-xl border border-red-300 bg-red-50 px-3 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-100"
            >
              Delete Internship
            </button>
          </div>
        )}
      </div>

      {isTerminated && (
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs text-amber-900">
          <p className="font-bold">Terminated Early</p>
          <p className="mt-1">Effective Date: {details.terminatedAt ? new Date(details.terminatedAt).toLocaleDateString() : "Recorded"}</p>
          {details.terminationReason && <p className="mt-1">Reason: {details.terminationReason}</p>}
        </div>
      )}

      {isDeleted && (
        <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-4 text-xs text-red-800">
          <p className="font-bold">Administrative Soft Delete</p>
          <p className="mt-1">Deleted Date: {details.deletedAt ? new Date(details.deletedAt).toLocaleDateString() : "Recorded"}</p>
          {details.deletionReason && <p className="mt-1">Reason: {details.deletionReason}</p>}
        </div>
      )}

      {success && <p className="mt-4 rounded-xl bg-emerald-50 p-3 text-xs font-semibold text-emerald-800">{success}</p>}
      {error && <p className="mt-4 rounded-xl bg-red-50 p-3 text-xs font-semibold text-red-700">{error}</p>}

      <LastStudentUpdate value={details.updatedAt ?? details.createdAt} />

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        <Group
          title="Company Information"
          dlClassName="mt-3 grid gap-4 sm:grid-cols-3"
          values={[
            { label: "Company Name", value: details.companyName || "Not specified" },
            { label: "Company Type", value: companyType },
            { label: "Company Phone", value: details.companyPhone || "Not specified" },
            { label: "Company Address", value: details.companyAddress || "Not specified", className: "sm:col-span-3" },
          ]}
        />
        <Group
          title="Industry Supervisor"
          dlClassName="mt-3 grid gap-4 sm:grid-cols-2"
          values={[
            { label: "Supervisor Name", value: details.industrySupervisorName || "Not specified", className: "sm:col-span-2" },
            { label: "Designation", value: details.industrySupervisorDesignation || "Not specified" },
            { label: "Contact Number", value: details.industrySupervisorContact || "Not specified" },
          ]}
        />
        <Group
          title="Internship Duration"
          dlClassName="mt-3 grid grid-cols-2 gap-4"
          values={[
            { label: "Duration", value: details.durationMonths != null ? `${details.durationMonths} months` : "Not specified" },
            { label: "Total Weeks", value: details.totalWeeks != null ? String(details.totalWeeks) : "Not calculated" },
            { label: "Start Date", value: details.startDate || "Not specified" },
            { label: "End Date", value: details.endDate || "Not specified" },
          ]}
        />
        <Group
          title="Internship Arrangement"
          dlClassName="mt-3 grid gap-4 sm:grid-cols-2"
          values={[
            { label: "Mode", value: details.internshipModeLabel || details.internshipMode || "Not specified" },
            {
              label: "Stipend Per Month",
              value: details.stipendPerMonth != null
                ? (Number(details.stipendPerMonth) === 0 ? "Unpaid" : `₹${Number(details.stipendPerMonth).toLocaleString("en-IN")}`)
                : "Not specified",
            },
            {
              label: "Full-Time Employment Opportunity",
              value: details.fullTimeEmploymentOffered === true ? "Yes" : details.fullTimeEmploymentOffered === false ? "No" : "Not specified",
              className: "sm:col-span-2",
            },
          ]}
        />
        <Group
          title="Company Working Schedule"
          className="md:col-span-2"
          dlClassName="mt-3 grid gap-4 sm:grid-cols-[140px_minmax(0,1fr)]"
          values={[
            { label: "Days Per Week", value: `${details.workingDaysPerWeek ?? details.workingDays?.length ?? 5} days` },
            {
              label: "Working Weekdays",
              value: details.workingDays && details.workingDays.length > 0
                ? `${details.workingDays.map(d => d.charAt(0) + d.slice(1).toLowerCase()).join(", ")} (${details.workingDaysPerWeek ?? details.workingDays.length} days/week)`
                : "Monday – Friday (5 days/week) [Default schedule]",
            },
          ]}
        />
      </div>

      {actionType && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4">
          <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl">
            <h3 className="text-lg font-bold text-slate-900">
              {actionType === "terminate" ? "End Internship Early" : "Delete Internship (Soft Delete)"}
            </h3>
            <p className="mt-2 text-xs text-slate-600">
              {actionType === "terminate"
                ? "This will mark the internship as TERMINATED_EARLY. Future attendance, automatic absences, and report obligations will stop immediately."
                : "This will soft-delete the internship from active views. Historical attendance, reports, and logs will be preserved in audit records."}
            </p>

            <label className="mt-4 block">
              <span className="block text-xs font-semibold text-slate-700">Administrative Reason (Required)</span>
              <textarea
                value={reason}
                onChange={e => setReason(e.target.value)}
                placeholder="Enter detailed reason for this action (min 5 characters)..."
                rows={4}
                className="mt-1.5 w-full rounded-xl border border-slate-300 p-3 text-sm focus:border-blue-600 focus:outline-none"
              />
            </label>

            <div className="mt-5 flex justify-end gap-3">
              <button
                type="button"
                data-button-variant="outline"
                disabled={submitting}
                onClick={() => setActionType(null)}
                className="text-sm"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={submitting || reason.trim().length < 5}
                onClick={() => void handleAdminAction()}
                className={`text-sm ${actionType === "terminate" ? "bg-amber-600 hover:bg-amber-700 text-white" : "bg-red-600 hover:bg-red-700 text-white"}`}
              >
                {submitting ? <LoadingSpinner label="Processing…" /> : actionType === "terminate" ? "Confirm Early End" : "Confirm Deletion"}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

type GroupItem = {
  label: string;
  value: string;
  className?: string;
};

function Group({
  title,
  values,
  className = "",
  dlClassName = "mt-3 space-y-3",
}: {
  title: string;
  values: GroupItem[];
  className?: string;
  dlClassName?: string;
}) {
  return (
    <div className={`min-w-0 rounded-xl border border-slate-200/80 bg-slate-50/60 p-4 sm:p-5 ${className}`.trim()}>
      <h3 className="text-sm font-bold text-slate-900">{title}</h3>
      <dl className={dlClassName}>
        {values.map(({ label, value, className: itemClassName }) => (
          <div key={label} className={`min-w-0 ${itemClassName ?? ""}`.trim()}>
            <dt className="text-[11px] font-bold uppercase tracking-wide text-slate-500">{label}</dt>
            <dd className="mt-1 break-words whitespace-pre-wrap text-sm font-medium text-slate-800">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
