"use client";

import { useCallback, useRef, useState } from "react";

export function useActionGuard() {
  const active = useRef(new Set<string>());
  const [pending, setPending] = useState<ReadonlySet<string>>(() => new Set());

  const run = useCallback(async <T,>(key: string, action: () => Promise<T>): Promise<T | undefined> => {
    if (active.current.has(key)) return undefined;
    active.current.add(key);
    setPending(new Set(active.current));
    try {
      return await action();
    } finally {
      active.current.delete(key);
      setPending(new Set(active.current));
    }
  }, []);

  const isPending = useCallback((key: string) => pending.has(key), [pending]);
  return { run, isPending };
}
