package edu.institution.ims.attendance;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class PhysicalVerificationPolicyTest {
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
