package edu.institution.ims.attendance;

import java.time.Instant;
import java.time.LocalDate;

public record LateAttendancePermissionResponse(Long studentUserId, String studentName, String uid,
        LocalDate attendanceDate, boolean enabled, Long authorizedBy, Instant authorizedAt,
        String attendanceZone) {}
