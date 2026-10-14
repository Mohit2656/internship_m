"use client";

import { useEffect, useRef, useState } from "react";
import axios from "axios";
import { api } from "@/lib/api";

type Field = { id: string; label: string; category: string; defaultSelected: boolean };
type Filter = { id: string; label: string; type: "text" | "number" | "date" | "select"; choices: { value: string; label: string }[]; description: string };
type Options = { fields: Field[]; filters: Filter[]; attendanceDate: string; timezone: string; scope: string };
type Preview = { studentCount: number; attendanceDate: string; students: { studentId: number; studentName: string | null; uid: string | null; loginEmail: string }[] };

async function errorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    if (error.response?.data instanceof Blob) {
      try { return (JSON.parse(await error.response.data.text()) as { message?: string }).message || fallback; } catch { return fallback; }
    }
    return (error.response?.data as { message?: string } | undefined)?.message || fallback;
  }
  return fallback;
}

export function DashboardReportExport({ role }: { role: "ADMIN" | "MENTOR" }) {
  const [open, setOpen] = useState(false);
  return <>
    <button type="button" onClick={() => setOpen(true)} className="text-sm">Export Report</button>
    {open && <ReportDialog role={role} onClose={() => setOpen(false)} />}
  </>;
}

function ReportDialog({ role, onClose }: { role: "ADMIN" | "MENTOR"; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [options, setOptions] = useState<Options | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [format, setFormat] = useState<"XLSX" | "CSV">("XLSX");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState("");
  const [previewError, setPreviewError] = useState("");
  const [success, setSuccess] = useState("");
  const [reload, setReload] = useState(0);
  const base = role === "ADMIN" ? "/admin/dashboard/reports" : "/mentor/dashboard/reports";
  const request = useRef<AbortController | null>(null);
  const configuration = JSON.stringify({ fields: selected, filters, format });
  const [reviewedConfiguration, setReviewedConfiguration] = useState("");

  useEffect(() => {
    dialog.current?.showModal();
    return () => { request.current?.abort(); };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    api.get<Options>(`${base}/options`, { signal: controller.signal }).then(({ data }) => {
      setOptions(data); setSelected(data.fields.filter(field => field.defaultSelected).map(field => field.id));
    }).catch(async failure => { if (!controller.signal.aborted) setError(await errorMessage(failure, "Unable to load report options.")); });
    return () => controller.abort();
  }, [base, reload]);
  useEffect(() => {
    setPreview(null); setReviewedConfiguration(""); setPreviewError(""); setSuccess("");
    if (!options || selected.length === 0) { setPreviewing(false); return; }
    const controller = new AbortController(); setPreviewing(true);
    const timer = window.setTimeout(() => {
      api.post<Preview>(`${base}/preview`, JSON.parse(configuration), { signal: controller.signal, timeout: 30_000 })
        .then(({ data }) => { setPreview(data); setReviewedConfiguration(configuration); })
        .catch(async failure => { if (!controller.signal.aborted) setPreviewError(await errorMessage(failure, "Unable to preview the report.")); })
        .finally(() => { if (!controller.signal.aborted) setPreviewing(false); });
    }, 350);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [base, configuration, options, selected.length]);

  function toggle(ids: string[], checked: boolean) {
    if (!options) return;
    const next = new Set(selected);
    ids.forEach(id => checked ? next.add(id) : next.delete(id));
    setSelected(options.fields.filter(field => next.has(field.id)).map(field => field.id));
  }
  async function download() {
    if (!selected.length || reviewedConfiguration !== configuration || !preview || generating) return;
    setGenerating(true); setError(""); setSuccess("");
    const controller = new AbortController(); request.current = controller;
    try {
      const response = await api.post<Blob>(`${base}/export`, JSON.parse(configuration), { responseType: "blob", timeout: 180_000, signal: controller.signal });
      const extension = format === "XLSX" ? "xlsx" : "csv";
      const url = URL.createObjectURL(response.data); const link = document.createElement("a");
      link.href = url; link.download = `dashboard-students-${preview.attendanceDate}.${extension}`;
      document.body.appendChild(link); link.click(); link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
      const count = response.headers["x-report-student-count"] ?? preview.studentCount;
      setSuccess(`Downloaded ${format === "XLSX" ? "Excel" : "CSV"} report containing ${count} students.`);
    } catch (failure) {
      if (!controller.signal.aborted) setError(await errorMessage(failure, "Unable to generate the report. Please try again."));
    } finally { setGenerating(false); request.current = null; }
  }
  const categories = options ? [...new Set(options.fields.map(field => field.category))] : [];
  const currentPreview = reviewedConfiguration === configuration ? preview : null;
  return <dialog ref={dialog} aria-labelledby="report-title" onClose={onClose} onCancel={event => { if (generating) event.preventDefault(); }}
    className="m-auto max-h-[90dvh] w-[min(96vw,72rem)] rounded-2xl border border-slate-200 bg-white p-0 text-slate-900 shadow-2xl backdrop:bg-slate-950/50">
    <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-slate-200 bg-white px-5 py-4 sm:px-7">
      <div><h2 id="report-title" className="text-xl font-bold">Export Dashboard Report</h2><p className="mt-1 text-sm text-slate-600">Choose students, columns, and a download format.</p></div>
      <button data-button-variant="outline" type="button" aria-label="Close report configuration" disabled={generating} onClick={onClose} className="text-sm">Close</button>
    </div>
    <div className="space-y-6 p-5 sm:p-7" aria-busy={generating}>
      {error && <div role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}{!options && <button data-button-variant="text" type="button" onClick={() => setReload(value => value + 1)} className="ml-3 underline">Retry</button>}</div>}
      {!options && !error && <p role="status" className="text-sm text-slate-500">Loading authorized report fields…</p>}
      {options && <>
        <p className="rounded-xl bg-blue-50 p-4 text-sm text-blue-950">{options.scope}. Attendance reference: {options.attendanceDate} ({options.timezone}).</p>
        <fieldset disabled={generating} className="space-y-6 disabled:opacity-70">
          <section aria-labelledby="report-filters-title">
            <div className="flex flex-wrap items-center justify-between gap-3"><h3 id="report-filters-title" className="text-lg font-bold">1. Filter students</h3><button data-button-variant="text" type="button" onClick={() => setFilters({})} className="text-sm">Clear filters</button></div>
            <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{options.filters.map(filter => <label key={filter.id} className="text-sm font-semibold text-slate-700">
              {filter.label}
              {filter.type === "select" ? <select value={filters[filter.id] || ""} onChange={event => setFilters(previous => ({ ...previous, [filter.id]: event.target.value }))} className="mt-1.5 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 font-normal"><option value="">All</option>{filter.choices.map(choice => <option key={choice.value} value={choice.value}>{choice.label}</option>)}</select>
                : <input type={filter.type} min={filter.type === "number" ? 1 : filter.type === "date" ? "1900-01-01" : undefined} max={filter.type === "date" ? "9999-12-31" : undefined} step={filter.type === "number" ? 1 : undefined} maxLength={254} value={filters[filter.id] || ""} placeholder={filter.id === "attendanceDate" ? options.attendanceDate : "Any"} onChange={event => setFilters(previous => ({ ...previous, [filter.id]: event.target.value }))} className="mt-1.5 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 font-normal" />}
              {filter.description && <span className="mt-1 block text-xs font-normal text-slate-500">{filter.description}</span>}
            </label>)}</div>
          </section>
          <section aria-labelledby="report-fields-title">
            <div className="flex flex-wrap items-center justify-between gap-3"><h3 id="report-fields-title" className="text-lg font-bold">2. Select report columns <span className="text-sm font-normal text-slate-500">({selected.length} of {options.fields.length})</span></h3>
              <div className="flex gap-4 text-sm font-semibold text-blue-700"><button type="button" onClick={() => setSelected(options.fields.map(field => field.id))}>Select All</button><button type="button" onClick={() => setSelected([])}>Deselect All</button></div></div>
            <div className="mt-4 grid gap-4 md:grid-cols-2">{categories.map(category => {
              const fields = options.fields.filter(field => field.category === category); const count = fields.filter(field => selected.includes(field.id)).length;
              return <fieldset key={category} className="rounded-xl border border-slate-200 p-4"><legend className="px-1 text-sm font-bold">{category}</legend>
                <label className="mb-3 flex items-center gap-2 border-b border-slate-100 pb-3 text-sm font-semibold text-blue-800"><input type="checkbox" checked={count === fields.length} ref={node => { if (node) node.indeterminate = count > 0 && count < fields.length; }} onChange={event => toggle(fields.map(field => field.id), event.target.checked)} className="h-4 w-4 accent-blue-700"/>Select category <span className="ml-auto text-xs">{count}/{fields.length}</span></label>
                <div className="space-y-2">{fields.map(field => <label key={field.id} className={`flex cursor-pointer items-start gap-2 rounded-lg p-2 text-sm ${selected.includes(field.id) ? "bg-blue-50 text-blue-950" : "text-slate-600"}`}><input type="checkbox" checked={selected.includes(field.id)} onChange={event => toggle([field.id], event.target.checked)} className="mt-0.5 h-4 w-4 shrink-0 accent-blue-700"/>{field.label}</label>)}</div>
              </fieldset>;
            })}</div>
          </section>
          <section aria-labelledby="report-review-title" className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <h3 id="report-review-title" className="text-lg font-bold">3. Review and download</h3>
            <p className="mt-2 text-sm text-slate-600">Columns appear in the order below. Related records use readable lines in each student&apos;s row.</p>
            <div className="mt-3 flex flex-wrap gap-2">{options.fields.filter(field => selected.includes(field.id)).map(field => <span key={field.id} className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-700 ring-1 ring-slate-200">{field.label}</span>)}</div>
            {!selected.length && <p className="mt-3 text-sm text-amber-800">Select at least one column.</p>}
            <div aria-live="polite" className="mt-4 text-sm">
              {previewing ? <p className="text-slate-500">Checking matching students…</p> : previewError ? <p role="alert" className="text-red-700">{previewError}</p> : currentPreview && <><p className="font-semibold">{currentPreview.studentCount} authorized students match. Attendance reference date: {currentPreview.attendanceDate}.</p>
                {currentPreview.studentCount === 0 ? <p className="mt-1 text-slate-500">The report will contain headers with no student rows.</p> : <details className="mt-2"><summary className="cursor-pointer text-blue-700">Preview students (first {currentPreview.students.length})</summary><ul className="mt-2 space-y-1 text-slate-600">{currentPreview.students.map(student => <li key={student.studentId}>{student.studentName || "Profile incomplete"} · {student.uid || "UID unavailable"} · {student.loginEmail} · Account ID {student.studentId}</li>)}</ul></details>}</>}
            </div>
            <label className="mt-5 block text-sm font-semibold">Report format<select value={format} onChange={event => setFormat(event.target.value as "XLSX" | "CSV")} className="ml-3 rounded-lg border border-slate-300 bg-white px-3 py-2"><option value="XLSX">Excel (.xlsx)</option><option value="CSV">CSV (.csv)</option></select></label>
            <p className="mt-2 text-xs text-slate-500">Excel includes a report metadata sheet, formatted columns, filters, and a frozen header. CSV includes the same selected headers and data, using UTF-8.</p>
          </section>
        </fieldset>
        <div className="flex flex-wrap items-center gap-4"><button type="button" disabled={generating || previewing || !currentPreview || !selected.length} onClick={() => void download()} className="text-sm">{generating ? "Generating report…" : `Download ${format === "XLSX" ? "Excel" : "CSV"} Report`}</button>
          {generating && <p role="status" className="text-sm text-slate-500">Preparing your selected student records. Please keep this dialog open.</p>}
          {success && <p role="status" className="text-sm text-emerald-700">{success}</p>}</div>
      </>}
    </div>
  </dialog>;
}
