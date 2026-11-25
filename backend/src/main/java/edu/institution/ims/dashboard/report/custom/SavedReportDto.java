package edu.institution.ims.dashboard.report.custom;

import java.time.Instant;

public record SavedReportDto(
        Long id,
        String title,
        String description,
        String scope,
        String configurationJson,
        Long createdBy,
        String creatorEmail,
        Instant createdAt,
        Instant updatedAt
) {}
