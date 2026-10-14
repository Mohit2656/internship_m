package edu.institution.ims.dashboard.report;

import jakarta.validation.constraints.*;
import java.util.*;

public record DashboardReportRequest(
        @NotEmpty @Size(max = 150) List<@NotBlank @Size(max = 80) String> fields,
        @Size(max = 25) Map<@NotBlank @Size(max = 80) String, @NotNull @Size(max = 254) String> filters,
        @NotNull Format format) {
    public enum Format {
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        CSV("csv", "text/csv;charset=UTF-8"),
        PDF("pdf", "application/pdf");

        private final String extension;
        private final String contentType;
        Format(String extension, String contentType) { this.extension = extension; this.contentType = contentType; }
        public String extension() { return extension; }
        public String contentType() { return contentType; }
    }
}
