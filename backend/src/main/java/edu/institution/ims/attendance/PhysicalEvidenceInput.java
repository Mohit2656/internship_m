package edu.institution.ims.attendance;

import java.math.BigDecimal;

public record PhysicalEvidenceInput(
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracyMeters,
        ValidatedCameraImage image
) {}
