package edu.institution.ims.attendance;

import java.math.BigDecimal;

public record LocationVerificationDecision(LocationVerificationStatus status, BigDecimal distanceMeters,
        Boolean passed) {}
