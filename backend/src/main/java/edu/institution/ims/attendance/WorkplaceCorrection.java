package edu.institution.ims.attendance;
import edu.institution.ims.internship.dto.RegisteredWorkplaceRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record WorkplaceCorrection(@NotNull @Valid RegisteredWorkplaceRequest workplace, @NotNull @DecimalMin("10") @DecimalMax("5000") BigDecimal radiusMeters, @NotBlank @Size(min=5,max=500) String reason) {}
