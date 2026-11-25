package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.internship.InternshipDetailsRepository;
import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.user.User;
import edu.institution.ims.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class AttendanceExceptionService {
    private static final Logger log = LoggerFactory.getLogger(AttendanceExceptionService.class);

    private final AttendanceDailyExceptionRepository exceptions;
    private final InternshipDetailsRepository internships;
    private final AttendanceRepository attendance;
    private final UserRepository users;
    private final MentorStudentAssignmentRepository mentorAssignments;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired(required = false)
    private edu.institution.ims.notification.MentorNotificationService mentorNotifications;

    public AttendanceExceptionService(AttendanceDailyExceptionRepository exceptions,
                                     InternshipDetailsRepository internships,
                                     AttendanceRepository attendance,
                                     UserRepository users,
                                     MentorStudentAssignmentRepository mentorAssignments,
                                     JdbcTemplate jdbc,
                                     Clock clock) {
        this.exceptions = exceptions;
        this.internships = internships;
        this.attendance = attendance;
        this.users = users;
        this.mentorAssignments = mentorAssignments;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public AttendanceExceptionResponse requestHoliday(Long studentId, Long internshipId, LocalDate date, String reason) {
        if (reason == null || reason.trim().isBlank()) {
            throw new AttendanceException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "A short reason for the company holiday is required.");
        }
        InternshipDetails internship = getStudentInternship(studentId, internshipId);
        if (internship.getStartDate() != null && date.isBefore(internship.getStartDate())) {
            throw new AttendanceException(HttpStatus.BAD_REQUEST, "DATE_BEFORE_START", "Holiday date cannot be before internship start date.");
        }
        if (internship.getEndDate() != null && date.isAfter(internship.getEndDate())) {
            throw new AttendanceException(HttpStatus.BAD_REQUEST, "DATE_AFTER_END", "Holiday date cannot be after internship end date.");
        }

        // Prevent ordinary students from retroactively changing finalized attendance to HOLIDAY without authorized correction
        LocalDate today = LocalDate.now(clock);
        var existingAttendance = attendance.findByStudentIdAndInternshipIdAndAttendanceDate(studentId, internship.getId(), date);
        if (existingAttendance.isPresent()) {
            AttendanceRecord record = existingAttendance.get();
            if (record.getAttendanceResult() == AttendanceResult.PRESENT || record.getVerificationStatus() == VerificationStatus.VERIFIED) {
                throw new AttendanceException(HttpStatus.CONFLICT, "ATTENDANCE_ALREADY_RECORDED",
                        "Attendance is already marked as PRESENT for this date. Contact your faculty mentor for an authorized correction.");
            }
            if (record.isAutomaticAbsent() && date.isBefore(today)) {
                throw new AttendanceException(HttpStatus.CONFLICT, "ATTENDANCE_FINALIZED",
                        "Attendance has already been finalized as absent for this past date. Contact your faculty mentor for an authorized correction.");
            }
        }

        // Prevent duplicate requests for the same internship/date
        if (exceptions.existsByInternshipIdAndExceptionDateAndExceptionType(internship.getId(), date, AttendanceExceptionType.COMPANY_HOLIDAY)) {
            throw new AttendanceException(HttpStatus.CONFLICT, "DUPLICATE_EXCEPTION_REQUEST",
                    "A holiday exception request already exists for this internship and date.");
        }

        User student = users.findById(studentId).orElseThrow();
        AttendanceDailyException entity = new AttendanceDailyException(
                internship, student, date, AttendanceExceptionType.COMPANY_HOLIDAY, reason.trim(), AttendanceExceptionStatus.PENDING);
        entity = exceptions.saveAndFlush(entity);

        audit(entity.getId(), "REQUESTED", studentId, null, "PENDING", "Student submitted company holiday request");

        if (mentorNotifications != null) {
            try {
                String eventKey = "holiday-req-" + entity.getId();
                String studentDisplayName = student.getStudentProfile() != null && student.getStudentProfile().getStudentName() != null ? student.getStudentProfile().getStudentName() : student.getEmail();
                mentorNotifications.workflowSubmitted(studentId, eventKey, "COMPANY_HOLIDAY_REQUESTED",
                        "Company Holiday Request (" + studentDisplayName + ")",
                        "Student " + studentDisplayName + " reported a company holiday for " + date + ": " + reason.trim(),
                        "/mentor/attendance", java.time.Instant.now(clock));
            } catch (Exception e) {
                log.warn("Failed to notify mentor of holiday request: {}", e.getMessage());
            }
        }

        return AttendanceExceptionResponse.from(entity);
    }

    @Transactional
    public AttendanceDailyException recordOrUpdateExtraWorkday(Long studentId, Long internshipId, LocalDate date, String reason, boolean approved) {
        InternshipDetails internship = getStudentInternship(studentId, internshipId);
        Optional<AttendanceDailyException> existing = exceptions.findByInternshipIdAndExceptionDateAndExceptionType(
                internship.getId(), date, AttendanceExceptionType.EXTRA_WORKDAY);
        if (existing.isPresent()) {
            AttendanceDailyException entity = existing.get();
            if (approved && entity.getStatus() != AttendanceExceptionStatus.APPROVED) {
                entity.review(null, AttendanceExceptionStatus.APPROVED, "Auto-approved upon attendance submission");
                exceptions.saveAndFlush(entity);
                audit(entity.getId(), "AUTO_APPROVED", studentId, "PENDING", "APPROVED", "Approved on attendance submission");
            }
            return entity;
        }

        User student = users.findById(studentId).orElseThrow();
        AttendanceDailyException entity = new AttendanceDailyException(
                internship, student, date, AttendanceExceptionType.EXTRA_WORKDAY,
                reason != null ? reason.trim() : "Working today (extra work)",
                approved ? AttendanceExceptionStatus.APPROVED : AttendanceExceptionStatus.PENDING);
        entity = exceptions.saveAndFlush(entity);
        audit(entity.getId(), approved ? "AUTO_APPROVED" : "REQUESTED", studentId, null, entity.getStatus().name(), "Extra workday record created");
        return entity;
    }

    @Transactional
    public AttendanceExceptionResponse review(Long reviewerUserId, Long exceptionId, AttendanceExceptionStatus newStatus, String remarks) {
        AttendanceDailyException entity = exceptions.findById(exceptionId)
                .orElseThrow(() -> new AttendanceException(HttpStatus.NOT_FOUND, "EXCEPTION_NOT_FOUND", "Attendance exception record not found."));

        User reviewer = users.findById(reviewerUserId)
                .orElseThrow(() -> new AttendanceException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Reviewer user not found."));

        String oldStatus = entity.getStatus().name();
        entity.review(reviewer, newStatus, remarks != null ? remarks.trim() : null);
        exceptions.saveAndFlush(entity);

        // If an approved holiday exception was approved, purge any auto-absence record on that date
        if (newStatus == AttendanceExceptionStatus.APPROVED && entity.getExceptionType() == AttendanceExceptionType.COMPANY_HOLIDAY) {
            var autoAbsent = attendance.findByStudentIdAndInternshipIdAndAttendanceDate(
                    entity.getStudent().getId(), entity.getInternship().getId(), entity.getExceptionDate());
            if (autoAbsent.isPresent() && autoAbsent.get().isAutomaticAbsent()) {
                attendance.delete(autoAbsent.get());
                log.info("Purged auto-absence record on approved holiday date {} for internship {}",
                        entity.getExceptionDate(), entity.getInternship().getId());
            }
        }

        audit(entity.getId(), "REVIEWED", reviewerUserId, oldStatus, newStatus.name(), remarks);
        return AttendanceExceptionResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<AttendanceExceptionResponse> getExceptionsForStudent(Long studentId, Long internshipId) {
        if (internshipId != null) {
            return exceptions.findAllByInternshipIdOrderByExceptionDateDesc(internshipId).stream()
                    .map(AttendanceExceptionResponse::from).toList();
        }
        return exceptions.findAllByStudentIdOrderByExceptionDateDesc(studentId).stream()
                .map(AttendanceExceptionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceExceptionResponse> getExceptionsForMentor(Long mentorUserId) {
        List<Long> assignedStudentIds = mentorAssignments.findAllByMentorIdAndActiveTrueOrderByStudentEmailAsc(mentorUserId)
                .stream().map(a -> a.getStudent().getId()).toList();
        if (assignedStudentIds.isEmpty()) return List.of();

        List<InternshipDetails> studentInternships = internships.findAllByOnboardingStudentIdIn(assignedStudentIds);
        List<Long> internshipIds = studentInternships.stream().map(InternshipDetails::getId).toList();
        if (internshipIds.isEmpty()) return List.of();

        return exceptions.findAllByInternshipIdInOrderByExceptionDateDesc(internshipIds).stream()
                .map(AttendanceExceptionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceExceptionResponse> getAllExceptionsForAdmin() {
        return exceptions.findAll().stream()
                .map(AttendanceExceptionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public boolean isApprovedHoliday(Long internshipId, LocalDate date) {
        if (internshipId == null || date == null) return false;
        return exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                internshipId, date, AttendanceExceptionType.COMPANY_HOLIDAY, AttendanceExceptionStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public boolean isApprovedExtraWorkday(Long internshipId, LocalDate date) {
        if (internshipId == null || date == null) return false;
        return exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                internshipId, date, AttendanceExceptionType.EXTRA_WORKDAY, AttendanceExceptionStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceDailyException> findHoliday(Long internshipId, LocalDate date) {
        if (internshipId == null || date == null) return Optional.empty();
        return exceptions.findByInternshipIdAndExceptionDateAndExceptionType(
                internshipId, date, AttendanceExceptionType.COMPANY_HOLIDAY);
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceDailyException> findExtraWorkday(Long internshipId, LocalDate date) {
        if (internshipId == null || date == null) return Optional.empty();
        return exceptions.findByInternshipIdAndExceptionDateAndExceptionType(
                internshipId, date, AttendanceExceptionType.EXTRA_WORKDAY);
    }

    private InternshipDetails getStudentInternship(Long studentId, Long internshipId) {
        if (internshipId != null) {
            return internships.findByIdAndOnboardingStudentId(internshipId, studentId)
                    .orElseThrow(() -> new AttendanceException(HttpStatus.NOT_FOUND, "INTERNSHIP_NOT_FOUND", "Internship not found"));
        }
        return internships.findByOnboardingStudentId(studentId)
                .orElseThrow(() -> new AttendanceException(HttpStatus.NOT_FOUND, "INTERNSHIP_NOT_FOUND", "Internship not found"));
    }

    private void audit(Long exceptionId, String action, Long actorUserId, String prevStatus, String newStatus, String remarks) {
        if (jdbc == null) return;
        try {
            jdbc.update("INSERT INTO attendance_exception_audit (exception_id, action, actor_user_id, previous_status, new_status, remarks, occurred_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    exceptionId, action, actorUserId, prevStatus, newStatus, remarks, java.time.Instant.now(clock));
        } catch (Exception e) {
            log.warn("Failed to write attendance exception audit: {}", e.getMessage());
        }
    }
}
