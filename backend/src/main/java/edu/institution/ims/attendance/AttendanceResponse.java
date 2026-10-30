package edu.institution.ims.attendance;

import java.time.*;

public record AttendanceResponse(
        Long id,
        Long internshipId,
        LocalDate attendanceDate,
        AttendanceResult attendanceResult,
        VerificationStatus verificationStatus,
        AttendanceSubmissionType submissionType,
        Instant serverSubmittedAt, AttendanceEvidenceResponse evidence,
        AttendanceVerificationBasis verificationBasis, LivenessStatus livenessStatus,
        boolean automaticAbsent, boolean submittedLate, AttendanceReviewResponse review
) {
    public AttendanceResponse(Long id, Long internshipId, LocalDate attendanceDate, AttendanceResult attendanceResult,
            VerificationStatus verificationStatus, AttendanceSubmissionType submissionType, Instant serverSubmittedAt,
            AttendanceEvidenceResponse evidence, AttendanceVerificationBasis verificationBasis, LivenessStatus livenessStatus,
            boolean automaticAbsent, boolean submittedLate) {
        this(id, internshipId, attendanceDate, attendanceResult, verificationStatus, submissionType, serverSubmittedAt,
                evidence, verificationBasis, livenessStatus, automaticAbsent, submittedLate, null);
    }
}
