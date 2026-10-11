package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.*;

@Service
public class PhysicalAttendanceService {
    private final AttendanceRepository attendance;
    private final AttendanceEvidenceRepository evidence;
    private final InternshipDetailsRepository internships;
    private final UserRepository users;
    private final CameraImageValidator imageValidator;
    private final AttendancePolicyService policies;
    private final AiVerificationService aiVerification;
    private final Clock clock;

    public PhysicalAttendanceService(AttendanceRepository attendance, AttendanceEvidenceRepository evidence,
            InternshipDetailsRepository internships, UserRepository users, CameraImageValidator imageValidator,
            AttendancePolicyService policies,
            AiVerificationService aiVerification, Clock clock) {
        this.attendance = attendance;
        this.evidence = evidence;
        this.internships = internships;
        this.users = users;
        this.imageValidator = imageValidator;
        this.policies = policies;
        this.aiVerification = aiVerification;
        this.clock = clock;
    }

    @Transactional
    public AttendanceResponse submitToday(UserPrincipal principal, MultipartFile photo,
            AiVerificationEvidenceRequest aiEvidence,
            BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {
        // Required submission coordinates are evidence only, never a liveness or workplace check.
        ValidatedCameraImage image = imageValidator.validate(photo);
        User student = users.findById(principal.id()).filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "Only a student may submit attendance"));
        InternshipDetails internship = eligibleInternship(student.getId());
        LocalDate today = LocalDate.now(clock);
        requireWithinInternship(internship, today);
        ResolvedAttendancePolicy policy = policies.resolve(internship, today);
        if (policy.verificationMethod() != AttendanceVerificationMethod.PHYSICAL) {
            throw policy.verificationMethod() == AttendanceVerificationMethod.REMOTE
                    ? error(HttpStatus.CONFLICT, "REMOTE_ATTENDANCE_REQUIRED", "Today's attendance requires remote evidence")
                    : error(HttpStatus.CONFLICT, "HYBRID_SCHEDULE_NOT_CONFIGURED",
                            "Today's hybrid work mode has not been configured");
        }
        SubmissionLocation submitted = SubmissionLocation.require(latitude, longitude, accuracyMeters);
        AiVerificationDecision aiDecision = aiVerification.evaluate(principal, internship, today, aiEvidence);
        if (attendance.findByStudentIdAndAttendanceDate(student.getId(), today).isPresent()) throw duplicate();

        Instant serverTime = clock.instant();
        PhysicalEvidenceInput input = new PhysicalEvidenceInput(submitted.latitude(), submitted.longitude(), submitted.accuracyMeters(), image);
        VerificationDecision decision = new VerificationDecision(
                aiDecision.accepted() ? VerificationStatus.VERIFIED : VerificationStatus.UNVERIFIED,
                aiDecision.reason(), null, null, null);
        AttendanceRecord record = new AttendanceRecord(student, internship, today, AttendanceResult.PRESENT,
                decision.status(), AttendanceSubmissionType.PHYSICAL_CAPTURE, serverTime);
        record.useLivenessVerification();
        try {
            attendance.saveAndFlush(record);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
        evidence.saveAndFlush(new AttendanceEvidence(record, input, null, decision, aiDecision, serverTime));
        return AttendanceService.response(record);
    }

    private InternshipDetails eligibleInternship(Long studentId) {
        InternshipDetails internship = internships.findByOnboardingStudentId(studentId)
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

    private static AttendanceException duplicate() {
        return error(HttpStatus.CONFLICT, "ATTENDANCE_ALREADY_SUBMITTED", "Today's attendance has already been submitted");
    }

    private static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
