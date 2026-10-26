package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipMode;
import java.time.LocalDate;
import java.math.BigDecimal;

public record AttendancePolicyResponse(
        InternshipMode internshipMode,
        HybridWorkMode todayWorkMode,
        AttendanceVerificationMethod verificationMethod,
        boolean attendanceAlreadySubmitted,
        boolean configurationReady,
        LocalDate serverDate,
        BigDecimal maxGpsAccuracyMeters
) {}
