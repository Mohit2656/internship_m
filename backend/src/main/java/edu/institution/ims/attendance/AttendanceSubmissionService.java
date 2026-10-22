package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;

/** Shared persistence path after the existing attendance validations have completed. */
@Service
public class AttendanceSubmissionService {
    private final AttendanceRepository attendance;
    private final AttendancePolicyService policies;
    private final LateAttendancePermissionService permissions;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AttendanceSubmissionService(AttendanceRepository attendance, AttendancePolicyService policies,
            LateAttendancePermissionService permissions, JdbcTemplate jdbc, Clock clock) {
        this.attendance = attendance;
        this.policies = policies;
        this.permissions = permissions;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // The caller holds the student lock, shared with mentor permission changes.
    @Transactional(propagation = Propagation.MANDATORY)
    public AttendanceRecord record(User student, InternshipDetails internship, LocalDate date,
            VerificationStatus status, AttendanceSubmissionType type) {
        policies.requireSubmissionOpen(student.getId(), date);
        boolean late = AttendanceSubmissionWindow.isClosed(date, clock);
        LateAttendancePermission permission = late ? permissions.activePermission(student.getId(), date)
                .orElseThrow(() -> AttendanceService.error(HttpStatus.CONFLICT, "ATTENDANCE_WINDOW_CLOSED",
                        "Late attendance permission is no longer active")) : null;
        AttendanceRecord record = attendance.findByStudentIdAndAttendanceDate(student.getId(), date).orElse(null);
        if (record != null) {
            if (!late || permission == null || !record.isAutomaticAbsent() || record.getEvidence() != null
                    || !record.getInternship().getId().equals(internship.getId())) throw duplicate();
            audit(record, "AUTO_ABSENT");
            record.replaceAutomaticAbsence(status, type, clock.instant());
        } else {
            record = new AttendanceRecord(student, internship, date, AttendanceResult.PRESENT,
                    status, type, clock.instant());
        }
        record.useLivenessVerification();
        if (permission != null) record.recordLatePermission(permission.getAuthorizedBy(), permission.getAuthorizedAt());
        try {
            attendance.saveAndFlush(record);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
        if (late) audit(record, "LATE_SUBMISSION");
        return record;
    }

    private void audit(AttendanceRecord record, String event) {
        jdbc.update("insert into attendance_submission_audit "
                + "(attendance_id,event_type,event_at,attendance_result,verification_status,verification_basis,"
                + "submission_type,authorized_by,authorized_at) values (?,?,?,?,?,?,?,?,?)",
                record.getId(), event, Timestamp.from(record.getServerSubmittedAt()),
                record.getAttendanceResult().name(), record.getVerificationStatus().name(),
                record.getVerificationBasis().name(), record.getSubmissionType().name(),
                record.getLateAuthorizedBy() == null ? null : record.getLateAuthorizedBy().getId(),
                record.getLateAuthorizedAt() == null ? null : Timestamp.from(record.getLateAuthorizedAt()));
    }

    private static AttendanceException duplicate() {
        return AttendanceService.error(HttpStatus.CONFLICT, "ATTENDANCE_ALREADY_SUBMITTED",
                "Today's attendance has already been submitted");
    }
}
