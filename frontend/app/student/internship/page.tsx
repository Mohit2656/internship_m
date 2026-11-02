"use client";

import axios from "axios";
import Link from "next/link";
import { ChangeEvent, useCallback, useEffect, useState } from "react";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { WorkflowBadge } from "@/components/WorkflowBadge";
import { ApiError, api } from "@/lib/api";
import {
  DocumentType,
  downloadDocument,
  fileSize,
  InternshipOnboarding,
  InternshipSource,
  sourceOptions,
} from "@/lib/internship-onboarding";
import {
  getSelectedInternship,
  setSelectedInternship,
  getStatusBadgeClass,
  InternshipStatusBadge,
} from "@/lib/internship-selection";

type SecondRequest = {
  id: number;
  status: "PENDING" | "APPROVED" | "REJECTED";
  rejectionReason: string | null;
};

export default function InternshipOnboardingPage() {
  const [activeTab, setActiveTab] = useState<1 | 2>(1);
  const [onboarding, setOnboarding] = useState<InternshipOnboarding | null>(null);
  const [secondRequest, setSecondRequest] = useState<SecondRequest | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [diaryConfirmed, setDiaryConfirmed] = useState(false);

  const load = useCallback(async (tabNumber: 1 | 2) => {
    setLoading(true);
    setError("");
    try {
      const [obRes, reqsRes] = await Promise.all([
        api.get<InternshipOnboarding>("/student/internship-onboarding", {
          params: { internshipNumber: tabNumber },
        }),
        api.get<SecondRequest[]>("/student/second-internship-requests").catch(() => ({ data: [] as SecondRequest[] })),
      ]);
      setOnboarding(obRes.data);
      if (reqsRes.data.length > 0) {
        setSecondRequest(reqsRes.data[0]);
      } else {
        setSecondRequest(null);
      }
    } catch {
      setError("Internship onboarding could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const tab = getSelectedInternship();
    setActiveTab(tab);
    void load(tab);
  }, [load]);

  function handleTabChange(tab: 1 | 2) {
    setActiveTab(tab);
    setSelectedInternship(tab);
    void load(tab);
  }

  function message(cause: unknown) {
    return axios.isAxiosError<ApiError>(cause)
      ? cause.response?.data?.message ?? "The request could not be completed."
      : "The request could not be completed.";
  }

  async function secure() {
    setBusy("secure");
    setError("");
    try {
      const res = await api.post<InternshipOnboarding>(
        "/student/internship-onboarding/secure",
        {},
        { params: { internshipNumber: activeTab } }
      );
      setOnboarding(res.data);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy("");
    }
  }

  async function selectSource(source: InternshipSource) {
    setBusy("source");
    setError("");
    try {
      const res = await api.put<InternshipOnboarding>(
        "/student/internship-onboarding/source",
        { source },
        { params: { internshipNumber: activeTab } }
      );
      setOnboarding(res.data);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy("");
    }
  }

  async function upload(type: DocumentType, event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setBusy(type);
    setError("");
    const body = new FormData();
    body.append("file", file);
    try {
      const res = await api.post<InternshipOnboarding>(
        `/student/internship-onboarding/documents/${type}`,
        body,
        {
          headers: { "Content-Type": "multipart/form-data" },
          params: { internshipNumber: activeTab },
        }
      );
      setOnboarding(res.data);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy("");
    }
  }

  async function download(id: number, name: string) {
    setBusy(`download-${id}`);
    setError("");
    try {
      await downloadDocument(`/student/internship-onboarding/documents/${id}/download`, name);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy("");
    }
  }

  async function confirmDiary() {
    setBusy("diary");
    setError("");
    try {
      const res = await api.post<InternshipOnboarding>(
        "/student/internship-onboarding/diary/confirm",
        {},
        { params: { internshipNumber: activeTab } }
      );
      setOnboarding(res.data);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy("");
    }
  }

  const statusBadge: InternshipStatusBadge =
    onboarding?.workflowStatus === "INTERNSHIP_DETAILS_COMPLETE"
      ? "Completed"
      : onboarding?.workflowStatus === "DIARY_ISSUED"
      ? "Active"
      : onboarding?.workflowStatus === "DOCUMENTS_COMPLETE"
      ? "Pending Approval"
      : onboarding?.workflowStatus === "NOT_SECURED"
      ? "Not Requested"
      : "Active";

  const slot2Blocked =
    activeTab === 2 &&
    (!secondRequest || secondRequest.status !== "APPROVED") &&
    onboarding?.workflowStatus === "NOT_SECURED";

  return (
    <ProtectedRoute role="STUDENT">
      <AppFrame>
        <main className="mx-auto max-w-5xl px-5 py-10 sm:px-8">
          <Link href="/student/dashboard" className="text-sm font-semibold text-blue-700">
            ← Back to dashboard
          </Link>
          <div className="mt-5">
            <p className="text-sm font-semibold text-blue-700">Internship registration</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Internship Onboarding &amp; Documents</h1>
            <p className="mt-2 text-slate-600">
              Complete required documentation and confirm diary receipt before entering company details.
            </p>
          </div>

          {/* Internship Tabs */}
          <div className="mt-8 flex border-b border-slate-200" role="tablist">
            <button
              type="button"
              role="tab"
              id="tab-onboarding-1"
              aria-selected={activeTab === 1}
              onClick={() => handleTabChange(1)}
              className={`flex items-center gap-2 border-b-2 px-6 py-3.5 text-sm font-semibold transition-colors ${
                activeTab === 1
                  ? "border-blue-700 text-blue-700"
                  : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              <span>Internship 1</span>
              {activeTab === 1 && onboarding && (
                <span className={`rounded-full border px-2 py-0.5 text-xs font-semibold ${getStatusBadgeClass(statusBadge)}`}>
                  {statusBadge}
                </span>
              )}
            </button>
            <button
              type="button"
              role="tab"
              id="tab-onboarding-2"
              aria-selected={activeTab === 2}
              onClick={() => handleTabChange(2)}
              className={`flex items-center gap-2 border-b-2 px-6 py-3.5 text-sm font-semibold transition-colors ${
                activeTab === 2
                  ? "border-blue-700 text-blue-700"
                  : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              <span>Internship 2</span>
              {activeTab === 2 && onboarding && (
                <span className={`rounded-full border px-2 py-0.5 text-xs font-semibold ${getStatusBadgeClass(statusBadge)}`}>
                  {statusBadge}
                </span>
              )}
            </button>
          </div>

          {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}

          {loading || !onboarding ? (
            <p className="py-20 text-center text-sm text-slate-500">Loading internship status…</p>
          ) : slot2Blocked ? (
            <div className="mt-8 rounded-2xl border border-amber-200 bg-amber-50 p-8 text-amber-900">
              <h2 className="font-bold text-lg">III Cell Approval Required for Internship 2</h2>
              <p className="mt-2 text-sm text-amber-800">
                You must have an approved request from the III Cell before starting the onboarding and documentation process for a second internship.
              </p>
              <div className="mt-6">
                <Link href="/student/internship/details" className="ui-button text-sm">
                  View Internship 2 Request Status
                </Link>
              </div>
            </div>
          ) : (
            <div className="mt-8 space-y-5">
              {/* Status Header */}
              <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">
                      Internship {activeTab} Status
                    </p>
                    <h2 className="mt-2 text-xl font-bold">Preparation &amp; Document Verification</h2>
                  </div>
                  <WorkflowBadge status={onboarding.workflowStatus} />
                </div>
                {onboarding.workflowStatus === "NOT_SECURED" && (
                  <div className="mt-6">
                    <p className="text-sm text-slate-600">
                      Once you have secured an offer for Internship {activeTab}, begin the registration workflow here.
                    </p>
                    <button
                      disabled={!!busy}
                      onClick={secure}
                      className="ui-button mt-4 text-sm"
                    >
                      {busy === "secure" ? "Starting…" : `Start Internship ${activeTab} Registration`}
                    </button>
                  </div>
                )}
              </section>

              {/* Step 1: Source */}
              {onboarding.id && (
                <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
                  <h2 className="text-lg font-bold">1. Internship Source</h2>
                  <p className="mt-2 text-sm text-slate-600">Select how this internship was secured.</p>
                  <div className="mt-4 grid gap-3 sm:grid-cols-3">
                    {sourceOptions.map((option) => (
                      <button
                        data-button-variant="outline"
                        key={option.value}
                        aria-pressed={onboarding.source === option.value}
                        disabled={!!busy}
                        onClick={() => selectSource(option.value)}
                        className={`rounded-xl border px-4 py-4 text-left text-sm font-semibold disabled:opacity-50 transition-colors ${
                          onboarding.source === option.value
                            ? "border-blue-700 bg-blue-50 text-blue-800"
                            : "border-slate-300 hover:border-blue-400"
                        }`}
                      >
                        <span className="mr-2">{onboarding.source === option.value ? "●" : "○"}</span>
                        {option.label}
                      </button>
                    ))}
                  </div>
                </section>
              )}

              {/* Step 2: Documents */}
              {onboarding.source && (
                <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
                  <div className="flex flex-wrap items-end justify-between gap-2">
                    <div>
                      <h2 className="text-lg font-bold">2. Required Documents</h2>
                      <p className="mt-2 text-sm text-slate-600">PDF, JPEG, or PNG · maximum 10 MB each</p>
                    </div>
                    <p className="text-sm font-bold text-blue-700">
                      {onboarding.documentsCompleted} of {onboarding.documentsRequired} completed
                    </p>
                  </div>
                  <div className="mt-5 divide-y divide-slate-100 rounded-xl border border-slate-200">
                    {onboarding.documents.map((document) => (
                      <div
                        key={document.documentType}
                        className="flex flex-wrap items-center justify-between gap-3 px-4 py-4"
                      >
                        <div>
                          <p className="text-sm font-semibold">
                            <span className={document.uploaded ? "text-emerald-600" : "text-slate-400"}>
                              {document.uploaded ? "✓" : "○"}
                            </span>{" "}
                            {document.documentTypeLabel}
                          </p>
                          <p className="mt-1 text-xs text-slate-500">
                            {document.uploaded
                              ? `${document.originalFileName} · ${fileSize(document.fileSize)}`
                              : "Required"}
                          </p>
                        </div>
                        <div className="flex gap-2">
                          {document.uploaded && document.id && (
                            <button
                              data-button-variant="outline"
                              disabled={!!busy}
                              onClick={() => download(document.id!, document.originalFileName!)}
                              className="text-xs"
                            >
                              View
                            </button>
                          )}
                          <label
                            className={`cursor-pointer rounded-lg px-3 py-2 text-xs font-semibold ${
                              busy ? "pointer-events-none opacity-50" : ""
                            } ${document.uploaded ? "border border-slate-300" : "bg-blue-700 text-white"}`}
                          >
                            {busy === document.documentType ? "Uploading…" : document.uploaded ? "Replace" : "Upload"}
                            <input
                              type="file"
                              accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                              onChange={(event) => upload(document.documentType, event)}
                              className="sr-only"
                            />
                          </label>
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {/* Step 3: Internship Diary */}
              {onboarding.source && (
                <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
                  <h2 className="text-lg font-bold">3. Internship Diary</h2>
                  {onboarding.diaryIssued ? (
                    <div className="mt-4 rounded-xl bg-emerald-50 p-4">
                      <p className="font-semibold text-emerald-800">✓ Internship Diary received</p>
                      <p className="mt-1 text-sm text-emerald-700">
                        Confirmed {onboarding.diaryIssuedAt && new Date(onboarding.diaryIssuedAt).toLocaleString()}
                      </p>
                      <Link
                        href="/student/internship/details"
                        className="ui-button mt-5 inline-block text-sm"
                      >
                        {onboarding.workflowStatus === "INTERNSHIP_DETAILS_COMPLETE"
                          ? "View Internship Details"
                          : "Continue to Internship Details"}
                      </Link>
                    </div>
                  ) : onboarding.workflowStatus !== "DOCUMENTS_COMPLETE" ? (
                    <p className="mt-3 text-sm text-slate-600">
                      Available after required documents are completed.
                    </p>
                  ) : (
                    <div className="mt-4">
                      <label className="flex items-start gap-3 text-sm font-medium">
                        <input
                          type="checkbox"
                          checked={diaryConfirmed}
                          onChange={(event) => setDiaryConfirmed(event.target.checked)}
                          className="mt-0.5 h-4 w-4"
                        />
                        I confirm that I have received my Internship Diary for Internship {activeTab}.
                      </label>
                      <button
                        disabled={!diaryConfirmed || !!busy}
                        onClick={confirmDiary}
                        className="ui-button mt-4 text-sm"
                      >
                        {busy === "diary" ? "Confirming…" : "Confirm Diary Received"}
                      </button>
                    </div>
                  )}
                </section>
              )}
            </div>
          )}
        </main>
      </AppFrame>
    </ProtectedRoute>
  );
}
