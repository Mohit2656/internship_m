package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class AttendancePolicyService {
    private final HybridWorkScheduleRepository schedules;
    private final InternshipDetailsRepository internships;
    private final AttendanceRepository attendance;
    private final Clock clock;
    private final LateAttendancePermissionService permissions;
    private final AttendanceLocationVerificationService locationVerification;

    public AttendancePolicyService(HybridWorkScheduleRepository schedules,
            InternshipDetailsRepository internships, AttendanceRepository attendance, Clock clock,
            LateAttendancePermissionService permissions, AttendanceLocationVerificationService locationVerification) {
        this.schedules = schedules;
        this.internships = internships;
        this.attendance = attendance;
        this.clock = clock;
        this.permissions = permissions;
        this.locationVerification = locationVerification;
    }

    @Transactional(readOnly = true)
    public AttendancePolicyResponse today(UserPrincipal principal) {
        InternshipDetails internship = eligibleInternship(principal.id());
        LocalDate today = LocalDate.now(clock);
        ResolvedAttendancePolicy policy = resolve(internship, today);
        return new AttendancePolicyResponse(internship.getInternshipMode(), policy.todayWorkMode(),
                policy.verificationMethod(), attendance.findByStudentIdAndAttendanceDate(principal.id(), today)
                        .map(record -> !canReplaceAutomaticAbsence(record, principal.id(), today)).orElse(false),
                policy.configurationReady(), today, locationVerification.maxGpsAccuracyMeters());
    }

    public ResolvedAttendancePolicy resolve(InternshipDetails internship, LocalDate date) {
        return switch (internship.getInternshipMode()) {
            case OFFICE_REPORTING -> new ResolvedAttendancePolicy(HybridWorkMode.OFFICE,
                    AttendanceVerificationMethod.PHYSICAL, true);
            case ONLINE -> new ResolvedAttendancePolicy(HybridWorkMode.REMOTE,
                    AttendanceVerificationMethod.REMOTE, true);
            case HYBRID -> schedules.findByInternshipIdAndDayOfWeek(internship.getId(), date.getDayOfWeek())
                    .map(entry -> new ResolvedAttendancePolicy(entry.getWorkMode(),
                            entry.getWorkMode() == HybridWorkMode.OFFICE
                                    ? AttendanceVerificationMethod.PHYSICAL : AttendanceVerificationMethod.REMOTE, true))
                    .orElseGet(() -> new ResolvedAttendancePolicy(null,
                            AttendanceVerificationMethod.UNAVAILABLE, false));
            case COLLEGE_REPORTING -> new ResolvedAttendancePolicy(null,
                    AttendanceVerificationMethod.BASIC, true);
        };
    }

    public void requireSubmissionOpen(LocalDate date) {
        if (AttendanceSubmissionWindow.isClosed(date, clock)) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_WINDOW_CLOSED",
                    "Daily attendance submission closes at 12:00 noon in the attendance time zone");
        }
    }

    public void requireSubmissionOpen(Long studentId, LocalDate date) {
        if (!date.equals(LocalDate.now(clock))) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_DATE_CHANGED",
                    "The attendance date has changed. Refresh today's attendance and try again");
        }
        if (AttendanceSubmissionWindow.isClosed(date, clock) && !permissions.isEnabled(studentId, date)) {
            requireSubmissionOpen(date);
        }
    }

    boolean canReplaceAutomaticAbsence(AttendanceRecord record, Long studentId, LocalDate date) {
        return record.isAutomaticAbsent() && record.getEvidence() == null
                && date.equals(record.getAttendanceDate()) && permissions.isEnabled(studentId, date);
    }

    InternshipDetails eligibleInternship(Long studentId) {
        InternshipDetails internship = internships.findByOnboardingStudentId(studentId)
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "ATTENDANCE_NOT_ELIGIBLE",
                        "Complete internship registration before submitting attendance"));
        if (internship.getOnboarding().getWorkflowStatus() != WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_NOT_ELIGIBLE",
                    "Complete internship registration before submitting attendance");
        }
        return internship;
    }

    private static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
