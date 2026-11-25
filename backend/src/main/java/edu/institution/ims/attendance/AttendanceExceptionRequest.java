package edu.institution.ims.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AttendanceExceptionRequest(
        Long internshipId,
        @NotNull(message = "Exception date is required")
        LocalDate date,
        AttendanceExceptionType type,
        @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {}
