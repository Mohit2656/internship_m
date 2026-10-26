export type AttendanceLocationSample = { latitude: number; longitude: number; accuracy: number; capturedAt?: string };
export type CaptureResult = { sample: AttendanceLocationSample | null; reason: "CAPTURED" | "INSUFFICIENT_ACCURACY" | "INVALID_ACCURACY" | "PERMISSION_DENIED" | "UNAVAILABLE" | "TIMEOUT" };
export const LOCATION_CAPTURE_WINDOW_MS = 20_000;

export function validReportedAccuracy(accuracy: number) {
  // The existing backend stores positive accuracy to two decimal places.
  return Number.isFinite(accuracy) && accuracy >= 0.005 && accuracy <= 999999.99;
}

export function usableAccuracy(accuracy: number, maximum: number) {
  return validReportedAccuracy(accuracy) && accuracy <= maximum;
}

export function accuracyDescription(accuracy: number) {
  return validReportedAccuracy(accuracy) ? `${accuracy} m` : "unavailable or invalid";
}

/** One user-initiated, bounded capture. No watchPosition or background tracking. */
export function captureAttendanceLocation({ maximumAccuracy, onReading, onComplete }: {
  maximumAccuracy: number | null;
  onReading: (sample: AttendanceLocationSample) => void;
  onComplete: (result: CaptureResult) => void;
}): { cancel: () => void } {
  let stopped = false;
  let best: AttendanceLocationSample | null = null;
  let retry: ReturnType<typeof setTimeout> | undefined;
  let deadline: ReturnType<typeof setTimeout> | undefined;
  const cancel = () => { stopped = true; clearTimeout(retry); clearTimeout(deadline); };
  const finish = (reason: CaptureResult["reason"], sample = best) => {
    if (stopped) return;
    cancel(); onComplete({ sample, reason });
  };
  const exhausted = () => finish(best ? validReportedAccuracy(best.accuracy) ? "INSUFFICIENT_ACCURACY" : "INVALID_ACCURACY" : "TIMEOUT");
  const again = () => { if (!stopped) retry = setTimeout(request, 2_000); };
  function request() {
    if (stopped) return;
    try {
      if (typeof navigator === "undefined" || !navigator.geolocation) { finish("UNAVAILABLE", null); return; }
      navigator.geolocation.getCurrentPosition(position => {
        if (stopped) return;
        const { latitude, longitude, accuracy } = position.coords;
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) { again(); return; }
        const time = new Date(position.timestamp);
        const sample: AttendanceLocationSample = { latitude, longitude, accuracy,
          ...(position.timestamp > 0 && Number.isFinite(time.getTime()) ? { capturedAt: time.toISOString() } : {}) };
        if (!best || (validReportedAccuracy(accuracy) && (!validReportedAccuracy(best.accuracy) || accuracy < best.accuracy))) best = sample;
        onReading(best);
        // Remote attendance keeps its existing no-geofence rule. Every payload retains real measurements.
        if (maximumAccuracy === null || usableAccuracy(accuracy, maximumAccuracy)) { finish("CAPTURED", sample); return; }
        again();
      }, error => {
        if (stopped) return;
        if (error.code === 1) { finish("PERMISSION_DENIED", null); return; }
        again();
      }, { enableHighAccuracy: true, timeout: 8_000, maximumAge: 0 });
    } catch { finish("UNAVAILABLE", null); }
  }
  // This timer also bounds time spent waiting on a browser permission prompt.
  deadline = setTimeout(exhausted, LOCATION_CAPTURE_WINDOW_MS);
  request();
  return { cancel };
}
