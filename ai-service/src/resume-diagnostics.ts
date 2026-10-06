import { getHeapStatistics } from "node:v8";
import { readFileSync } from "node:fs";

function containerMemoryLimit(): number | undefined {
  for (const path of ["/sys/fs/cgroup/memory.max", "/sys/fs/cgroup/memory/memory.limit_in_bytes"]) {
    try {
      const value = Number(readFileSync(path, "utf8").trim());
      if (Number.isFinite(value) && value > 0 && value < Number.MAX_SAFE_INTEGER) return value;
    } catch { /* Resource probes must never interrupt resume processing. */ }
  }
}
const memoryLimitBytes = containerMemoryLimit();

export function resourceSnapshot() {
  try {
    const usage = process.memoryUsage();
    return {
      rssBytes: usage.rss, heapUsedBytes: usage.heapUsed, heapTotalBytes: usage.heapTotal,
      externalBytes: usage.external, arrayBufferBytes: usage.arrayBuffers,
      heapLimitBytes: getHeapStatistics().heap_size_limit, containerMemoryLimitBytes: memoryLimitBytes
    };
  } catch { return { resourceSnapshotUnavailable: true }; }
}

export function createDiagnosticRedactor(secrets: string[] = Object.entries(process.env)
    .filter(([key]) => /TOKEN|SECRET|PASSWORD|PASSWD|API.?KEY|PRIVATE.?KEY|ENCRYPTION.?KEY|CREDENTIAL|AUTHORIZATION|DATABASE_URL|DB_URL/i.test(key))
    .map(([, value]) => value ?? "")) {
  const sensitiveValues = new Set(secrets.filter(value => value.length >= 4));
  const registerSensitiveText = (value: string) => {
    // Keep these values private to the redactor; never attach text or parsed records to log events.
    for (const fragment of [value, value.replace(/\s+/g, " "), ...value.split(/\r?\n/)].map(part => part.trim())) {
      if (fragment.length >= 4) sensitiveValues.add(fragment);
    }
  };
  const redact = (value: string): string => {
    let clean = value;
    for (const secret of [...sensitiveValues].sort((a, b) => b.length - a.length)) clean = clean.split(secret).join("[REDACTED]");
    return clean
      .replace(/\b(Bearer\s+)[A-Za-z0-9._~+\/-]+=*/gi, "$1[REDACTED]")
      .replace(/\b[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b/g, "[REDACTED]")
      .replace(/\b(api[-_ ]?key|password|passwd|token|secret|authorization)\s*[:=]\s*(?:"[^"]*"|'[^']*'|[^\s,;]+)/gi, "$1=[REDACTED]")
      .replace(/([a-z][a-z0-9+.-]*:\/\/)[^\s/@]+:[^\s/@]+@/gi, "$1[REDACTED]@")
      .replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi, "[REDACTED]")
      .replace(/(?<![\w])\+?\d[\d ()-]{7,}\d(?![\w])/g, "[REDACTED]");
  };
  return { redact, registerSensitiveText };
}

export type DiagnosticError = {
  name: string; message: string; stack?: string; code?: string;
  cause?: DiagnosticError; errors?: DiagnosticError[];
};

function readField(error: object, key: string): unknown {
  try { return Reflect.get(error, key); } catch { return undefined; }
}

export function describeError(error: unknown, redact: (value: string) => string,
    seen = new Set<object>()): DiagnosticError {
  if (error === null || (typeof error !== "object" && typeof error !== "function")) {
    return { name: typeof error, message: redact(String(error)) };
  }
  if (seen.has(error)) return { name: "CircularCause", message: "[Circular error cause]" };
  seen.add(error);
  const name = readField(error, "name");
  const message = readField(error, "message");
  const stack = readField(error, "stack");
  const code = readField(error, "code");
  const cause = readField(error, "cause");
  const errors = readField(error, "errors");
  // Explicit allowlist: do not serialize arbitrary library properties, request bodies, or parsed resumes.
  return {
    name: redact(typeof name === "string" ? name : "UnknownError"),
    message: redact(typeof message === "string" ? message : "Non-Error object thrown"),
    stack: typeof stack === "string" ? redact(stack) : undefined,
    code: typeof code === "string" && /^[A-Z][A-Z0-9_]{0,63}$/.test(code) ? redact(code) : undefined,
    cause: cause === undefined ? undefined : describeError(cause, redact, seen),
    errors: Array.isArray(errors) ? errors.map(value => describeError(value, redact, seen)) : undefined
  };
}

export function resourceFailure(error: DiagnosticError): boolean {
  return /out of memory|ENOMEM|memory limit|allocation|bad_alloc|resource exhausted|EMFILE|ENFILE|EAGAIN|pthread|thread creation/i.test(JSON.stringify(error));
}
