package edu.institution.ims.attendance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttendanceExceptionReviewRequest(
        @NotNull(message = "Status is required")
        AttendanceExceptionStatus status,
        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        String remarks
) {}
