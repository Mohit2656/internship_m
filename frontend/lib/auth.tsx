"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api, AuthUser } from "./api";

type AuthState = { user: AuthUser | null; loading: boolean; refresh: () => Promise<AuthUser | null>; logout: () => Promise<void> };
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const refresh = useCallback(async () => {
    try { const { data } = await api.get<AuthUser>("/auth/me"); setUser(data); return data; }
    catch { setUser(null); return null; }
    finally { setLoading(false); }
  }, []);
  useEffect(() => {
    let mounted = true;
    const fallback = window.setTimeout(() => {
      if (mounted) {
        setUser(null);
        setLoading(false);
      }
    }, 11_000);

    void refresh().finally(() => window.clearTimeout(fallback));
    return () => {
      mounted = false;
      window.clearTimeout(fallback);
    };
  }, [refresh]);
  const logout = useCallback(async () => { try { await api.post("/auth/logout"); } finally { setUser(null); } }, []);
  const value = useMemo(() => ({ user, loading, refresh, logout }), [user, loading, refresh, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext); if (!value) throw new Error("useAuth must be used inside AuthProvider"); return value;
}
