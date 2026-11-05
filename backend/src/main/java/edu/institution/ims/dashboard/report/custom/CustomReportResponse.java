package edu.institution.ims.dashboard.report.custom;

import java.util.List;
import java.util.Map;

public record CustomReportResponse(
        String institutionName,
        String departmentName,
        String headerTitle,
        String reportTitle,
        String academicSessionName,
        String generatedAt,
        String timezone,
        String filterSummary,
        List<CustomReportCatalog.Field> columns,
        String scope,
        String groupBy,
        long totalStudents,
        long totalInternships,
        long totalRecords,
        Map<String, Object> overallSummary,
        List<GroupData> groups,
        int page,
        int size,
        int totalPages
) {
    public record GroupData(
            String groupValue,
            int recordCount,
            Map<String, Object> summary,
            List<Map<String, Object>> rows
    ) {}
}
