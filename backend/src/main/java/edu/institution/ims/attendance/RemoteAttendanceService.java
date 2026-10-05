package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;

@Service
public class RemoteAttendanceService {
    static final int MIN_SUMMARY_LENGTH = 20;
    static final int MAX_SUMMARY_LENGTH = 1000;

    private final AttendanceRepository attendance;
    private final AttendanceEvidenceRepository evidence;
    private final AttendancePolicyService policies;
    private final UserRepository users;
    private final CameraImageValidator imageValidator;
    private final PhysicalVerificationPolicy metadataPolicy;
    private final AiVerificationService aiVerification;
    private final Clock clock;

    public RemoteAttendanceService(AttendanceRepository attendance, AttendanceEvidenceRepository evidence,
            AttendancePolicyService policies, UserRepository users, CameraImageValidator imageValidator,
            PhysicalVerificationPolicy metadataPolicy, AiVerificationService aiVerification, Clock clock) {
        this.attendance = attendance;
        this.evidence = evidence;
        this.policies = policies;
        this.users = users;
        this.imageValidator = imageValidator;
        this.metadataPolicy = metadataPolicy;
        this.aiVerification = aiVerification;
        this.clock = clock;
    }

    @Transactional
    public AttendanceResponse submitToday(UserPrincipal principal, MultipartFile photo,
            AiVerificationEvidenceRequest aiEvidence, String workSummary) {
        String summary = validateSummary(workSummary);
        ValidatedCameraImage image = imageValidator.validate(photo);
        User student = users.findById(principal.id()).filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "Only a student may submit attendance"));
        InternshipDetails internship = policies.eligibleInternship(student.getId());
        LocalDate today = LocalDate.now(clock);
        requireWithinInternship(internship, today);
        ResolvedAttendancePolicy policy = policies.resolve(internship, today);
        if (policy.verificationMethod() != AttendanceVerificationMethod.REMOTE) {
            throw policy.verificationMethod() == AttendanceVerificationMethod.PHYSICAL
                    ? error(HttpStatus.CONFLICT, "PHYSICAL_ATTENDANCE_REQUIRED", "Today's attendance requires physical evidence")
                    : error(HttpStatus.CONFLICT, "HYBRID_SCHEDULE_NOT_CONFIGURED",
                            "Today's hybrid work mode has not been configured");
        }
        AiVerificationDecision aiDecision = aiVerification.evaluate(principal, internship, today, aiEvidence);
        if (attendance.findByStudentIdAndAttendanceDate(student.getId(), today).isPresent()) throw duplicate();

        Instant serverTime = clock.instant();
        PhysicalEvidenceInput metadataInput = new PhysicalEvidenceInput(null, null, null, image);
        Boolean metadataConsistent = metadataPolicy.metadataConsistency(metadataInput, serverTime);
        VerificationStatus status = Boolean.FALSE.equals(metadataConsistent) || !aiDecision.accepted()
                ? VerificationStatus.UNVERIFIED : VerificationStatus.VERIFIED;
        VerificationReason reason = Boolean.FALSE.equals(metadataConsistent) ? VerificationReason.METADATA_CONTRADICTION
                : !aiDecision.accepted() ? aiDecision.reason()
                : VerificationReason.REMOTE_EVIDENCE_COMPLETE;
        AttendanceRecord record = new AttendanceRecord(student, internship, today, AttendanceResult.PRESENT,
                status, AttendanceSubmissionType.REMOTE_CAPTURE, serverTime);
        try {
            attendance.saveAndFlush(record);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
        evidence.saveAndFlush(new AttendanceEvidence(record, image, summary, metadataConsistent,
                reason, aiDecision, serverTime));
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

    private static AttendanceException duplicate() {
        return error(HttpStatus.CONFLICT, "ATTENDANCE_ALREADY_SUBMITTED", "Today's attendance has already been submitted");
    }

    private static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
