package edu.institution.ims.attendance;

import java.time.*;
import java.util.List;

public record TodayAttendanceResponse(
        LocalDate attendanceDate,
        AttendanceResponse attendance,
        Instant submissionDeadlineAt,
        boolean submissionOpen,
        String attendanceZone,
        boolean latePermissionEnabled,
        AttendanceLifecycleStatus lifecycleStatus,
        List<AttendanceResponse> todaySubmissions,
        int submissionsCount,
        int maxDailySubmissions
) {
    public TodayAttendanceResponse(LocalDate attendanceDate, AttendanceResponse attendance,
            Instant submissionDeadlineAt, boolean submissionOpen, String attendanceZone, boolean latePermissionEnabled,
            AttendanceLifecycleStatus lifecycleStatus) {
        this(attendanceDate, attendance, submissionDeadlineAt, submissionOpen, attendanceZone, latePermissionEnabled,
                lifecycleStatus, attendance == null || attendance.automaticAbsent() ? List.of() : List.of(attendance),
                attendance == null || attendance.automaticAbsent() ? 0 : 1, 2);
    }

    public TodayAttendanceResponse(LocalDate attendanceDate, AttendanceResponse attendance,
            Instant submissionDeadlineAt, boolean submissionOpen, String attendanceZone, boolean latePermissionEnabled) {
        this(attendanceDate, attendance, submissionDeadlineAt, submissionOpen, attendanceZone, latePermissionEnabled, null);
    }
}

