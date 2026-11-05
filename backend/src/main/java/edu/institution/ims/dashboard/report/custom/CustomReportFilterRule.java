package edu.institution.ims.dashboard.report.custom;

import java.util.List;

public record CustomReportFilterRule(
        String field,
        String operator,
        String value,
        String secondaryValue,
        List<String> values
) {}
