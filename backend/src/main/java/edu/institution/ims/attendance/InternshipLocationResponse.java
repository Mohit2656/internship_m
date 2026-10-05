package edu.institution.ims.attendance;

import java.math.BigDecimal;
import java.time.Instant;

public record InternshipLocationResponse(
        Long id,
        Long internshipId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal geofenceRadiusMeters,
        InternshipLocationStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant confirmedAt,
        Long confirmedBy
) {}
