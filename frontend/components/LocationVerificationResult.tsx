import type { AttendanceLocationEvidence } from "@/lib/attendance";

export function LocationVerificationResult({ evidence }: { evidence?: AttendanceLocationEvidence | null }) {
  const status = evidence?.locationVerificationStatus;
  // Historical records have no GPS verification outcome; do not infer one from coordinates or liveness.
  if (!status || status === "NOT_APPLICABLE") return null;
  const labels: Record<string, string> = {
    VERIFIED: "Workplace location verified",
    OUTSIDE_RADIUS: "Location outside the permitted workplace radius — manual review required.",
    INSUFFICIENT_ACCURACY: "GPS accuracy is insufficient — manual review required.",
    LOCATION_UNAVAILABLE: "Location could not be verified — manual review required.",
    WORKPLACE_NOT_APPROVED: "Workplace approval is required before location can be verified.",
    STALE_LOCATION: "The GPS capture is too old — manual review required.",
  };
  if (!labels[status]) return null;
  return <div role="status" className={`mt-3 rounded-lg px-3 py-2 text-sm ${status === "VERIFIED" ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-900"}`}>
    <p>{labels[status]}</p>
    {typeof evidence.distanceMeters === "number" && Number.isFinite(evidence.distanceMeters) && <p className="mt-1 text-xs">Distance: {Math.round(evidence.distanceMeters)} m{typeof evidence.geofenceRadiusMeters === "number" && ` · permitted radius: ${evidence.geofenceRadiusMeters} m`}</p>}
  </div>;
}
