package edu.institution.ims.attendance;

import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AttendanceLivenessStatusTest {
    private static final Instant NOW = Instant.parse("2026-09-18T04:12:00Z");

    @Test
    void historicalSuccessIncludingStoredAiFlagsNeverBecomesCurrentLivenessVerification() {
        var record = record(VerificationStatus.VERIFIED, false);
        evidence(record, decision(true, true, true, true, true, VerificationReason.EDGE_AI_VERIFIED));
        assertThat(record.getLivenessStatus()).isEqualTo(LivenessStatus.HISTORICAL);
        assertThat(record.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(record.getEvidence().isAiLivenessPassed()).isTrue();
        assertThat(record.getEvidence().getDistanceMeters()).isEqualByComparingTo("10");
    }

    @Test
    void failedOrIncompleteAiFlagsCannotVerifyEvenIfThePersistedAttendanceVerdictSaysVerified() {
        for (AiVerificationDecision decision : new AiVerificationDecision[] {
                decision(false, false, true, true, true, VerificationReason.EDGE_AI_VERIFIED),
                decision(true, false, true, true, true, VerificationReason.EDGE_AI_VERIFIED),
                decision(true, true, false, true, true, VerificationReason.EDGE_AI_VERIFIED),
                decision(true, true, true, false, true, VerificationReason.EDGE_AI_VERIFIED),
                decision(true, true, true, true, false, VerificationReason.EDGE_AI_SESSION_EXPIRED)}) {
            var record = record(VerificationStatus.VERIFIED, true);
            evidence(record, decision);
            assertThat(record.getLivenessStatus()).isEqualTo(LivenessStatus.FAILED);
        }
    }

    @Test
    void missingLivenessEvidenceNeverVerifiesEvenIfThePersistedAttendanceVerdictSaysVerified() {
        var record = record(VerificationStatus.VERIFIED, true);
        assertThat(record.getLivenessStatus()).isEqualTo(LivenessStatus.NOT_PERFORMED);
        evidence(record, AiVerificationDecision.missing());
        assertThat(record.getLivenessStatus()).isEqualTo(LivenessStatus.NOT_PERFORMED);
    }

    @Test
    void successfulLivenessExposesSubmissionLocationWithoutWorkplaceOrEvidenceMutation() {
        var record = record(VerificationStatus.VERIFIED, true);
        AttendanceEvidence stored = evidence(record,
                decision(true, true, true, true, true, VerificationReason.EDGE_AI_VERIFIED));
        assertThat(record.getLivenessStatus()).isEqualTo(LivenessStatus.VERIFIED);
        AttendanceEvidenceResponse response = AttendanceEvidenceResponse.from(stored);
        assertThat(response.latitude()).isEqualByComparingTo("21.125");
        assertThat(response.longitude()).isEqualByComparingTo("79.0875");
        assertThat(response.gpsAccuracyMeters()).isEqualByComparingTo("10");
        assertThat(response.expectedLatitude()).isNull();
        assertThat(response.expectedLongitude()).isNull();
        assertThat(response.geofenceRadiusMeters()).isNull();
        assertThat(response.distanceMeters()).isNull();
        assertThat(response.locationCheckPassed()).isNull();
        assertThat(response.workplaceAddress()).isNull();
        assertThat(response.workplacePlaceId()).isNull();
        assertThat(response.exifLatitude()).isNull();
        assertThat(response.exifLongitude()).isNull();
        assertThat(stored.getLatitude()).isEqualByComparingTo("21.125");
        assertThat(stored.getExpectedLatitude()).isEqualByComparingTo("21.125");
        assertThat(stored.getExifLongitude()).isEqualByComparingTo("79.0875");
    }

    private static AttendanceRecord record(VerificationStatus verdict, boolean current) {
        var record = new AttendanceRecord(null, null, LocalDate.of(2026, 9, 18), AttendanceResult.PRESENT,
                verdict, AttendanceSubmissionType.PHYSICAL_CAPTURE, NOW);
        if (current) record.useLivenessVerification();
        return record;
    }

    private static AttendanceEvidence evidence(AttendanceRecord record, AiVerificationDecision ai) {
        BigDecimal latitude = new BigDecimal("21.125"), longitude = new BigDecimal("79.0875");
        var location = new InternshipLocation(null, latitude, longitude, new BigDecimal("200"), NOW);
        location.selectedPlace("test-only-place", "Test workplace address");
        var image = new ValidatedCameraImage("image/jpeg", 123, NOW, latitude, longitude);
        return new AttendanceEvidence(record, new PhysicalEvidenceInput(latitude, longitude, new BigDecimal("10"), image),
                location, new VerificationDecision(record.getVerificationStatus(), ai.reason(), new BigDecimal("10"), true, true),
                ai, NOW);
    }

    private static AiVerificationDecision decision(boolean face, boolean single, boolean quality,
            boolean liveness, boolean accepted, VerificationReason reason) {
        var session = new AttendanceVerificationSession("test-only-session", null, null,
                LocalDate.of(2026, 9, 18), AiChallengeType.BLINK, NOW.minusSeconds(1), NOW.plusSeconds(180));
        return new AiVerificationDecision(session, AiChallengeType.BLINK, face, single, quality, liveness,
                accepted, "test-only-model", NOW, reason);
    }
}
