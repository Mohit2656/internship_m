package edu.institution.ims.internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InternshipLifecycleAdminRequest(
        @NotBlank(message = "Reason is required")
        @Size(min = 5, max = 1000, message = "Reason must be between 5 and 1000 characters")
        String reason
) {}
