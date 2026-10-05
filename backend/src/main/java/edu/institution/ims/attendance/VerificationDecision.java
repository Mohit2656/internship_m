package edu.institution.ims.attendance;

import java.math.BigDecimal;

public record VerificationDecision(
        VerificationStatus status,
        VerificationReason reason,
        BigDecimal distanceMeters,
        Boolean locationCheckPassed,
        Boolean metadataConsistent
) {}
