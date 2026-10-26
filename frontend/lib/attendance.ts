export type AttendanceResult = "PRESENT" | "ABSENT";
export type VerificationStatus = "UNVERIFIED" | "VERIFIED";
export type LivenessStatus = "VERIFIED" | "FAILED" | "NOT_PERFORMED" | "HISTORICAL" | "NOT_APPLICABLE";
export type AttendanceSubmissionType = "BASIC" | "PHYSICAL_CAPTURE" | "REMOTE_CAPTURE" | "AUTO_ABSENT";
export type LocationVerificationStatus = "VERIFIED" | "OUTSIDE_RADIUS" | "INSUFFICIENT_ACCURACY" | "LOCATION_UNAVAILABLE" | "WORKPLACE_NOT_APPROVED" | "STALE_LOCATION" | "NOT_APPLICABLE";
export type AttendanceLocationEvidence = {
  latitude: number | null; longitude: number | null; gpsAccuracyMeters?: number | null;
  locationCheckPassed?: boolean | null; expectedLatitude: number | null; expectedLongitude: number | null;
  distanceMeters: number | null; geofenceRadiusMeters: number | null; workplaceAddress: string | null;
  verificationReason: string | null; locationVerificationStatus?: LocationVerificationStatus | null;
  locationCapturedAt?: string | null; workplaceRevision?: number | null;
};

export function locationVerificationLabel(status?: LocationVerificationStatus | null) {
  const labels: Record<LocationVerificationStatus, string> = {
    VERIFIED: "Workplace location verified",
    OUTSIDE_RADIUS: "Outside permitted workplace radius",
    INSUFFICIENT_ACCURACY: "Insufficient GPS accuracy",
    LOCATION_UNAVAILABLE: "Location unavailable",
    WORKPLACE_NOT_APPROVED: "Workplace approval required",
    STALE_LOCATION: "GPS capture too old",
    NOT_APPLICABLE: "Not applicable",
  };
  return status ? labels[status] ?? "Not assessed" : "Not assessed";
}

export type Attendance = {
  id: number;
  internshipId: number;
  attendanceDate: string;
  attendanceResult: AttendanceResult;
  verificationStatus: VerificationStatus;
  livenessStatus: LivenessStatus;
  verificationBasis: "LEGACY" | "LIVENESS" | "AUTOMATIC";
  automaticAbsent?: boolean;
  submittedLate?: boolean;
  submissionType: AttendanceSubmissionType;
  serverSubmittedAt: string;
  evidence?: AttendanceLocationEvidence | null;
};

export function attendanceLabel(attendance: Attendance) {
  if (attendance.attendanceResult !== "PRESENT") return "Absent";
  return `Present — ${livenessLabel(attendance.livenessStatus)}`;
}

export function isAutomaticAbsence(attendance: Attendance) {
  return attendance.automaticAbsent === true || attendance.submissionType === "AUTO_ABSENT";
}

export function livenessLabel(status: LivenessStatus | undefined) {
  switch (status) {
    case "VERIFIED": return "Liveness verified";
    case "FAILED": return "Liveness not confirmed";
    case "NOT_PERFORMED": return "Liveness not performed";
    case "NOT_APPLICABLE": return "Not applicable — automatic absence";
    default: return "Historical attendance — liveness not reassessed";
  }
}

export type TodayAttendance = {
  attendanceDate: string;
  attendance: Attendance | null;
  submissionDeadlineAt?: string;
  submissionOpen?: boolean;
  attendanceZone?: string;
  latePermissionEnabled?: boolean;
};

export function attendanceSubmissionOpen(today: TodayAttendance | null, now = Date.now()) {
  if (today?.submissionOpen === false) return false;
  if (today?.latePermissionEnabled) {
    const parts = new Intl.DateTimeFormat("en-CA", { timeZone: today.attendanceZone ?? "UTC", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date(now));
    const part = (type: string) => parts.find(value => value.type === type)?.value;
    return `${part("year")}-${part("month")}-${part("day")}` === today.attendanceDate;
  }
  return !today?.submissionDeadlineAt || now < Date.parse(today.submissionDeadlineAt);
}

export type LateAttendancePermission = {
  studentUserId: number;
  studentName: string | null;
  uid: string | null;
  attendanceDate: string;
  enabled: boolean;
  authorizedBy: number | null;
  authorizedAt: string | null;
  attendanceZone: string;
};

export type MentorStudentAttendance = {
  studentUserId: number;
  name: string | null;
  uid: string | null;
  branchLabel: string | null;
  semester: number | null;
  companyName: string | null;
  internshipMode: string | null;
  internshipModeLabel: string | null;
  todayWorkMode: HybridWorkMode | null;
  todayAttendance: Attendance | null;
};

export type HybridWorkMode = "OFFICE" | "REMOTE";
export type AttendanceVerificationMethod = "PHYSICAL" | "REMOTE" | "BASIC" | "UNAVAILABLE";

export type AttendancePolicy = {
  internshipMode: "OFFICE_REPORTING" | "HYBRID" | "ONLINE" | "COLLEGE_REPORTING";
  todayWorkMode: HybridWorkMode | null;
  verificationMethod: AttendanceVerificationMethod;
  attendanceAlreadySubmitted: boolean;
  configurationReady: boolean;
  serverDate: string;
};

export type HybridScheduleEntry = { dayOfWeek: string; workMode: HybridWorkMode };
export type HybridSchedule = { internshipId: number; entries: HybridScheduleEntry[] };

export type AiChallenge = "BLINK" | "TURN_HEAD_LEFT" | "TURN_HEAD_RIGHT";
export type AiVerificationSession = {
  sessionId: string;
  challenge: AiChallenge;
  expiresAt: string;
};
export type AiVerificationEvidence = {
  sessionId: string;
  challenge: AiChallenge;
  faceCount: number;
  qualityPassed: boolean;
  livenessPassed: boolean;
  modelVersion: string;
};

export type InternshipLocationStatus = "PENDING" | "CONFIRMED" | "APPROVED" | "REJECTED";

export type InternshipLocation = {
  id: number;
  internshipId: number;
  latitude: number;
  longitude: number;
  geofenceRadiusMeters: number;
  status: InternshipLocationStatus;
  createdAt: string;
  updatedAt: string;
  confirmedAt: string | null;
  confirmedBy: number | null;
  placeId: string | null;
  workplaceAddress: string | null;
  workplaceName: string | null;
  companyName: string;
  revision: number; mentorDecision: "APPROVE" | "REJECT" | null; mentorReviewerId: number | null; mentorReviewedAt: string | null; mentorRemarks: string | null; iiicDecision: "APPROVE" | "REJECT" | null; iiicRemarks: string | null;
};
