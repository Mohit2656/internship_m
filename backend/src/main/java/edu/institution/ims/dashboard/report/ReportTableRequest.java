package edu.institution.ims.dashboard.report;

import jakarta.validation.constraints.*;
import java.util.*;

/** Explicit report and column allowlists are applied before any query. */
public record ReportTableRequest(
        @NotBlank @Size(max=40) String reportType,
        @Size(max=40) List<@NotBlank @Size(max=80) String> fields,
        @Size(max=25) Map<@NotBlank @Size(max=80) String, @NotNull @Size(max=254) String> filters,
        @Size(max=200) List<@NotNull @Positive Long> studentIds,
        @Min(0) @Max(100000) int page,
        @NotNull DashboardReportRequest.Format format) {}
