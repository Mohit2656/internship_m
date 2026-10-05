package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service
public class AttendanceService {
    private final AttendanceRepository attendance;
    private final InternshipDetailsRepository internships;
    private final UserRepository users;
    private final AttendancePolicyService policies;
    private final Clock clock;

    public AttendanceService(AttendanceRepository attendance, InternshipDetailsRepository internships,
            UserRepository users, AttendancePolicyService policies, Clock clock) {
        this.attendance = attendance;
        this.internships = internships;
        this.users = users;
        this.policies = policies;
        this.clock = clock;
    }

    @Transactional
    public AttendanceResponse submitToday(UserPrincipal principal) {
        User student = users.findById(principal.id()).filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "Only a student may submit attendance"));
        InternshipDetails internship = eligibleInternship(student.getId());
        LocalDate today = LocalDate.now(clock);
        requireWithinInternship(internship, today);
        AttendanceVerificationMethod method = policies.resolve(internship, today).verificationMethod();
        if (method == AttendanceVerificationMethod.PHYSICAL || method == AttendanceVerificationMethod.REMOTE) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_EVIDENCE_REQUIRED",
                    "Use today's required attendance evidence flow");
        }
        if (attendance.findByStudentIdAndAttendanceDate(student.getId(), today).isPresent()) {
            throw duplicate();
        }
        AttendanceRecord record = new AttendanceRecord(student, internship, today,
                AttendanceResult.PRESENT, VerificationStatus.UNVERIFIED,
                AttendanceSubmissionType.BASIC, clock.instant());
        try {
            return response(attendance.saveAndFlush(record));
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    @Transactional(readOnly = true)
    public TodayAttendanceResponse today(UserPrincipal principal) {
        LocalDate today = LocalDate.now(clock);
        return new TodayAttendanceResponse(today,
                attendance.findByStudentIdAndAttendanceDate(principal.id(), today)
                        .map(AttendanceService::response).orElse(null));
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
                record.getServerSubmittedAt());
    }

    private static AttendanceException duplicate() {
        return error(HttpStatus.CONFLICT, "ATTENDANCE_ALREADY_SUBMITTED",
                "Today's attendance has already been submitted");
    }

    static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
