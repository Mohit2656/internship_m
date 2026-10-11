import type { Attendance } from "@/lib/attendance";
import { LocationMapLink } from "@/components/LocationMapLink";

function verificationReason(evidence: NonNullable<Attendance["evidence"]>) {
  switch (evidence.verificationReason) {
    case "GPS_ACCURACY_TOO_LOW": return "Browser GPS accuracy is too low to verify this location.";
    case "EXPECTED_LOCATION_NOT_CONFIRMED": return "The registered workplace requires approval from both the assigned Faculty Mentor and IIIC Incharge.";
    case "LIVE_LOCATION_UNAVAILABLE": return "Browser GPS was unavailable or permission was denied.";
    case "INVALID_LIVE_LOCATION": return "Submitted browser GPS coordinates or accuracy were invalid.";
    case "OUTSIDE_GEOFENCE": return "The location and its GPS accuracy margin do not fit within the workplace radius.";
    case "VERIFIED_GEOFENCE": return "The approved workplace location and attendance verification checks passed.";
    case "METADATA_CONTRADICTION": return "Camera metadata conflicts with the attendance location or capture time.";
    default: return evidence.verificationReason?.replaceAll("_", " ") || "Verification reason unavailable";
  }
}
export function AttendanceLocations({ evidence }: { evidence: Attendance["evidence"] }) {
  if (!evidence) return <p className="mt-2 text-xs text-slate-500">Location evidence unavailable for this attendance record.</p>;
  return <div className="mt-3 text-xs font-normal text-slate-600"><p>{evidence.workplaceAddress || "Registered workplace address unavailable"}</p><p>{verificationReason(evidence)}</p>{evidence.gpsAccuracyMeters != null && <p>Reported GPS accuracy: {evidence.gpsAccuracyMeters} m</p>}{evidence.locationCheckPassed !== undefined && <p>GPS distance/accuracy check: {evidence.locationCheckPassed === true ? "Passed" : evidence.locationCheckPassed === false ? "Failed" : "Unavailable"}</p>}{evidence.distanceMeters != null && <p>Distance: {evidence.distanceMeters} m; radius: {evidence.geofenceRadiusMeters} m</p>}<div><LocationMapLink latitude={evidence.latitude} longitude={evidence.longitude} label="Attendance GPS map" /></div><div><LocationMapLink latitude={evidence.expectedLatitude} longitude={evidence.expectedLongitude} label="Attendance workplace map" /></div>{evidence.latitude == null || evidence.longitude == null ? <p>Browser GPS unavailable for this attendance.</p> : null}</div>;
}
