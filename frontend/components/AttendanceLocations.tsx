import type { Attendance } from "@/lib/attendance";

export function validSubmissionCoordinates(latitude: unknown, longitude: unknown): boolean {
  return typeof latitude === "number" && typeof longitude === "number"
    && Number.isFinite(latitude) && Number.isFinite(longitude)
    && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
}

export function AttendanceLocations({ evidence }: { evidence: Pick<NonNullable<Attendance["evidence"]>, "latitude" | "longitude" | "gpsAccuracyMeters"> | null | undefined }) {
  if (!evidence || !validSubmissionCoordinates(evidence.latitude, evidence.longitude))
    return <p className="mt-3 text-xs font-normal text-slate-500">Submission location unavailable for this attendance record.</p>;
  const accuracy = evidence.gpsAccuracyMeters;
  return <div className="mt-3 text-xs font-normal text-slate-600">
    <p className="font-semibold">Attendance submission location</p>
    <p>Latitude: {evidence.latitude}; Longitude: {evidence.longitude}</p>
    {typeof accuracy === "number" && Number.isFinite(accuracy) && accuracy >= 0 && <p>Reported GPS accuracy: {accuracy} m</p>}
    <a className="mt-2 inline-block text-sm font-semibold text-blue-700 hover:underline" href={`https://www.google.com/maps?q=${encodeURIComponent(`${evidence.latitude},${evidence.longitude}`)}`} target="_blank" rel="noopener noreferrer">View Submitted Location on Google Maps</a>
  </div>;
}
