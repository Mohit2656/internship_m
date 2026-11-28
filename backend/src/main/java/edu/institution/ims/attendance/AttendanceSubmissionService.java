package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.notification.MentorNotificationService;
import edu.institution.ims.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;

/** Shared persistence path after the existing attendance validations have completed. */
@Service
public class AttendanceSubmissionService {
    private final AttendanceRepository attendance;
    private final AttendancePolicyService policies;
    private final LateAttendancePermissionService permissions;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final MentorNotificationService notifications;
    private final AttendanceExceptionService exceptionService;

    @Autowired
    public AttendanceSubmissionService(AttendanceRepository attendance, AttendancePolicyService policies,
            LateAttendancePermissionService permissions, JdbcTemplate jdbc, Clock clock,
            MentorNotificationService notifications, AttendanceExceptionService exceptionService) {
        this.attendance = attendance;
        this.policies = policies;
        this.permissions = permissions;
        this.jdbc = jdbc;
        this.clock = clock;
        this.notifications = notifications;
        this.exceptionService = exceptionService;
    }

    public AttendanceSubmissionService(AttendanceRepository attendance, AttendancePolicyService policies,
            LateAttendancePermissionService permissions, JdbcTemplate jdbc, Clock clock,
            MentorNotificationService notifications) {
        this(attendance, policies, permissions, jdbc, clock, notifications, null);
    }

    // The caller holds the student lock, shared with mentor permission changes.
    @Transactional(propagation = Propagation.MANDATORY)
    public AttendanceRecord record(User student, InternshipDetails internship, LocalDate date,
            VerificationStatus status, AttendanceSubmissionType type) {
        policies.requireAttendanceActive(internship, date);
        policies.requireSubmissionOpen(student.getId(), internship.getId(), date);

        if (internship.getStartDate() != null && date.isBefore(internship.getStartDate())) {
            throw AttendanceService.error(HttpStatus.CONFLICT, "ATTENDANCE_BEFORE_START",
                    "Attendance date cannot be before internship start date");
        }
        if (internship.getEndDate() != null && date.isAfter(internship.getEndDate())) {
            throw AttendanceService.error(HttpStatus.CONFLICT, "ATTENDANCE_AFTER_END",
                    "Attendance date cannot be after internship end date");
        }

        if (exceptionService != null && exceptionService.isApprovedHoliday(internship.getId(), date)) {
            throw AttendanceService.error(HttpStatus.CONFLICT, "APPROVED_HOLIDAY",
                    "Attendance cannot be submitted on an approved company holiday");
        }

        // Handle extra workday when submitting on a scheduled day off
        if (exceptionService != null && !internship.isWorkingDay(date)) {
            exceptionService.recordOrUpdateExtraWorkday(student.getId(), internship.getId(), date, "Working today (extra work)", true);
        }

        List<AttendanceRecord> todayRecords = attendance.findAllByStudentIdAndInternshipIdAndAttendanceDateOrderByActiveSlotAscIdAsc(
                student.getId(), internship.getId(), date);
        AttendanceRecord replaceableAbsence = todayRecords.stream()
                .filter(r -> r.isAutomaticAbsent() && r.getEvidence() == null && r.getInternship().getId().equals(internship.getId()))
                .findFirst().orElse(null);
        long submittedCount = todayRecords.stream().filter(r -> !r.isAutomaticAbsent()).count();
        AttendanceRecord record;
        if (replaceableAbsence != null) {
            audit(replaceableAbsence, "AUTO_ABSENT");
            replaceableAbsence.replaceAutomaticAbsence(status, type, clock.instant());
            record = replaceableAbsence;
        } else {
            if (submittedCount >= AttendanceAbsenceService.DAILY_SUBMISSION_SLOTS) throw duplicate();
            int nextSlot = todayRecords.stream().anyMatch(r -> Integer.valueOf(1).equals(r.getActiveSlot())) ? 2 : 1;
            record = new AttendanceRecord(student, internship, date, AttendanceResult.PENDING_MENTOR_REVIEW,
                    status, type, clock.instant());
            record.setActiveSlot(nextSlot);
        }
        record.useLivenessVerification();
        try {
            attendance.saveAndFlush(record);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
        return record;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyMentor(AttendanceRecord record) {
        notifications.attendanceSubmitted(record);
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
