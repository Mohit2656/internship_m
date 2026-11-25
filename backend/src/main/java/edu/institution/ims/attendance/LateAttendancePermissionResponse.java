package edu.institution.ims.attendance;

import java.time.Instant;
import java.time.LocalDate;

public record LateAttendancePermissionResponse(Long studentUserId, Long internshipId, int internshipNumber,
        String companyName, String studentName, String uid,
        LocalDate attendanceDate, boolean enabled, Long authorizedBy, Instant authorizedAt,
        String attendanceZone) {
    public LateAttendancePermissionResponse(Long studentUserId, String studentName, String uid,
            LocalDate attendanceDate, boolean enabled, Long authorizedBy, Instant authorizedAt,
            String attendanceZone) {
        this(studentUserId, null, 1, null, studentName, uid, attendanceDate, enabled, authorizedBy, authorizedAt, attendanceZone);
    }
}
