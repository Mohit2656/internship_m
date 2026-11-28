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
    private final AttendanceDailyExceptionRepository exceptions;

    @org.springframework.beans.factory.annotation.Autowired
    public AttendancePolicyService(HybridWorkScheduleRepository schedules,
            InternshipDetailsRepository internships, AttendanceRepository attendance, Clock clock,
            LateAttendancePermissionService permissions, AttendanceLocationVerificationService locationVerification,
            AttendanceDailyExceptionRepository exceptions) {
        this.schedules = schedules;
        this.internships = internships;
        this.attendance = attendance;
        this.clock = clock;
        this.permissions = permissions;
        this.locationVerification = locationVerification;
        this.exceptions = exceptions;
    }

    public AttendancePolicyService(HybridWorkScheduleRepository schedules,
            InternshipDetailsRepository internships, AttendanceRepository attendance, Clock clock,
            LateAttendancePermissionService permissions, AttendanceLocationVerificationService locationVerification) {
        this(schedules, internships, attendance, clock, permissions, locationVerification, null);
    }

    @Transactional(readOnly = true)
    public AttendancePolicyResponse today(UserPrincipal principal) {
        return today(principal,null);
    }
    @Transactional(readOnly = true)
    public AttendancePolicyResponse today(UserPrincipal principal,Long internshipId) {
        InternshipDetails internship = eligibleInternship(principal.id(),internshipId);
        LocalDate today = InternshipLifecycle.today(clock);
        ResolvedAttendancePolicy policy = resolve(internship, today);
        AttendanceLifecycleStatus lifecycle = lifecycle(internship, today);

        boolean isWorkingDay = internship.isWorkingDay(today);
        boolean approvedHoliday = exceptions != null && exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                internship.getId(), today, AttendanceExceptionType.COMPANY_HOLIDAY, AttendanceExceptionStatus.APPROVED);
        boolean pendingHoliday = exceptions != null && exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                internship.getId(), today, AttendanceExceptionType.COMPANY_HOLIDAY, AttendanceExceptionStatus.PENDING);
        boolean approvedExtraWorkday = exceptions != null && exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                internship.getId(), today, AttendanceExceptionType.EXTRA_WORKDAY, AttendanceExceptionStatus.APPROVED);

        boolean scheduledWorkingDay = isWorkingDay && !approvedHoliday;
        boolean scheduledDayOff = !isWorkingDay || approvedHoliday;

        long activeSubmissionsToday = attendance.findAllByStudentIdAndInternshipIdAndAttendanceDateOrderByActiveSlotAscIdAsc(
                        principal.id(), internship.getId(), today).stream()
                .filter(record -> !canReplaceAutomaticAbsence(record, principal.id(), internship.getId(), today))
                .count();
        return new AttendancePolicyResponse(internship.getInternshipMode(), policy.todayWorkMode(),
                policy.verificationMethod(), activeSubmissionsToday >= AttendanceAbsenceService.DAILY_SUBMISSION_SLOTS,
                policy.configurationReady(), locationVerification.workplaceStatus(internship, policy.verificationMethod()),
                lifecycle, internship.getStartDate(), internship.getEndDate(), lifecycle == AttendanceLifecycleStatus.ACTIVE,
                today, locationVerification.maxGpsAccuracyMeters(),
                scheduledWorkingDay, scheduledDayOff, approvedHoliday, pendingHoliday, approvedExtraWorkday,
                internship.getEffectiveWorkingDaysPerWeek(), internship.getWorkingDaysList());
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

    public boolean hasConfiguredHybridSchedule(Long internshipId) {
        if (internshipId == null) return false;
        if (!schedules.findAllByInternshipId(internshipId).isEmpty()) return true;
        for (DayOfWeek day : DayOfWeek.values()) {
            if (schedules.findByInternshipIdAndDayOfWeek(internshipId, day).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public boolean isTrackedWorkingDayPolicy(InternshipDetails internship, LocalDate date) {
        if (internship == null || date == null) return false;
        if (resolve(internship, date).configurationReady()) return true;
        return internship.getInternshipMode() == InternshipMode.HYBRID
                && !hasConfiguredHybridSchedule(internship.getId());
    }

    public void requireSubmissionOpen(LocalDate date) {
        if (AttendanceSubmissionWindow.isClosed(date, clock)) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_WINDOW_CLOSED",
                    "Attendance submission for that date has closed after midnight");
        }
    }

    public void requireSubmissionOpen(Long studentId, Long internshipId, LocalDate date) {
        if (!date.equals(InternshipLifecycle.today(clock))) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_DATE_CHANGED",
                    "The attendance date has changed. Refresh today's attendance and try again");
        }
        if (AttendanceSubmissionWindow.isClosed(date, clock)) {
            requireSubmissionOpen(date);
        }
    }

    public void requireSubmissionOpen(Long studentId, LocalDate date) {
        requireSubmissionOpen(studentId, null, date);
    }

    public AttendanceLifecycleStatus lifecycle(InternshipDetails internship, LocalDate date) {
        if (internship == null || internship.getStartDate() == null || internship.getEndDate() == null) {
            return AttendanceLifecycleStatus.NOT_STARTED;
        }
        if (internship.isDeleted() || internship.isEarlyTerminated()) {
            return AttendanceLifecycleStatus.ENDED;
        }
        if (internship.getOnboarding() == null || internship.getOnboarding().getWorkflowStatus() != WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE) {
            return AttendanceLifecycleStatus.NOT_STARTED;
        }
        if ("COMPLETED".equals(internship.getCompletionStatus())) return AttendanceLifecycleStatus.COMPLETED;
        if (date.isBefore(internship.getStartDate())) return AttendanceLifecycleStatus.NOT_STARTED;
        if (date.isAfter(internship.getEndDate())) return AttendanceLifecycleStatus.ENDED;
        return AttendanceLifecycleStatus.ACTIVE;
    }

    public void requireAttendanceActive(InternshipDetails internship, LocalDate date) {
        AttendanceLifecycleStatus status = lifecycle(internship, date);
        if (status != AttendanceLifecycleStatus.ACTIVE) {
            String message = switch (status) {
                case NOT_STARTED -> "Attendance tracking starts on " + internship.getStartDate();
                case ENDED -> "Attendance tracking ended on " + internship.getEndDate();
                case COMPLETED -> "Attendance tracking is closed because this internship is completed";
                default -> "Attendance is unavailable for this internship";
            };
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_OUTSIDE_INTERNSHIP_LIFECYCLE", message);
        }
    }

    boolean canReplaceAutomaticAbsence(AttendanceRecord record, Long studentId, Long internshipId, LocalDate date) {
        return record.isAutomaticAbsent() && record.getEvidence() == null
                && date.equals(record.getAttendanceDate());
    }

    boolean canReplaceAutomaticAbsence(AttendanceRecord record, Long studentId, LocalDate date) {
        return canReplaceAutomaticAbsence(record, studentId, null, date);
    }

    InternshipDetails eligibleInternship(Long studentId) {
        return eligibleInternship(studentId,null);
    }
    InternshipDetails eligibleInternship(Long studentId,Long internshipId) {
        InternshipDetails internship = (internshipId==null?internships.findByOnboardingStudentId(studentId):internships.findByIdAndOnboardingStudentId(internshipId,studentId))
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
