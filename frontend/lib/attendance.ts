export type AttendanceResult = "PRESENT" | "ABSENT";
export type VerificationStatus = "UNVERIFIED" | "VERIFIED";
export type AttendanceSubmissionType = "BASIC" | "PHYSICAL_CAPTURE" | "REMOTE_CAPTURE";

export type Attendance = {
  id: number;
  internshipId: number;
  attendanceDate: string;
  attendanceResult: AttendanceResult;
  verificationStatus: VerificationStatus;
  submissionType: AttendanceSubmissionType;
  serverSubmittedAt: string;
  evidence?: { latitude: number | null; longitude: number | null; gpsAccuracyMeters?: number | null; locationCheckPassed?: boolean | null; expectedLatitude: number | null; expectedLongitude: number | null; distanceMeters: number | null; geofenceRadiusMeters: number | null; workplaceAddress: string | null; verificationReason: string | null } | null;
};

export function attendanceLabel(attendance: Attendance) {
  if (attendance.attendanceResult !== "PRESENT") return "Absent";
  return attendance.verificationStatus === "VERIFIED" ? "Present — Verified" : "Present — Unverified";
}

export type TodayAttendance = {
  attendanceDate: string;
  attendance: Attendance | null;
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
