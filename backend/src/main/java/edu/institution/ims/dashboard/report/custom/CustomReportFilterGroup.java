package edu.institution.ims.dashboard.report.custom;

import java.util.List;

public record CustomReportFilterGroup(
        String conjunction, // "AND" or "OR"
        List<CustomReportFilterRule> rules,
        List<CustomReportFilterGroup> groups
) {}
