package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.math.BigDecimal;

@Service
public class RemoteAttendanceService {
    static final int MIN_SUMMARY_LENGTH = 20;
    static final int MAX_SUMMARY_LENGTH = 1000;

    private final AttendanceRepository attendance;
    private final AttendanceEvidenceRepository evidence;
    private final AttendancePolicyService policies;
    private final UserRepository users;
    private final CameraImageValidator imageValidator;
    private final AiVerificationService aiVerification;
    private final Clock clock;
    private final AttendanceSubmissionService submissions;

    public RemoteAttendanceService(AttendanceRepository attendance, AttendanceEvidenceRepository evidence,
            AttendancePolicyService policies, UserRepository users, CameraImageValidator imageValidator,
            AiVerificationService aiVerification, Clock clock, AttendanceSubmissionService submissions) {
        this.attendance = attendance;
        this.evidence = evidence;
        this.policies = policies;
        this.users = users;
        this.imageValidator = imageValidator;
        this.aiVerification = aiVerification;
        this.clock = clock;
        this.submissions = submissions;
    }

    @Transactional
    public AttendanceResponse submitToday(UserPrincipal principal, MultipartFile photo,
            AiVerificationEvidenceRequest aiEvidence, String workSummary) {
        return submitToday(principal, photo, aiEvidence, workSummary, null, null, null);
    }

    @Transactional
    public AttendanceResponse submitToday(UserPrincipal principal, MultipartFile photo,
            AiVerificationEvidenceRequest aiEvidence, String workSummary,
            BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy) {
        String summary = validateSummary(workSummary);
        ValidatedCameraImage image = imageValidator.validate(photo);
        User student = users.findByIdForAttendanceUpdate(principal.id()).filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "Only a student may submit attendance"));
        InternshipDetails internship = policies.eligibleInternship(student.getId());
        LocalDate today = LocalDate.now(clock);
        requireWithinInternship(internship, today);
        policies.requireSubmissionOpen(student.getId(), today);
        ResolvedAttendancePolicy policy = policies.resolve(internship, today);
        if (policy.verificationMethod() != AttendanceVerificationMethod.REMOTE) {
            throw policy.verificationMethod() == AttendanceVerificationMethod.PHYSICAL
                    ? error(HttpStatus.CONFLICT, "PHYSICAL_ATTENDANCE_REQUIRED", "Today's attendance requires physical evidence")
                    : error(HttpStatus.CONFLICT, "HYBRID_SCHEDULE_NOT_CONFIGURED",
                            "Today's hybrid work mode has not been configured");
        }
        SubmissionLocation submitted = SubmissionLocation.require(latitude, longitude, accuracy);
        AiVerificationDecision aiDecision = aiVerification.evaluate(principal, internship, today, aiEvidence);

        Instant serverTime = clock.instant();
        VerificationStatus status = aiDecision.accepted() ? VerificationStatus.VERIFIED : VerificationStatus.UNVERIFIED;
        VerificationReason reason = aiDecision.reason();
        AttendanceRecord record = submissions.record(student, internship, today,
                status, AttendanceSubmissionType.REMOTE_CAPTURE);
        AttendanceEvidence captured = new AttendanceEvidence(record, image, summary, null, reason, aiDecision, serverTime);
        captured.recordSubmissionLocation(submitted.latitude(), submitted.longitude(), submitted.accuracyMeters());
        evidence.saveAndFlush(captured);
        return AttendanceService.response(record);
    }

    private static String validateSummary(String workSummary) {
        String clean = workSummary == null ? "" : workSummary.trim();
        if (clean.length() < MIN_SUMMARY_LENGTH || clean.length() > MAX_SUMMARY_LENGTH) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_DAILY_WORK_SUMMARY",
                    "Daily work summary must contain between 20 and 1000 characters");
        }
        return clean;
    }

    private static void requireWithinInternship(InternshipDetails internship, LocalDate date) {
        if (date.isBefore(internship.getStartDate()) || date.isAfter(internship.getEndDate())) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_OUTSIDE_INTERNSHIP_PERIOD",
                    "Attendance is available only during the registered internship period");
        }
    }

    private static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
