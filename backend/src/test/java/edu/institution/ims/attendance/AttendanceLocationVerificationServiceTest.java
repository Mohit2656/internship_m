package edu.institution.ims.attendance;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class AttendanceLocationVerificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T04:00:00Z");
    private final AttendanceLocationVerificationService service = new AttendanceLocationVerificationService(
            null, null, null, null, Clock.fixed(NOW, ZoneOffset.UTC), new BigDecimal("50"), 300);
    private final InternshipLocation workplace = new InternshipLocation(null, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("100"), NOW);

    @Test void distanceIsCalculatedOnServerAndBoundaryIsInclusive() {
        assertThat(check("0", "0.000899", "5", NOW).status()).isEqualTo(LocationVerificationStatus.VERIFIED);
        assertThat(check("0", "0.000900", "5", NOW).status()).isEqualTo(LocationVerificationStatus.OUTSIDE_RADIUS);
        assertThat(check("0", "0", "5", NOW).distanceMeters()).isEqualByComparingTo("0");
        assertThat(check("0", "0.002", "5", NOW).passed()).isFalse();
    }

    @Test void absentAndPoorAccuracyCannotPassAndConfiguredThresholdIsUsed() {
        assertThat(service.maxGpsAccuracyMeters()).isEqualByComparingTo("50");
        assertThat(check("0", "0", "51", NOW).status()).isEqualTo(LocationVerificationStatus.INSUFFICIENT_ACCURACY);
        assertThat(check("0", "0", "50", NOW).status()).isEqualTo(LocationVerificationStatus.VERIFIED);
        assertThat(check("0", "0", "0", NOW).status()).isEqualTo(LocationVerificationStatus.INSUFFICIENT_ACCURACY);
        assertThat(check("0", "0", "0.001", NOW).status()).isEqualTo(LocationVerificationStatus.INSUFFICIENT_ACCURACY);
    }

    @Test void twoHundredMetrePolicyAcceptsReported115AndBoundaryButStillRejectsPoorOrOutsideReadings() {
        var relaxed = new AttendanceLocationVerificationService(null, null, null, null,
                Clock.fixed(NOW, ZoneOffset.UTC), new BigDecimal("200"), 300);
        assertThat(relaxed.maxGpsAccuracyMeters()).isEqualByComparingTo("200");
        for (String accuracy : new String[]{"115", "200"}) {
            var sample = SubmissionLocation.require(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(accuracy));
            assertThat(relaxed.evaluate(sample, workplace, NOW, true).status()).isEqualTo(LocationVerificationStatus.VERIFIED);
        }
        var poor = SubmissionLocation.require(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200.01"));
        assertThat(relaxed.evaluate(poor, workplace, NOW, true).status()).isEqualTo(LocationVerificationStatus.INSUFFICIENT_ACCURACY);
        var outside = SubmissionLocation.require(BigDecimal.ZERO, new BigDecimal("0.002"), new BigDecimal("115"));
        assertThat(relaxed.evaluate(outside, workplace, NOW, true).status()).isEqualTo(LocationVerificationStatus.OUTSIDE_RADIUS);
    }

    @Test void invalidAccuracyIsDistinctFromBeingOutsideTheRadius() {
        for (BigDecimal accuracy : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1"),
                new BigDecimal("0.001"), new BigDecimal("1000000"), new BigDecimal("1e2147483647")}) {
            var submitted = SubmissionLocation.require(BigDecimal.ZERO, BigDecimal.ZERO, accuracy);
            assertThat(service.evaluate(submitted, workplace, NOW, true).status())
                    .isEqualTo(LocationVerificationStatus.INSUFFICIENT_ACCURACY);
        }
        assertThat(check("0", "0.002", "5", NOW).status()).isEqualTo(LocationVerificationStatus.OUTSIDE_RADIUS);
    }

    @Test void missingApprovedWorkplaceAndRemoteWorkNeverClaimGeofenceVerification() {
        var sample = SubmissionLocation.require(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
        var pending = service.evaluate(sample, null, NOW, true);
        assertThat(pending.status()).isEqualTo(LocationVerificationStatus.WORKPLACE_NOT_APPROVED);
        assertThat(pending.distanceMeters()).isNull();
        assertThat(service.evaluate(sample, null, NOW, false).status()).isEqualTo(LocationVerificationStatus.NOT_APPLICABLE);
        assertThat(service.evaluate(null, workplace, NOW, true).status()).isEqualTo(LocationVerificationStatus.LOCATION_UNAVAILABLE);
    }

    @Test void captureTimeIsValidatedAndOldSamplesRequireReview() {
        assertThat(check("0", "0", "5", NOW.minusSeconds(301)).status()).isEqualTo(LocationVerificationStatus.STALE_LOCATION);
        assertThat(check("0", "0", "5", NOW.minusSeconds(300)).status()).isEqualTo(LocationVerificationStatus.VERIFIED);
        assertThatThrownBy(() -> service.validateCaptureTime(NOW.plusSeconds(31))).isInstanceOf(AttendanceException.class);
        assertThatThrownBy(() -> service.validateCaptureTime(Instant.MIN)).isInstanceOf(AttendanceException.class);
        service.validateCaptureTime(null);
    }

    @Test void haversineRemainsFiniteAcrossDateLineAndAtPoles() {
        var crossing = PhysicalVerificationPolicy.haversineMeters(BigDecimal.ZERO, new BigDecimal("179.9999"),
                BigDecimal.ZERO, new BigDecimal("-179.9999"));
        assertThat(crossing).isBetween(new BigDecimal("22"), new BigDecimal("23"));
        assertThat(PhysicalVerificationPolicy.haversineMeters(new BigDecimal("90"), BigDecimal.ZERO,
                new BigDecimal("-90"), new BigDecimal("180"))).isBetween(new BigDecimal("20000000"), new BigDecimal("20100000"));
    }

    private LocationVerificationDecision check(String latitude, String longitude, String accuracy, Instant capturedAt) {
        return service.evaluate(SubmissionLocation.require(new BigDecimal(latitude), new BigDecimal(longitude),
                new BigDecimal(accuracy)), workplace, capturedAt, true);
    }
}
