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
        WorkplaceAttendanceStatus workplaceStatus,
        AttendanceLifecycleStatus lifecycleStatus,
        LocalDate internshipStartDate,
        LocalDate internshipEndDate,
        boolean attendanceTrackingActive,
        LocalDate serverDate,
        BigDecimal maxGpsAccuracyMeters,
        boolean scheduledWorkingDay,
        boolean scheduledDayOff,
        boolean approvedHoliday,
        boolean pendingHoliday,
        boolean approvedExtraWorkday,
        Integer workingDaysPerWeek,
        java.util.List<String> workingDays
) {
    public AttendancePolicyResponse(
            InternshipMode internshipMode,
            HybridWorkMode todayWorkMode,
            AttendanceVerificationMethod verificationMethod,
            boolean attendanceAlreadySubmitted,
            boolean configurationReady,
            WorkplaceAttendanceStatus workplaceStatus,
            AttendanceLifecycleStatus lifecycleStatus,
            LocalDate internshipStartDate,
            LocalDate internshipEndDate,
            boolean attendanceTrackingActive,
            LocalDate serverDate,
            BigDecimal maxGpsAccuracyMeters
    ) {
        this(internshipMode, todayWorkMode, verificationMethod, attendanceAlreadySubmitted, configurationReady,
                workplaceStatus, lifecycleStatus, internshipStartDate, internshipEndDate, attendanceTrackingActive,
                serverDate, maxGpsAccuracyMeters, true, false, false, false, false, 5,
                java.util.List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"));
    }
}
