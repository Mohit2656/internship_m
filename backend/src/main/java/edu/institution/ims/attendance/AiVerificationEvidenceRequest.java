package edu.institution.ims.attendance;

import jakarta.validation.constraints.*;

public record AiVerificationEvidenceRequest(
        @NotBlank @Size(max = 36) String sessionId,
        @NotNull AiChallengeType challenge,
        @Min(0) @Max(10) int faceCount,
        boolean qualityPassed,
        boolean livenessPassed,
        @NotBlank @Size(max = 120) String modelVersion
) {}
