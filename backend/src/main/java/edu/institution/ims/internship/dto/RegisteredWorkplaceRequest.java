package edu.institution.ims.internship.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record RegisteredWorkplaceRequest(@Size(max=255) String placeId,
    @NotBlank @Size(max=1000) String address,
    @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
    @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude, @Size(max=255) String name) {
    public RegisteredWorkplaceRequest(String placeId, String address, BigDecimal latitude, BigDecimal longitude) { this(placeId,address,latitude,longitude,null); }
}
