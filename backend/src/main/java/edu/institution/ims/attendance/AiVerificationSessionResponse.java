package edu.institution.ims.attendance;

import java.time.Instant;

public record AiVerificationSessionResponse(
        String sessionId,
        AiChallengeType challenge,
        Instant expiresAt
) {}

