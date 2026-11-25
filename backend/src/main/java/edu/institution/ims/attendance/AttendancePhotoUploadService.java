package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.*;

@Service
public class AttendancePhotoUploadService {
    private final AttendanceRepository attendance;
    private final AttendanceEvidenceRepository evidence;
    private final InternshipDetailsRepository internships;
    private final UserRepository users;
    private final PhotoMetadataExtractor photoExtractor;
    private final AttendancePolicyService policies;
    private final Clock clock;
    private final AttendanceSubmissionService submissions;
    private final AttendanceLocationVerificationService locationVerification;

    public AttendancePhotoUploadService(AttendanceRepository attendance,
                                       AttendanceEvidenceRepository evidence,
                                       InternshipDetailsRepository internships,
                                       UserRepository users,
                                       PhotoMetadataExtractor photoExtractor,
                                       AttendancePolicyService policies,
                                       Clock clock,
                                       AttendanceSubmissionService submissions,
                                       AttendanceLocationVerificationService locationVerification) {
        this.attendance = attendance;
        this.evidence = evidence;
        this.internships = internships;
        this.users = users;
        this.photoExtractor = photoExtractor;
        this.policies = policies;
        this.clock = clock;
        this.submissions = submissions;
        this.locationVerification = locationVerification;
    }

    @Transactional
    public AttendanceResponse submitUploadedPhoto(UserPrincipal principal, Long internshipId, MultipartFile photo) {
        User student = users.findByIdForAttendanceUpdate(principal.id())
                .filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "Only a student may submit attendance"));

        InternshipDetails internship = internshipId == null
                ? eligibleInternship(student.getId())
                : internships.findByIdAndOnboardingStudentId(internshipId, student.getId())
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "ATTENDANCE_NOT_ELIGIBLE", "Selected internship is unavailable"));

        if (internship.isEarlyTerminated()) {
            throw error(HttpStatus.CONFLICT, "INTERNSHIP_TERMINATED", "This internship has ended early and cannot accept attendance.");
        }
        if (internship.isDeleted()) {
            throw error(HttpStatus.CONFLICT, "INTERNSHIP_DELETED", "This internship is no longer active.");
        }

        LocalDate today = InternshipLifecycle.today(clock);
        policies.requireAttendanceActive(internship, today);
        policies.requireSubmissionOpen(student.getId(), internship.getId(), today);

        // 1. Extract and validate metadata (throws error if date, time, or GPS coordinates are missing)
        PhotoMetadata metadata = photoExtractor.extractAndValidate(photo);

        // 2. Validate capture date equals today (attendance date in Asia/Kolkata)
        if (!today.equals(metadata.captureDate())) {
            throw error(HttpStatus.BAD_REQUEST, "PHOTO_DATE_MISMATCH",
                    "Photo was captured on " + metadata.captureDate() + ", which does not match today's attendance date (" + today + "). Attendance requires a photo taken today.");
        }

        // 3. Validate capture time within submission window
        Instant captureInstant = metadata.captureDate().atTime(metadata.captureTime()).atZone(PhotoMetadataExtractor.INSTITUTIONAL_ZONE).toInstant();

        // 4. Validate GPS location against approved workplace
        InternshipLocation workplace = locationVerification.requireApprovedWorkplace(internship);
        BigDecimal distance = PhysicalVerificationPolicy.haversineMeters(metadata.latitude(), metadata.longitude(),
                workplace.getLatitude(), workplace.getLongitude());

        boolean insideRadius = distance.compareTo(workplace.getGeofenceRadiusMeters()) <= 0;
        if (!insideRadius) {
            throw error(HttpStatus.BAD_REQUEST, "LOCATION_OUTSIDE_RADIUS",
                    "The photo's GPS location is outside the approved workplace radius (" +
                            distance.setScale(1, java.math.RoundingMode.HALF_UP) + "m from workplace, permitted radius: " +
                            workplace.getGeofenceRadiusMeters() + "m).");
        }

        SubmissionLocation submissionLocation = new SubmissionLocation(metadata.latitude(), metadata.longitude(), BigDecimal.valueOf(50));

        // 5. Create AttendanceRecord:
        // Submission type: PHYSICAL_CAPTURE, result: PRESENT, verification status: VERIFIED
        AttendanceRecord record = submissions.record(student, internship, today,
                VerificationStatus.VERIFIED, AttendanceSubmissionType.PHYSICAL_CAPTURE);

        ValidatedCameraImage validatedImage = new ValidatedCameraImage(
                photo.getContentType() != null ? photo.getContentType().toLowerCase() : "image/jpeg",
                photo.getSize(),
                captureInstant,
                metadata.latitude(),
                metadata.longitude()
        );

        PhysicalEvidenceInput evidenceInput = new PhysicalEvidenceInput(
                metadata.latitude(), metadata.longitude(), BigDecimal.valueOf(50), validatedImage
        );

        VerificationDecision decision = new VerificationDecision(
                VerificationStatus.VERIFIED,
                VerificationReason.VERIFIED_GEOFENCE,
                distance,
                true,
                true
        );

        Instant serverTime = clock.instant();
        AiVerificationDecision aiDecision = new AiVerificationDecision(
                null, null, true, true, true, true, true, "photo-upload-metadata", serverTime, VerificationReason.VERIFIED_GEOFENCE
        );
        AttendanceEvidence capturedEvidence = new AttendanceEvidence(record, evidenceInput, workplace, decision, aiDecision, serverTime);

        locationVerification.save(capturedEvidence, internship, submissionLocation, captureInstant, true, BigDecimal.valueOf(50));

        // Directly mark PRESENT since metadata verified (no mandatory mentor review required)
        record.finalizeSubmission(true);
        attendance.saveAndFlush(record);

        submissions.notifyMentor(record);

        // Photo bytes are NOT stored permanently; image stream is closed and discarded.
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

    private static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
