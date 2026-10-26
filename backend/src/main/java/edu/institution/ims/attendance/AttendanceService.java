package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;

@Service
public class AttendanceService {
    private final AttendanceRepository attendance;
    private final InternshipDetailsRepository internships;
    private final UserRepository users;
    private final AttendancePolicyService policies;
    private final Clock clock;
    private final AttendanceEvidenceRepository evidence;
    private final AttendanceSubmissionService submissions;
    private final LateAttendancePermissionService permissions;
    private final AttendanceLocationVerificationService locationVerification;

    public AttendanceService(AttendanceRepository attendance, InternshipDetailsRepository internships,
            UserRepository users, AttendancePolicyService policies, Clock clock, AttendanceEvidenceRepository evidence,
            AttendanceSubmissionService submissions, LateAttendancePermissionService permissions,
            AttendanceLocationVerificationService locationVerification) {
        this.attendance = attendance;
        this.internships = internships;
        this.users = users;
        this.policies = policies;
        this.clock = clock;
        this.evidence = evidence;
        this.submissions = submissions;
        this.permissions = permissions;
        this.locationVerification = locationVerification;
    }

    @Transactional
    public AttendanceResponse submitToday(UserPrincipal principal) {
        return submitToday(principal, null, null, null);
    }

    @Transactional
    public AttendanceResponse submitToday(UserPrincipal principal, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy) {
        return submitToday(principal, latitude, longitude, accuracy, null);
    }

    @Transactional
    public AttendanceResponse submitToday(UserPrincipal principal, BigDecimal latitude, BigDecimal longitude,
            BigDecimal accuracy, Instant locationCapturedAt) {
        User student = users.findByIdForAttendanceUpdate(principal.id()).filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "Only a student may submit attendance"));
        InternshipDetails internship = eligibleInternship(student.getId());
        LocalDate today = LocalDate.now(clock);
        requireWithinInternship(internship, today);
        policies.requireSubmissionOpen(student.getId(), today);
        AttendanceVerificationMethod method = policies.resolve(internship, today).verificationMethod();
        if (method == AttendanceVerificationMethod.PHYSICAL || method == AttendanceVerificationMethod.REMOTE) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_EVIDENCE_REQUIRED",
                    "Use today's required attendance evidence flow");
        }
        SubmissionLocation location = SubmissionLocation.require(latitude, longitude, accuracy);
        locationVerification.validateCaptureTime(locationCapturedAt);
        AttendanceRecord record = submissions.record(student, internship, today,
                VerificationStatus.UNVERIFIED, AttendanceSubmissionType.BASIC);
        locationVerification.save(new AttendanceEvidence(record, location, clock.instant()), internship,
                location, locationCapturedAt, true, accuracy);
        return response(record);
    }

    @Transactional(readOnly = true)
    public TodayAttendanceResponse today(UserPrincipal principal) {
        LocalDate today = LocalDate.now(clock);
        boolean latePermission = permissions.isEnabled(principal.id(), today);
        return new TodayAttendanceResponse(today,
                attendance.findByStudentIdAndAttendanceDate(principal.id(), today)
                        .map(AttendanceService::response).orElse(null),
                AttendanceSubmissionWindow.deadline(today, clock),
                !AttendanceSubmissionWindow.isClosed(today, clock) || latePermission,
                clock.getZone().getId(), latePermission);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> history(UserPrincipal principal) {
        return attendance.findAllByStudentIdOrderByAttendanceDateDescServerSubmittedAtDesc(principal.id())
                .stream().map(AttendanceService::response).toList();
    }

    private InternshipDetails eligibleInternship(Long studentUserId) {
        InternshipDetails internship = internships.findByOnboardingStudentId(studentUserId)
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "ATTENDANCE_NOT_ELIGIBLE",
                        "Complete internship registration before submitting attendance"));
        if (internship.getOnboarding().getWorkflowStatus() != WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_NOT_ELIGIBLE",
                    "Complete internship registration before submitting attendance");
        }
        return internship;
    }

    private static void requireWithinInternship(InternshipDetails internship, LocalDate date) {
        if (date.isBefore(internship.getStartDate()) || date.isAfter(internship.getEndDate())) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_OUTSIDE_INTERNSHIP_PERIOD",
                    "Attendance is available only during the registered internship period");
        }
    }

    public static AttendanceResponse response(AttendanceRecord record) {
        return new AttendanceResponse(record.getId(), record.getInternship().getId(), record.getAttendanceDate(),
                record.getAttendanceResult(), record.getVerificationStatus(), record.getSubmissionType(),
                record.getServerSubmittedAt(), AttendanceEvidenceResponse.from(record.getEvidence()),
                record.getVerificationBasis(), record.getLivenessStatus(), record.isAutomaticAbsent(),
                record.isSubmittedLate());
    }

    static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
