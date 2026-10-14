"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { downloadDocument } from "@/lib/internship-onboarding";

type Props = {
  contentType: string | null;
  downloadUrl: string;
  fileName: string;
  onClose: () => void;
  viewUrl: string;
};

export function DocumentPreviewModal({ contentType, downloadUrl, fileName, onClose, viewUrl }: Props) {
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const normalizedType = contentType?.split(";")[0].toLowerCase() ?? "";
  const previewKind = normalizedType === "application/pdf" ? "pdf" : normalizedType === "image/jpeg" || normalizedType === "image/png" ? "image" : "unsupported";

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) { if (event.key === "Escape") onClose(); }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onClose]);

  useEffect(() => {
    if (previewKind === "unsupported") { setLoading(false); return; }
    let cancelled = false;
    let url: string | null = null;
    setLoading(true); setError("");
    api.get<Blob>(viewUrl, { responseType: "blob" }).then(response => {
      if (cancelled) return;
      const responseType = String(response.headers["content-type"] ?? normalizedType).split(";")[0];
      url = URL.createObjectURL(new Blob([response.data], { type: responseType }));
      setObjectUrl(url); setLoading(false);
    }).catch(() => {
      if (!cancelled) { setError("The document preview could not be loaded."); setLoading(false); }
    });
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [normalizedType, previewKind, viewUrl]);

  async function download() {
    setError("");
    try { await downloadDocument(downloadUrl, fileName); }
    catch { setError("The document could not be downloaded."); }
  }

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4" role="presentation" onMouseDown={onClose}>
    <section role="dialog" aria-modal="true" aria-label={`${fileName} preview`} className="flex max-h-[92vh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl" onMouseDown={event => event.stopPropagation()}>
      <header className="flex items-center justify-between gap-4 border-b border-slate-200 px-5 py-4 sm:px-6"><h2 className="min-w-0 truncate text-lg font-bold text-slate-950">{fileName}</h2><div className="flex shrink-0 items-center gap-2"><button data-button-variant="outline" onClick={download} className="text-sm">Download</button><button data-button-variant="icon" onClick={onClose} aria-label="Close preview" className="text-lg">×</button></div></header>
      <div className="min-h-0 flex-1 overflow-auto bg-slate-100 p-4 sm:p-6">{loading ? <p className="py-24 text-center text-sm text-slate-500">Loading document preview…</p> : error ? <p className="mx-auto max-w-md rounded-xl bg-red-50 p-4 text-center text-sm text-red-700">{error}</p> : previewKind === "unsupported" ? <p className="mx-auto max-w-md rounded-xl bg-amber-50 p-4 text-center text-sm text-amber-800">This file type cannot be previewed here. Use Download to open it locally.</p> : objectUrl && previewKind === "pdf" ? <iframe title={fileName} src={objectUrl} className="h-[72vh] w-full rounded-lg border border-slate-200 bg-white"/> : objectUrl && <img src={objectUrl} alt={fileName} className="mx-auto max-h-[72vh] max-w-full rounded-lg bg-white object-contain shadow-sm"/>}</div>
    </section>
  </div>;
}
