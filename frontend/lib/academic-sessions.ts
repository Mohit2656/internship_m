"use client";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
export type AcademicSession = { id: number; name: string; startDate: string | null; endDate: string | null; description: string | null; active: boolean; studentCount: number; status: "ACTIVE" | "CLOSED" | "INACTIVE"; closedAt: string | null; closedById: number | null; closedBy: string | null };
export type SessionDeletionPreview = { session: AcademicSession; records: Record<string, number>; recordCount: number; canDelete: boolean; explanation: string };
export function useAcademicSessions() {
  const [sessions, setSessions] = useState<AcademicSession[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    api.get<AcademicSession[]>("/admin/academic-sessions", { signal: controller.signal }).then(({ data }) => {
      setSessions(data);
      const requested = Number(new URLSearchParams(window.location.search).get("sessionId"));
      setSelected(data.some(s => s.id === requested) ? requested : data.find(s => s.active)?.id ?? null);
      setReady(true);
    }).catch(() => { if (!controller.signal.aborted) { setError("Academic sessions could not be loaded."); setReady(true); } });
    return () => controller.abort();
  }, []);
  return { sessions, selected, setSelected, error, ready };
}
