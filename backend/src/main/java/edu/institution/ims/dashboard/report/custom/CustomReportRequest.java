package edu.institution.ims.dashboard.report.custom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CustomReportRequest(
        @NotBlank(message = "Report title is required")
        String title,

        String scope, // "INTERNSHIP" or "STUDENT"

        Long academicSessionId,

        @NotEmpty(message = "Select at least one report column")
        List<String> columns,

        CustomReportFilterGroup filterGroup,

        String groupBy, // e.g. "companyName", "branch", "NONE"

        String sortBy, // e.g. "companyName", "studentName", "annualCtc"

        String sortDirection, // "ASC" or "DESC"

        String format, // "PREVIEW", "XLSX", "CSV", "PDF"

        Integer page,

        Integer size,

        String search
) {}
