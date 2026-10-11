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
    private final InternshipLocationRepository locations;
    private final InternshipLocationService locationService;
    private final UserRepository users;
    private final CameraImageValidator imageValidator;
    private final PhysicalVerificationPolicy verificationPolicy;
    private final AttendancePolicyService policies;
    private final AiVerificationService aiVerification;
    private final Clock clock;

    public PhysicalAttendanceService(AttendanceRepository attendance, AttendanceEvidenceRepository evidence,
            InternshipDetailsRepository internships, InternshipLocationRepository locations,
            InternshipLocationService locationService, UserRepository users, CameraImageValidator imageValidator,
            PhysicalVerificationPolicy verificationPolicy, AttendancePolicyService policies,
            AiVerificationService aiVerification, Clock clock) {
        this.attendance = attendance;
        this.evidence = evidence;
        this.internships = internships;
        this.locations = locations;
        this.locationService = locationService;
        this.users = users;
        this.imageValidator = imageValidator;
        this.verificationPolicy = verificationPolicy;
        this.policies = policies;
        this.aiVerification = aiVerification;
        this.clock = clock;
    }

    @Transactional
    public AttendanceResponse submitToday(UserPrincipal principal, MultipartFile photo,
            AiVerificationEvidenceRequest aiEvidence,
            BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {
        boolean invalidGps=false;
        try { validateCoordinates(latitude,longitude,accuracyMeters); }
        catch (AttendanceException invalid) { invalidGps=true;latitude=null;longitude=null;accuracyMeters=null; }
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
        AiVerificationDecision aiDecision = aiVerification.evaluate(principal, internship, today, aiEvidence);
        if (attendance.findByStudentIdAndAttendanceDate(student.getId(), today).isPresent()) throw duplicate();

        InternshipLocation location = locations.findForVerification(internship.getId()).orElse(null);
        Instant serverTime = clock.instant();
        PhysicalEvidenceInput input = new PhysicalEvidenceInput(latitude, longitude, accuracyMeters, image);
        VerificationDecision physicalDecision = verificationPolicy.evaluate(
                InternshipMode.OFFICE_REPORTING, location, input, serverTime, locationService.fullyApproved(location));
        if(invalidGps) physicalDecision=new VerificationDecision(VerificationStatus.UNVERIFIED,VerificationReason.INVALID_LIVE_LOCATION,null,false,null);
        VerificationDecision decision = combine(physicalDecision, aiDecision);
        AttendanceRecord record = new AttendanceRecord(student, internship, today, AttendanceResult.PRESENT,
                decision.status(), AttendanceSubmissionType.PHYSICAL_CAPTURE, serverTime);
        try {
            attendance.saveAndFlush(record);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
        evidence.saveAndFlush(new AttendanceEvidence(record, input, location, decision, aiDecision, serverTime));
        return AttendanceService.response(record);
    }

    private static VerificationDecision combine(VerificationDecision physical, AiVerificationDecision ai) {
        if (physical.status() == VerificationStatus.UNVERIFIED) return physical;
        if (ai.accepted()) return physical;
        return new VerificationDecision(VerificationStatus.UNVERIFIED, ai.reason(), physical.distanceMeters(),
                physical.locationCheckPassed(), physical.metadataConsistent());
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

    private static void validateCoordinates(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy) {
        boolean none = latitude == null && longitude == null && accuracy == null;
        boolean all = latitude != null && longitude != null && accuracy != null;
        if (!none && !all) {
            throw error(HttpStatus.BAD_REQUEST, "INCOMPLETE_LIVE_LOCATION",
                    "Latitude, longitude and GPS accuracy must be provided together");
        }
        if (none) return;
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_LATITUDE", "Latitude must be between -90 and 90");
        }
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_LONGITUDE", "Longitude must be between -180 and 180");
        }
        if (accuracy.compareTo(BigDecimal.ZERO) <= 0 || accuracy.compareTo(BigDecimal.valueOf(100_000)) > 0) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_GPS_ACCURACY", "GPS accuracy must be a positive distance");
        }
    }

    private static AttendanceException duplicate() {
        return error(HttpStatus.CONFLICT, "ATTENDANCE_ALREADY_SUBMITTED", "Today's attendance has already been submitted");
    }

    private static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
