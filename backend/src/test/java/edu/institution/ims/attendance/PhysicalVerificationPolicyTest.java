package edu.institution.ims.attendance;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class PhysicalVerificationPolicyTest {
    private static final Instant NOW = Instant.parse("2026-09-18T04:12:00Z");
    private final PhysicalVerificationPolicy policy = new PhysicalVerificationPolicy(new BigDecimal("100"), new BigDecimal("100"), 600);
    private final InternshipLocation workplace = new InternshipLocation(null, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200"), NOW);

    private VerificationDecision evaluate(String longitude, String accuracy, boolean approved) {
        var image = new ValidatedCameraImage("image/jpeg", 100, null, null, null);
        var input = new PhysicalEvidenceInput(longitude == null ? null : BigDecimal.ZERO,
                longitude == null ? null : new BigDecimal(longitude), accuracy == null ? null : new BigDecimal(accuracy), image);
        return policy.evaluate(edu.institution.ims.internship.InternshipMode.OFFICE_REPORTING, workplace, input, NOW, approved);
    }

    @Test void identicalCoordinatesVerifyOnlyWithAcceptableAccuracyAndApproval() {
        var result = evaluate("0", "10", true);
        assertThat(result.status()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(result.distanceMeters()).isEqualByComparingTo("0");
        assertThat(result.locationCheckPassed()).isTrue();
    }

    @Test void knownDistancesInsideAndOutsideTheTwoHundredMetreRadius() {
        var inside = evaluate("0.001", "10", true);
        assertThat(inside.distanceMeters()).isBetween(new BigDecimal("111"), new BigDecimal("112"));
        assertThat(inside.status()).isEqualTo(VerificationStatus.VERIFIED);
        var outside = evaluate("0.002", "10", true);
        assertThat(outside.distanceMeters()).isBetween(new BigDecimal("222"), new BigDecimal("223"));
        assertThat(outside.status()).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(outside.reason()).isEqualTo(VerificationReason.OUTSIDE_GEOFENCE);
    }

    @Test void accuracyLimitAndUncertaintyMarginRemainEnforced() {
        assertThat(evaluate("0", "100", true).status()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(evaluate("0", "100.01", true).reason()).isEqualTo(VerificationReason.GPS_ACCURACY_TOO_LOW);
        assertThat(evaluate("0.001", "100", true).reason()).isEqualTo(VerificationReason.OUTSIDE_GEOFENCE);
    }

    @Test void missingGpsAndIncompleteApprovalHaveDistinctReasons() {
        assertThat(evaluate(null, null, true).reason()).isEqualTo(VerificationReason.LIVE_LOCATION_UNAVAILABLE);
        assertThat(evaluate("0", "10", false).reason()).isEqualTo(VerificationReason.EXPECTED_LOCATION_NOT_CONFIRMED);
    }

    @Test
    void haversineUsesCoordinatesRatherThanAFrontendClaim() {
        BigDecimal distance = PhysicalVerificationPolicy.haversineMeters(
                new BigDecimal("18.520400"), new BigDecimal("73.856700"),
                new BigDecimal("18.530400"), new BigDecimal("73.856700"));
        assertThat(distance).isBetween(new BigDecimal("1100"), new BigDecimal("1120"));
    }

    @Test
    void contradictoryExifIsNotSupportingEvidence() {
        PhysicalVerificationPolicy policy = new PhysicalVerificationPolicy(
                new BigDecimal("100"), new BigDecimal("100"), 600);
        ValidatedCameraImage image = new ValidatedCameraImage("image/jpeg", 100,
                Instant.parse("2020-01-01T00:00:00Z"), null, null);
        PhysicalEvidenceInput input = new PhysicalEvidenceInput(new BigDecimal("18.5204"),
                new BigDecimal("73.8567"), new BigDecimal("10"), image);
        InternshipLocation pending = null;
        VerificationDecision result = policy.evaluate(
                edu.institution.ims.internship.InternshipMode.OFFICE_REPORTING, pending, input,
                Instant.parse("2026-09-18T04:12:00Z"));
        assertThat(result.metadataConsistent()).isFalse();
    }
}
