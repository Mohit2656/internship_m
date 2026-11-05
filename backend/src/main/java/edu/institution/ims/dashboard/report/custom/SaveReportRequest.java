package edu.institution.ims.dashboard.report.custom;

import jakarta.validation.constraints.NotBlank;

public record SaveReportRequest(
        @NotBlank(message = "Title is required")
        String title,
        String description,
        String scope,
        @NotBlank(message = "Configuration JSON is required")
        String configurationJson
) {}
