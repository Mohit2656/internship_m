package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;

/** Evaluates only a submitted sample against the authenticated internship's approved revision. */
@Service
public class AttendanceLocationVerificationService {
    private static final Logger log = LoggerFactory.getLogger(AttendanceLocationVerificationService.class);
    private final InternshipLocationRepository locations;
    private final InternshipLocationService approvals;
    private final AttendanceEvidenceRepository evidence;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final BigDecimal maxAccuracy;
    private final Duration maxAge;

    public AttendanceLocationVerificationService(InternshipLocationRepository locations,
            InternshipLocationService approvals, AttendanceEvidenceRepository evidence, JdbcTemplate jdbc, Clock clock,
            @Value("${app.attendance.max-gps-accuracy-meters:200}") BigDecimal maxAccuracy,
            @Value("${app.attendance.max-location-age-seconds:300}") long maxAgeSeconds) {
        if (maxAccuracy.signum() <= 0 || maxAgeSeconds <= 0)
            throw new IllegalArgumentException("GPS accuracy threshold and sample age must be positive");
        this.locations = locations;
        this.approvals = approvals;
        this.evidence = evidence;
        this.jdbc = jdbc;
        this.clock = clock;
        this.maxAccuracy = maxAccuracy;
        this.maxAge = Duration.ofSeconds(maxAgeSeconds);
        log.info("Attendance GPS policy maxAccuracyMeters={} maxLocationAgeSeconds={}", maxAccuracy, maxAgeSeconds);
    }

    public BigDecimal maxGpsAccuracyMeters() { return maxAccuracy; }

    public void validateCaptureTime(Instant capturedAt) {
        if (capturedAt != null && (capturedAt.isBefore(Instant.EPOCH)
                || capturedAt.isAfter(clock.instant().plusSeconds(30))))
            throw AttendanceService.error(HttpStatus.BAD_REQUEST, "INVALID_LOCATION_CAPTURE_TIME",
                    "Location capture time is invalid. Refresh your device location and retry.");
    }

    public void save(AttendanceEvidence captured, InternshipDetails internship, SubmissionLocation submitted,
            Instant capturedAt, boolean geofenceApplicable, BigDecimal reportedAccuracy) {
        validateCaptureTime(capturedAt);
        // The evidence constructor attaches itself to the managed attendance record. Make it
        // managed before the approval query can trigger Hibernate's automatic flush.
        evidence.save(captured);
        InternshipLocation workplace = geofenceApplicable
                ? locations.findForVerification(internship.getId()).filter(approvals::fullyApproved).orElse(null)
                : null;
        LocationVerificationDecision decision = evaluate(submitted, workplace, capturedAt, geofenceApplicable);
        captured.recordLocationVerification(workplace, decision, capturedAt);
        evidence.saveAndFlush(captured);
        jdbc.update("insert into attendance_location_verification_audit "
                + "(attendance_id,student_user_id,verified_at,location_status,workplace_revision,latitude,longitude,"
                + "gps_accuracy_meters,location_captured_at,expected_latitude,expected_longitude,"
                + "geofence_radius_meters,distance_meters,location_check_passed) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                captured.getAttendance().getId(), captured.getAttendance().getStudent().getId(),
                Timestamp.from(clock.instant()), decision.status().name(), captured.getWorkplaceRevision(),
                submitted.latitude(), submitted.longitude(), submitted.accuracyMeters(),
                capturedAt == null ? null : Timestamp.from(capturedAt), captured.getExpectedLatitude(),
                captured.getExpectedLongitude(), captured.getGeofenceRadiusMeters(),
                decision.distanceMeters(), decision.passed());
        // Diagnostic values only: no identity, coordinates, address, token, or session data.
        String safeReportedAccuracy = reportedAccuracy == null ? "missing"
                : reportedAccuracy.precision() <= 40 && Math.abs((long) reportedAccuracy.scale()) <= 20
                    ? reportedAccuracy.toString() : "invalid";
        log.info("Attendance GPS reportedAccuracyMeters={} normalizedAccuracyMeters={} maxAccuracyMeters={} result={}",
                safeReportedAccuracy, submitted.accuracyMeters(), maxAccuracy, decision.status());
    }

    LocationVerificationDecision evaluate(SubmissionLocation submitted, InternshipLocation approvedWorkplace,
            Instant capturedAt, boolean geofenceApplicable) {
        if (!geofenceApplicable) return result(LocationVerificationStatus.NOT_APPLICABLE, null, null);
        if (submitted == null || submitted.latitude() == null || submitted.longitude() == null)
            return result(LocationVerificationStatus.LOCATION_UNAVAILABLE, null, null);
        if (approvedWorkplace == null)
            return result(LocationVerificationStatus.WORKPLACE_NOT_APPROVED, null, null);
        BigDecimal distance = PhysicalVerificationPolicy.haversineMeters(submitted.latitude(), submitted.longitude(),
                approvedWorkplace.getLatitude(), approvedWorkplace.getLongitude());
        if (capturedAt != null && capturedAt.isBefore(clock.instant().minus(maxAge)))
            return result(LocationVerificationStatus.STALE_LOCATION, distance, false);
        if (submitted.accuracyMeters() == null || submitted.accuracyMeters().compareTo(maxAccuracy) > 0)
            return result(LocationVerificationStatus.INSUFFICIENT_ACCURACY, distance, false);
        boolean inside = distance.compareTo(approvedWorkplace.getGeofenceRadiusMeters()) <= 0;
        return result(inside ? LocationVerificationStatus.VERIFIED : LocationVerificationStatus.OUTSIDE_RADIUS,
                distance, inside);
    }

    private static LocationVerificationDecision result(LocationVerificationStatus status, BigDecimal distance, Boolean passed) {
        return new LocationVerificationDecision(status, distance, passed);
    }
}
