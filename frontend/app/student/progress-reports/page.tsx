"use client";

import Link from "next/link";
import { FormEvent, Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AxiosError } from "axios";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { WeeklyProgress } from "@/components/WeeklyProgress";
import { ApiError, api } from "@/lib/api";
import {
  formatDate,
  formatDateTime,
  monthlyStatusLabel,
  monthlyStatusClass,
  MonthlyProgressOverview,
  MonthlyProgressPeriod,
  openPrivateFile,
  privateFileErrorMessage,
} from "@/lib/monthly-progress";
import { InternshipDetails } from "@/lib/internship-details";
import {
  getSelectedInternship,
  setSelectedInternship,
  getStatusBadgeClass,
  deriveInternshipStatus,
  InternshipStatusBadge,
} from "@/lib/internship-selection";

function MonthlyProgressView({ internshipId }: { internshipId?: number | null }) {
  const [overview, setOverview] = useState<MonthlyProgressOverview | null>(null);
  const [selected, setSelected] = useState<MonthlyProgressPeriod | null>(null);
  const [description, setDescription] = useState("");
  const [photo, setPhoto] = useState<File | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const maxPhotoBytes = overview?.maxPhotoSizeBytes ?? 20 * 1024 * 1024;
  const maxPhotoMiB = maxPhotoBytes / (1024 * 1024);

  function load() {
    setLoading(true);
    setError("");
    api
      .get<MonthlyProgressOverview>("/student/monthly-progress", {
        params: internshipId ? { internshipId } : undefined,
      })
      .then((response) => setOverview(response.data))
      .catch((cause: AxiosError<ApiError>) => {
        if (cause.response?.status === 409 || cause.response?.status === 404) {
          setOverview(null);
        } else {
          setError(cause.response?.data?.message ?? "Unable to load monthly progress.");
        }
      })
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
  }, [internshipId]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!selected || !photo) {
      setError("Choose a JPG, JPEG or PNG photograph before submitting.");
      return;
    }
    if (submitting) return;
    if (photo.size > maxPhotoBytes) {
      setError(`Photographs must be ${maxPhotoMiB} MiB or smaller.`);
      return;
    }
    setSubmitting(true);
    setError("");
    setNotice("");
    const body = new FormData();
    body.append("workDescription", description);
    body.append("photo", photo);
    try {
      if (selected.report) body.append("revision", String(selected.report.revision));
      const url = selected.report
        ? `/student/monthly-progress/${selected.report.id}/resubmit`
        : `/student/monthly-progress/${selected.monthNumber}`;
      await api.post(url, body, {
        params: internshipId ? { internshipId } : undefined,
        headers: { "Content-Type": "multipart/form-data" },
        timeout: 120_000,
      });
      setNotice(
        "Report uploaded successfully and sent to your faculty mentor for review. It is not yet approved."
      );
      setSelected(null);
      setDescription("");
      setPhoto(null);
      load();
    } catch (cause) {
      setError(
        (cause as AxiosError<ApiError>).response?.data?.message ??
          "The monthly progress report could not be submitted."
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function file(id: number, kind: "photo" | "pdf", uid?: string | null, month?: number) {
    setBusy(`${id}-${kind}`);
    setError("");
    try {
      await openPrivateFile(
        `/student/monthly-progress/${id}/${kind}`,
        kind === "pdf" ? `Monthly_Progress_${uid ?? "Student"}_Month_${month}.pdf` : undefined
      );
    } catch (cause) {
      setError(privateFileErrorMessage(cause, kind));
    } finally {
      setBusy("");
    }
  }

  return (
    <div>
      {notice && (
        <p role="status" className="mt-4 rounded-xl bg-blue-50 p-4 text-sm text-blue-800">
          {notice}
        </p>
      )}
      {error && (
        <p role="alert" className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">
          {error}
        </p>
      )}

      {loading ? (
        <p className="mt-8 text-sm text-slate-500">Loading monthly reporting periods…</p>
      ) : !overview ? (
        <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-8 text-center">
          <p className="text-sm font-semibold text-slate-700">No monthly progress periods available</p>
          <p className="mt-1 text-xs text-slate-500">
            Monthly reporting periods become active once internship details are completed and approved.
          </p>
        </div>
      ) : (
        <>
          <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6">
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">
              Registered Internship
            </p>
            <h3 className="mt-1 text-xl font-bold text-slate-900">{overview.companyName}</h3>
            <p className="mt-2 text-sm text-slate-600">
              {overview.internshipMode} · {formatDate(overview.internshipStart)} –{" "}
              {formatDate(overview.internshipEnd)}
            </p>
          </section>

          <div className="mt-6 space-y-4">
            {overview.periods.map((period) => (
              <article
                id={`month-${period.monthNumber}`}
                key={period.monthNumber}
                className="rounded-2xl border border-slate-200 bg-white p-6"
              >
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <h4 className="text-lg font-bold text-slate-900">Month {period.monthNumber}</h4>
                    <p className="mt-1 text-sm text-slate-600">
                      {formatDate(period.periodStart)} – {formatDate(period.periodEnd)}
                    </p>
                  </div>
                  <span
                    className={`rounded-full px-3 py-1.5 text-xs font-bold ${
                      period.report
                        ? monthlyStatusClass(period.report.status)
                        : period.status === "UPCOMING"
                        ? "bg-slate-100 text-slate-600"
                        : "bg-blue-50 text-blue-700"
                    }`}
                  >
                    {period.report
                      ? monthlyStatusLabel(period.report.status)
                      : period.status === "UPCOMING"
                      ? "Upcoming"
                      : "Available"}
                  </span>
                </div>

                {period.report ? (
                  <div id={`report-${period.report.id}`} className="mt-5">
                    <p className="report-document whitespace-pre-wrap text-sm leading-6 text-slate-700">
                      {period.report.workDescription}
                    </p>
                    {period.report.status === "PENDING_MENTOR_REVIEW" && (
                      <p className="mt-4 text-sm text-amber-800">
                        This report is not yet approved. It is awaiting faculty mentor review.
                      </p>
                    )}
                    {period.report.rejectionReason && (
                      <p className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-700">
                        Rejection reason: {period.report.rejectionReason}
                      </p>
                    )}
                    <dl className="report-document mt-4 grid gap-3 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2">
                      <div>
                        <dt className="text-slate-500">Uploaded</dt>
                        <dd className="mt-1 font-semibold">{formatDateTime(period.report.submittedAt)}</dd>
                      </div>
                      <div>
                        <dt className="text-slate-500">Mentor decision</dt>
                        <dd className="mt-1 font-semibold">
                          {period.report.reviewedAt
                            ? `${period.report.reviewerEmail ?? "Faculty Mentor"} · ${formatDateTime(
                                period.report.reviewedAt
                              )}`
                            : "Awaiting review"}
                        </dd>
                      </div>
                    </dl>
                    <div className="mt-4 flex flex-wrap gap-3">
                      {period.report.status === "REJECTED" && (
                        <button
                          onClick={() => {
                            setSelected(period);
                            setDescription(period.report!.workDescription);
                            setPhoto(null);
                            setNotice("");
                            setError("");
                          }}
                          className="ui-button text-sm"
                        >
                          Correct and Resubmit
                        </button>
                      )}
                      <button
                        data-button-variant="outline"
                        onClick={() => file(period.report!.id, "photo")}
                        className="ui-button text-sm"
                      >
                        {busy === `${period.report.id}-photo` ? "Opening…" : "View Photograph"}
                      </button>
                      <button
                        onClick={() =>
                          file(period.report!.id, "pdf", period.report!.uid, period.monthNumber)
                        }
                        className="ui-button text-sm"
                      >
                        {busy === `${period.report.id}-pdf` ? "Preparing…" : "Download PDF"}
                      </button>
                    </div>
                  </div>
                ) : period.status === "AVAILABLE" ? (
                  <button
                    onClick={() => {
                      setSelected(period);
                      setDescription("");
                      setPhoto(null);
                      setNotice("");
                      setError("");
                    }}
                    className="ui-button mt-5 text-sm"
                  >
                    Submit Report
                  </button>
                ) : (
                  <p className="mt-4 text-sm text-slate-500">
                    Submission becomes available when this reporting period begins.
                  </p>
                )}
              </article>
            ))}
          </div>
        </>
      )}

      {selected && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-950/50 p-4">
          <div className="mx-auto my-8 max-w-xl rounded-2xl bg-white p-6 shadow-2xl sm:p-8">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-bold uppercase tracking-wide text-blue-700">
                  Monthly Progress Report
                </p>
                <h3 className="mt-2 text-2xl font-bold">Draft — Month {selected.monthNumber}</h3>
                <p className="mt-1 text-sm text-slate-600">
                  {formatDate(selected.periodStart)} – {formatDate(selected.periodEnd)}
                </p>
              </div>
              <button
                data-button-variant="text"
                type="button"
                onClick={() => setSelected(null)}
                className="text-sm"
              >
                Close
              </button>
            </div>
            <form onSubmit={submit} className="mt-7 space-y-5">
              {error && (
                <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">
                  {error}
                </p>
              )}
              <label className="block">
                <span className="mb-2 block text-sm font-semibold">Photograph</span>
                <input
                  type="file"
                  required
                  accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                  onChange={(event) => {
                    const chosen = event.target.files?.[0] ?? null;
                    if (
                      chosen &&
                      (chosen.size > maxPhotoBytes || !/\.(jpe?g|png)$/i.test(chosen.name))
                    ) {
                      setPhoto(null);
                      event.target.value = "";
                      setError(
                        `Choose a JPG, JPEG or PNG photograph of ${maxPhotoMiB} MiB or smaller.`
                      );
                    } else {
                      setPhoto(chosen);
                      setError("");
                    }
                  }}
                  className="block w-full rounded-xl border border-slate-300 p-3 text-sm"
                />
                <span className="mt-2 block text-xs text-slate-500">
                  JPG, JPEG or PNG, maximum {maxPhotoMiB} MiB. The server validates image integrity.
                </span>
              </label>
              <label className="block">
                <span className="mb-2 block text-sm font-semibold">
                  Work performed during this month
                </span>
                <textarea
                  required
                  maxLength={3000}
                  rows={7}
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  className="w-full rounded-xl border border-slate-300 p-4 text-sm outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100"
                />
                <span className="mt-1 block text-right text-xs text-slate-500">
                  {description.length}/3000
                </span>
              </label>
              <p className="rounded-xl bg-amber-50 p-3 text-xs leading-5 text-amber-800">
                Submitting sends this draft for faculty mentor review. Approved reports are read-only;
                rejected reports may be corrected and resubmitted.
              </p>
              <button disabled={submitting} className="ui-button w-full">
                {submitting ? "Submitting…" : "Submit Report"}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function ProgressReportsContent() {
  const searchParams = useSearchParams();
  const initialType = searchParams.get("tab") === "monthly" ? "monthly" : "weekly";
  const [reportType, setReportType] = useState<"weekly" | "monthly">(initialType);
  const [internshipTab, setInternshipTab] = useState<1 | 2>(1);
  const [internships, setInternships] = useState<InternshipDetails[]>([]);

  useEffect(() => {
    setInternshipTab(getSelectedInternship());
    api
      .get<InternshipDetails[]>("/student/internship/all")
      .then(({ data }) => setInternships(data))
      .catch(() => {});
  }, []);

  useEffect(() => {
    const tab = searchParams.get("tab");
    if (tab === "monthly" || tab === "weekly") {
      setReportType(tab);
    }
  }, [searchParams]);

  function handleInternshipTabChange(tab: 1 | 2) {
    setInternshipTab(tab);
    setSelectedInternship(tab);
  }

  const details1 = internships.find((i) => i.internshipNumber === 1) ?? null;
  const details2 = internships.find((i) => i.internshipNumber === 2) ?? null;

  const status1: InternshipStatusBadge = deriveInternshipStatus(details1);
  const status2: InternshipStatusBadge = deriveInternshipStatus(details2);

  const activeInternship = internshipTab === 1 ? details1 : details2;

  return (
    <main className="mx-auto max-w-5xl px-5 py-10 sm:px-8">
      <Link href="/student/dashboard" className="text-sm font-semibold text-blue-700">
        ← Back to dashboard
      </Link>
      <div className="mt-5 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-blue-700">Internship records</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Progress Reports</h1>
          <p className="mt-2 text-slate-600">
            Submit and review your weekly and monthly internship progress records.
          </p>
        </div>
      </div>

      {/* Level 1 Navigation: Internship Tabs */}
      {details2 && (
        <div className="mt-8">
          <div className="inline-flex items-center gap-1 rounded-lg bg-slate-100/90 p-1 border border-slate-200/80 shadow-xs" role="tablist" aria-label="Internship selection">
            <button
              type="button"
              role="tab"
              id="tab-internship-1"
              aria-selected={internshipTab === 1}
              onClick={() => handleInternshipTabChange(1)}
              className={`segmented-tab flex items-center gap-2 rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
                internshipTab === 1
                  ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                  : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
              }`}
            >
              <span>Internship 1</span>
              {details1 && (
                <span className={`rounded-full border px-2 py-0.5 text-[10px] font-semibold ${
                  internshipTab === 1 ? "border-blue-200 bg-blue-100 text-blue-800" : getStatusBadgeClass(status1)
                }`}>
                  {status1}
                </span>
              )}
            </button>
            <button
              type="button"
              role="tab"
              id="tab-internship-2"
              aria-selected={internshipTab === 2}
              onClick={() => handleInternshipTabChange(2)}
              className={`segmented-tab flex items-center gap-2 rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
                internshipTab === 2
                  ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                  : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
              }`}
            >
              <span>Internship 2</span>
              <span className={`rounded-full border px-2 py-0.5 text-[10px] font-semibold ${
                internshipTab === 2 ? "border-blue-200 bg-blue-100 text-blue-800" : getStatusBadgeClass(status2)
              }`}>
                {status2}
              </span>
            </button>
          </div>
        </div>
      )}

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

      {/* Level 2 Navigation: Weekly vs Monthly */}
      <div className="mt-6">
        <div className="inline-flex items-center gap-1 rounded-lg bg-slate-100/90 p-1 border border-slate-200/80 shadow-xs" role="tablist" aria-label="Progress report types">
          <button
            type="button"
            role="tab"
            id="tab-weekly"
            aria-selected={reportType === "weekly"}
            aria-controls="panel-weekly"
            onClick={() => setReportType("weekly")}
            className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              reportType === "weekly"
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
            }`}
          >
            Weekly Reports
          </button>
          <button
            type="button"
            role="tab"
            id="tab-monthly"
            aria-selected={reportType === "monthly"}
            aria-controls="panel-monthly"
            onClick={() => setReportType("monthly")}
            className={`segmented-tab rounded-md px-3.5 py-1.5 text-xs sm:text-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              reportType === "monthly"
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent font-medium"
            }`}
          >
            Monthly Reports
          </button>
        </div>

        <div className="mt-6">
          {!activeInternship ? (
            <div className="rounded-2xl border border-slate-200 bg-white p-12 text-center">
              <p className="text-base font-semibold text-slate-800">
                Internship {internshipTab} Details Not Configured
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Progress reports will become available once Internship {internshipTab} details are submitted and approved.
              </p>
              <Link
                href="/student/internship/details"
                className="ui-button mt-4 inline-block text-xs"
              >
                Go to Internship Details
              </Link>
            </div>
          ) : reportType === "weekly" ? (
            <div id="panel-weekly" role="tabpanel" aria-labelledby="tab-weekly">
              <WeeklyProgress key={`weekly-${activeInternship.id}`} scope="student" internshipId={activeInternship.id} />
            </div>
          ) : (
            <div id="panel-monthly" role="tabpanel" aria-labelledby="tab-monthly">
              <MonthlyProgressView key={`monthly-${activeInternship.id}`} internshipId={activeInternship.id} />
            </div>
          )}
        </div>
      </div>
    </main>
  );
}

export default function StudentProgressReportsPage() {
  return (
    <ProtectedRoute role="STUDENT">
      <AppFrame>
        <Suspense fallback={<p className="py-20 text-center text-sm text-slate-500">Loading progress reports…</p>}>
          <ProgressReportsContent />
        </Suspense>
      </AppFrame>
    </ProtectedRoute>
  );
}
