package edu.institution.ims.attendance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record InternshipLocationRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @DecimalMin("10.0") @DecimalMax("5000.0") BigDecimal geofenceRadiusMeters
) {}
