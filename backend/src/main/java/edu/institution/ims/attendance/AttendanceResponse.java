package edu.institution.ims.attendance;

import java.time.*;

public record AttendanceResponse(
        Long id,
        Long internshipId,
        LocalDate attendanceDate,
        AttendanceResult attendanceResult,
        VerificationStatus verificationStatus,
        AttendanceSubmissionType submissionType,
        Instant serverSubmittedAt
) {}
