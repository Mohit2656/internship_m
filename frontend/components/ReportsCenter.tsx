"use client";

import { useEffect, useState } from "react";
import axios from "axios";
import { api } from "@/lib/api";
import { DashboardReportExport } from "@/components/DashboardReportExport";

type Field = { id: string; label: string; category: string; defaultSelected?: boolean };
type Filter = { id: string; label: string; type: string; choices: { value: string; label: string }[]; description: string };
type Report = { id: string; title: string; description: string; available: boolean; fields: Field[] };
type Options = { types: Report[]; filters: Filter[]; scope: string; date: string };
type Table = { fields: Field[]; rows: Record<string, unknown>[]; totalRows: number; page: number; summary: Record<string, unknown>; title: string };
const control = "mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm";
const button = "rounded-lg bg-blue-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50";
function display(value: unknown): string { return value == null ? "—" : typeof value === "boolean" ? value ? "Yes" : "No" : String(value); }
async function message(error: unknown) {
  if (axios.isAxiosError(error)) {
    let data = error.response?.data;
    if (data instanceof Blob) { try { data = JSON.parse(await data.text()); } catch { return "Report could not be generated."; } }
    return data?.message || "Report request failed.";
  }
  return "Report request failed.";
}

export function ReportsCenter({ role, attendanceOnly = false }: { role: "ADMIN" | "MENTOR"; attendanceOnly?: boolean }) {
  const base = `/${role.toLowerCase()}/reports`;
  const [options, setOptions] = useState<Options | null>(null);
  const [type, setType] = useState(attendanceOnly ? "ATTENDANCE" : "OVERALL");
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [ids, setIds] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [table, setTable] = useState<Table | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [previewBusy, setPreviewBusy] = useState(false);
  const [reload, setReload] = useState(0);
  const report = options?.types.find(item => item.id === type);
  const [reviewed, setReviewed] = useState("");
  const [requestKey, setRequestKey] = useState(0);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerSearch, setPickerSearch] = useState("");
  const [pickerPage, setPickerPage] = useState(0);
  const [picker, setPicker] = useState<Table | null>(null);
  const [pickerError, setPickerError] = useState("");
  const config = JSON.stringify({ reportType: type, fields: selected, filters: Object.fromEntries(Object.entries(filters).filter(([, value]) => value.trim())), studentIds: ids.trim() ? ids.split(",").map(value => Number(value.trim())) : [], page, format: "XLSX" });
  useEffect(() => {
    const abort = new AbortController();
    api.get<Options>(`${base}/types`, { signal: abort.signal }).then(({ data }) => { setOptions(data); setError(""); }).catch(async e => { if (!abort.signal.aborted) setError(await message(e)); });
    return () => abort.abort();
  }, [base, reload]);
  useEffect(() => { if (report) setSelected(report.fields.filter(field => report.id !== "MONTHLY_PROGRESS" || field.defaultSelected).map(field => field.id)); }, [report]);
  useEffect(() => {
    if (!pickerOpen) return;
    const abort = new AbortController(); setPicker(null); setPickerError("");
    const timer = window.setTimeout(() => api.post<Table>(`${base}/table`, { reportType: "ROSTER", fields: ["studentId", "uid", "studentName", "company"], filters: pickerSearch ? { search: pickerSearch } : {}, studentIds: [], page: pickerPage, format: "XLSX" }, { signal: abort.signal })
      .then(({ data }) => setPicker(data)).catch(async e => { if (!abort.signal.aborted) setPickerError(await message(e)); }), 250);
    return () => { window.clearTimeout(timer); abort.abort(); };
  }, [base, pickerOpen, pickerSearch, pickerPage]);
  useEffect(() => {
    setTable(null); setReviewed("");
    if (!report?.available || !selected.length) { setPreviewBusy(false); return; }
    const abort = new AbortController(); setPreviewBusy(true);
    const timer = window.setTimeout(() => {
      api.post<Table>(`${base}/table`, JSON.parse(config), { signal: abort.signal }).then(({ data }) => { setTable(data); setReviewed(config); setError(""); })
        .catch(async e => { if (!abort.signal.aborted) setError(await message(e)); }).finally(() => { if (!abort.signal.aborted) setPreviewBusy(false); });
    }, 350);
    return () => { window.clearTimeout(timer); abort.abort(); };
  }, [base, config, report?.available, selected.length, requestKey]);
  function changeType(value: string) { if (!(type.startsWith("ATTENDANCE") && value.startsWith("ATTENDANCE"))) setFilters({}); setType(value); setPage(0); setTable(null); }
  async function download(format: "XLSX" | "CSV") {
    setBusy(true); setError("");
    try {
      const response = await api.post<Blob>(`${base}/generate`, { ...JSON.parse(config), format, page: 0 }, { responseType: "blob", timeout: 180_000 });
      const url = URL.createObjectURL(response.data); const link = document.createElement("a"); link.href = url;
      link.download = `${type.toLowerCase()}-${options?.date}.${format === "XLSX" ? "xlsx" : "csv"}`;
      document.body.appendChild(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
    } catch (e) { setError(await message(e)); } finally { setBusy(false); }
  }
  const attendance = type.startsWith("ATTENDANCE");
  const availableFilters = options?.filters.filter(filter => (type === "MONTHLY_PROGRESS" || !["geoVerificationStatus", "gpsPresent", "exifPresent"].includes(filter.id)) && (attendance || !["historyFrom", "historyTo", "attendanceStatus", "verificationStatus"].includes(filter.id))) || [];
  const current = reviewed === config ? table : null;
  return <main className="mx-auto max-w-7xl px-5 py-10 sm:px-8">
    <div className="flex flex-wrap items-start justify-between gap-4"><div><h1 className="text-3xl font-bold">{attendanceOnly ? "Attendance Records" : "Reports Center"}</h1><p className="mt-2 text-slate-600">{options?.scope || "Loading authorized report options…"}</p></div>{!attendanceOnly && <DashboardReportExport role={role} />}</div>
    {error && <div role="alert" className="mt-5 rounded-lg bg-red-50 p-4 text-red-700">{error} <button type="button" onClick={() => { setReload(n => n + 1); setRequestKey(n => n + 1); }} className="underline">Retry</button></div>}
    <div className="mt-7 grid gap-6 lg:grid-cols-[18rem_1fr]">
      <nav aria-label="Report types" className="space-y-2">{options?.types.filter(item => !attendanceOnly || item.id.startsWith("ATTENDANCE")).map(item => <button key={item.id} type="button" disabled={busy} onClick={() => changeType(item.id)} aria-pressed={type === item.id} className={`w-full rounded-xl border p-4 text-left ${type === item.id ? "border-blue-600 bg-blue-50" : "border-slate-200 bg-white"}`}><span className="block font-semibold">{item.title}</span><span className="mt-1 block text-xs text-slate-600">{item.available ? item.description : `Unavailable. ${item.description}`}</span></button>)}</nav>
      <section className="min-w-0 rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="text-xl font-bold">{report?.title}</h2><p className="mt-2 text-sm text-slate-600">{report?.description}</p>
        {report?.available && <div className="mt-4"><button type="button" disabled={busy} className="text-sm font-semibold text-blue-700 underline" onClick={() => setPickerOpen(value => !value)}>Choose {role === "MENTOR" ? "assigned " : ""}students</button>{pickerOpen && <div className="mt-3 rounded-lg border border-slate-200 p-4"><label className="text-sm">Search students<input className={control} value={pickerSearch} onChange={e => { setPickerSearch(e.target.value); setPickerPage(0); }} /></label><button type="button" className="mt-2 text-sm text-blue-700" onClick={() => { setIds(""); setPage(0); }}>Use all authorized students</button>{pickerError && <p role="alert" className="text-red-700">{pickerError}</p>}<div className="mt-3 space-y-2">{picker?.rows.map(row => <label key={String(row.studentId)} className="flex items-center gap-2 text-sm"><input type="checkbox" disabled={busy} checked={ids.split(",").map(id => id.trim()).includes(String(row.studentId))} onChange={e => { const next = new Set(ids.split(",").map(id => id.trim()).filter(Boolean)); if (e.target.checked) next.add(String(row.studentId)); else next.delete(String(row.studentId)); setIds([...next].join(", ")); setPage(0); }} />{display(row.studentName)} · {display(row.uid)} · {display(row.company)}</label>)}</div>{picker && <div className="mt-3 flex gap-4"><button type="button" disabled={pickerPage === 0} onClick={() => setPickerPage(p => p - 1)} className="text-sm text-blue-700 disabled:text-slate-400">Previous students</button><button type="button" disabled={(pickerPage + 1) * 50 >= picker.totalRows} onClick={() => setPickerPage(p => p + 1)} className="text-sm text-blue-700 disabled:text-slate-400">Next students</button></div>}</div>}</div>}
        {report?.available && <><fieldset disabled={busy} className="mt-5"><legend className="font-semibold">Filters</legend><div className="mt-3 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{availableFilters.map(filter => <label key={filter.id} className="text-sm">{filter.label}{filter.type === "select" ? <select className={control} value={filters[filter.id] || ""} onChange={e => { setFilters({ ...filters, [filter.id]: e.target.value }); setPage(0); }}><option value="">All</option>{filter.choices.map(choice => <option key={choice.value} value={choice.value}>{choice.label}</option>)}</select> : <input className={control} type={filter.type} value={filters[filter.id] || ""} onChange={e => { setFilters({ ...filters, [filter.id]: e.target.value }); setPage(0); }} />}<span className="mt-1 block text-xs text-slate-500">{filter.description}</span></label>)}<label className="text-sm">Selected student account IDs<input className={control} value={ids} onChange={e => { setIds(e.target.value); setPage(0); }} placeholder="e.g. 12, 18, 24" /><span className="mt-1 block text-xs text-slate-500">Leave blank for all authorized students. Every selected ID is checked by the server.</span></label></div><button type="button" className="mt-3 text-sm font-semibold text-blue-700" onClick={() => { setFilters({}); setIds(""); setPage(0); }}>Clear filters</button></fieldset>
        <details className="mt-5 rounded-lg border border-slate-200 p-3"><summary className="cursor-pointer font-semibold">Report columns ({selected.length}/{report.fields.length})</summary><div className="mt-3 flex gap-4"><button type="button" disabled={busy} onClick={() => setSelected(report.fields.filter(field => report.id !== "MONTHLY_PROGRESS" || field.defaultSelected).map(field => field.id))} className="text-sm text-blue-700">Select All</button><button type="button" disabled={busy} onClick={() => setSelected([])} className="text-sm text-blue-700">Deselect All</button></div><div className="mt-3 grid gap-2 sm:grid-cols-2">{report.fields.map(field => <label key={field.id} className="flex items-center gap-2 text-sm"><input type="checkbox" disabled={busy} checked={selected.includes(field.id)} onChange={e => setSelected(report.fields.filter(item => item.id === field.id ? e.target.checked : selected.includes(item.id)).map(item => item.id))} />{field.label}</label>)}</div></details>
        <div className="mt-5 flex flex-wrap items-center gap-3"><button type="button" className={button} disabled={busy || previewBusy || !current || !selected.length} onClick={() => void download("XLSX")}>Export Excel</button><button type="button" className={button} disabled={busy || previewBusy || !current || !selected.length} onClick={() => void download("CSV")}>Export CSV</button><span aria-live="polite" className="text-sm text-slate-600">{busy ? "Generating report…" : previewBusy ? "Loading preview…" : current ? `${current.totalRows} records match. Export includes all matching records.` : "Select at least one column."}</span></div>
        {current && <><div className="mt-5 flex flex-wrap gap-4">{Object.entries(current.summary).map(([key, value]) => <div key={key} className="rounded-lg bg-slate-50 p-3"><span className="block text-xs text-slate-600">{key === "presentRecordPercentage" ? "Present / stored records (%)" : key}</span><strong>{typeof value === "number" ? Number(value.toFixed(1)) : display(value)}</strong></div>)}</div><div className="mt-5 overflow-x-auto"><table className="w-full text-left text-sm"><thead className="bg-slate-100"><tr>{current.fields.map(field => <th key={field.id} className="whitespace-nowrap p-3">{field.label}</th>)}</tr></thead><tbody>{current.rows.map((row, index) => <tr key={index} className="border-b border-slate-200">{current.fields.map(field => <td key={field.id} className="whitespace-pre-wrap p-3">{display(row[field.id])}{attendanceOnly && field.id === "studentId" && <button type="button" className="ml-2 text-blue-700 underline" onClick={() => { setIds(String(row[field.id])); setPage(0); }}>View student</button>}</td>)}</tr>)}</tbody></table>{!current.rows.length && <p className="p-4 text-slate-600">No records match these filters.</p>}</div><div className="mt-4 flex items-center gap-4"><button type="button" className={button} disabled={busy || page === 0} onClick={() => setPage(n => n - 1)}>Previous</button><span>Page {page + 1}</span><button type="button" className={button} disabled={busy || (page + 1) * 50 >= current.totalRows} onClick={() => setPage(n => n + 1)}>Next</button></div></>}
        </>}
      </section>
    </div>
  </main>;
}
