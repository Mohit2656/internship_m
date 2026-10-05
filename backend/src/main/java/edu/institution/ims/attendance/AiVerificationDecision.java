package edu.institution.ims.attendance;

import java.time.Instant;

public record AiVerificationDecision(
        AttendanceVerificationSession session,
        AiChallengeType challenge,
        boolean faceDetected,
        boolean singleFace,
        boolean qualityPassed,
        boolean livenessPassed,
        boolean accepted,
        String modelVersion,
        Instant processedAt,
        VerificationReason reason
) {
    public static AiVerificationDecision missing() {
        return new AiVerificationDecision(null, null, false, false, false, false, false,
                null, null, VerificationReason.EDGE_AI_EVIDENCE_MISSING);
    }
}

