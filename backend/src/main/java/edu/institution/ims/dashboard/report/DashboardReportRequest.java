package edu.institution.ims.dashboard.report;

import jakarta.validation.constraints.*;
import java.util.*;

public record DashboardReportRequest(
        @NotEmpty @Size(max = 150) List<@NotBlank @Size(max = 80) String> fields,
        @Size(max = 25) Map<@NotBlank @Size(max = 80) String, @NotNull @Size(max = 254) String> filters,
        @NotNull Format format) {
    public enum Format { XLSX, CSV }
}
