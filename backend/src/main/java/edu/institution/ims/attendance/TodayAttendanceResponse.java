package edu.institution.ims.attendance;

import java.time.*;

public record TodayAttendanceResponse(LocalDate attendanceDate, AttendanceResponse attendance,
        Instant submissionDeadlineAt, boolean submissionOpen, String attendanceZone, boolean latePermissionEnabled) {}
