package edu.institution.ims.dashboard.report.custom;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CustomReportQueryBuilder {

    private final CustomReportCatalog catalog;

    public CustomReportQueryBuilder(CustomReportCatalog catalog) {
        this.catalog = catalog;
    }

    public record BuiltQuery(
            String selectSql,
            String countSql,
            String fromClause,
            String whereClause,
            MapSqlParameterSource params,
            List<CustomReportCatalog.Field> selectedFields,
            String groupByFieldId,
            String sortSql,
            String filterSummary
    ) {}

    public BuiltQuery build(CustomReportRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Report title is mandatory");
        }
        if (request.columns() == null || request.columns().isEmpty()) {
            throw new IllegalArgumentException("At least one report column must be selected");
        }

        boolean isInternshipScope = !"STUDENT".equalsIgnoreCase(request.scope());

        // Validate and gather selected columns
        List<CustomReportCatalog.Field> selectedFields = new ArrayList<>();
        Set<String> columnIds = new LinkedHashSet<>(request.columns());
        for (String colId : columnIds) {
            CustomReportCatalog.Field field = catalog.getField(colId);
            if (field == null) {
                throw new IllegalArgumentException("Unsupported report column: " + colId);
            }
            selectedFields.add(field);
        }

        // Validate groupBy field
        String rawGroupBy = request.groupBy() == null || request.groupBy().isBlank() ? "companyName" : request.groupBy().trim();
        final String groupBy = (!"NONE".equalsIgnoreCase(rawGroupBy) && !catalog.hasField(rawGroupBy)) ? "companyName" : rawGroupBy;

        // Build FROM clause
        String fromClause = isInternshipScope ? buildInternshipFromClause() : buildStudentFromClause();

        // Parameter collector and predicate builder
        MapSqlParameterSource params = new MapSqlParameterSource();
        AtomicInteger paramIndex = new AtomicInteger(0);
        StringBuilder whereClause = new StringBuilder();

        // Base where condition
        if (isInternshipScope) {
            whereClause.append("where u.role = 'STUDENT' and u.active = true");
        } else {
            whereClause.append("where u.role = 'STUDENT' and u.active = true");
        }

        // Academic session scope
        if (request.academicSessionId() != null && request.academicSessionId() > 0) {
            whereClause.append(" and u.academic_session_id = :academicSessionId");
            params.addValue("academicSessionId", request.academicSessionId());
        }

        // Process filter groups
        StringBuilder filterSummaryBuilder = new StringBuilder();
        if (request.filterGroup() != null) {
            String filterSql = buildFilterGroupSql(request.filterGroup(), params, paramIndex, 0, filterSummaryBuilder);
            if (!filterSql.isBlank()) {
                whereClause.append(" and (").append(filterSql).append(")");
            }
        }
        String filterSummary = filterSummaryBuilder.toString().trim();
        if (filterSummary.isBlank()) {
            filterSummary = "All records";
        }

        // Preview search filter
        if (request.search() != null && !request.search().trim().isBlank()) {
            String sanitizedSearch = "%" + request.search().trim().toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            whereClause.append(" and (lower(coalesce(p.student_name, sa.student_name, u.email)) like :previewSearch escape '!'"
                    + " or lower(coalesce(p.uid, sa.uid, '')) like :previewSearch escape '!'"
                    + " or lower(coalesce(i.company_name, '')) like :previewSearch escape '!')");
            params.addValue("previewSearch", sanitizedSearch);
        }

        // Build select list
        StringBuilder selectList = new StringBuilder();
        selectList.append("u.id as student_user_id, ");
        selectList.append("i.id as internship_id, ");
        selectList.append("coalesce(o.internship_number, 1) as internship_slot, ");

        for (int i = 0; i < selectedFields.size(); i++) {
            CustomReportCatalog.Field field = selectedFields.get(i);
            selectList.append(field.expression()).append(" as \"").append(field.id()).append("\"");
            if (i < selectedFields.size() - 1) {
                selectList.append(", ");
            }
        }

        // Ensure groupBy column is present in select if not already
        if (!"NONE".equalsIgnoreCase(groupBy) && selectedFields.stream().noneMatch(f -> f.id().equals(groupBy))) {
            CustomReportCatalog.Field groupField = catalog.getField(groupBy);
            if (groupField != null) {
                selectList.append(", ").append(groupField.expression()).append(" as \"").append(groupField.id()).append("\"");
            }
        }

        // Build sort clause
        String sortDirection = "DESC".equalsIgnoreCase(request.sortDirection()) ? "DESC" : "ASC";
        String sortSql;
        String requestedSort = request.sortBy();

        if (requestedSort != null && catalog.hasField(requestedSort)) {
            CustomReportCatalog.Field sortField = catalog.getField(requestedSort);
            sortSql = "order by " + sortField.expression() + " " + sortDirection + " nulls last, u.id asc";
        } else if ("studentCount".equalsIgnoreCase(requestedSort)) {
            sortSql = "order by u.id " + sortDirection;
        } else {
            sortSql = "order by coalesce(i.company_name, 'Unassigned') asc, coalesce(p.student_name, sa.student_name) asc";
        }

        String selectSql = "select " + selectList + " " + fromClause + " " + whereClause + " " + sortSql;
        String countSql = "select count(distinct " + (isInternshipScope ? "i.id" : "u.id") + ") " + fromClause + " " + whereClause;

        return new BuiltQuery(selectSql, countSql, fromClause, whereClause.toString(), params, selectedFields, groupBy, sortSql, filterSummary);
    }

    private String buildFilterGroupSql(CustomReportFilterGroup group, MapSqlParameterSource params,
                                       AtomicInteger paramIndex, int depth, StringBuilder summary) {
        if (group == null || depth > 3) return "";

        String conjunction = "OR".equalsIgnoreCase(group.conjunction()) ? " OR " : " AND ";
        String summaryConjunction = "OR".equalsIgnoreCase(group.conjunction()) ? " OR " : " AND ";
        List<String> conditions = new ArrayList<>();
        List<String> summaryItems = new ArrayList<>();

        if (group.rules() != null) {
            for (CustomReportFilterRule rule : group.rules()) {
                if (rule == null || rule.field() == null || rule.operator() == null) continue;
                CustomReportCatalog.Field field = catalog.getField(rule.field());
                if (field == null) continue;

                String pName = "p" + paramIndex.incrementAndGet();
                String condition = buildRuleCondition(field, rule, pName, params, summaryItems);
                if (condition != null && !condition.isBlank()) {
                    conditions.add(condition);
                }
            }
        }

        if (group.groups() != null && depth < 3) {
            for (CustomReportFilterGroup subGroup : group.groups()) {
                StringBuilder subSummary = new StringBuilder();
                String subSql = buildFilterGroupSql(subGroup, params, paramIndex, depth + 1, subSummary);
                if (subSql != null && !subSql.isBlank()) {
                    conditions.add("(" + subSql + ")");
                    if (subSummary.length() > 0) {
                        summaryItems.add("(" + subSummary + ")");
                    }
                }
            }
        }

        if (conditions.isEmpty()) return "";

        if (summary.length() > 0 && !summaryItems.isEmpty()) {
            summary.append(summaryConjunction);
        }
        summary.append(String.join(summaryConjunction, summaryItems));

        return String.join(conjunction, conditions);
    }

    private String buildRuleCondition(CustomReportCatalog.Field field, CustomReportFilterRule rule,
                                      String pName, MapSqlParameterSource params, List<String> summaryItems) {
        String expr = field.expression();
        String op = rule.operator().toUpperCase(Locale.ROOT);
        String val = rule.value() != null ? rule.value().trim() : "";
        String fieldLabel = field.label();

        switch (op) {
            case "EQUALS":
            case "IS":
                if (field.kind() == CustomReportCatalog.Kind.NUMBER) {
                    try {
                        BigDecimal num = new BigDecimal(val);
                        params.addValue(pName, num);
                        summaryItems.add(fieldLabel + " = " + num);
                        return expr + " = :" + pName;
                    } catch (Exception e) { return ""; }
                } else if (field.kind() == CustomReportCatalog.Kind.DATE) {
                    try {
                        LocalDate d = LocalDate.parse(val);
                        params.addValue(pName, java.sql.Date.valueOf(d));
                        summaryItems.add(fieldLabel + " = " + d);
                        return expr + " = :" + pName;
                    } catch (Exception e) { return ""; }
                } else {
                    params.addValue(pName, val);
                    summaryItems.add(fieldLabel + " is \"" + val + "\"");
                    return "lower(cast(" + expr + " as text)) = lower(:" + pName + ")";
                }

            case "NOT_EQUALS":
            case "IS_NOT":
                params.addValue(pName, val);
                summaryItems.add(fieldLabel + " is not \"" + val + "\"");
                return "(lower(cast(" + expr + " as text)) <> lower(:" + pName + ") or " + expr + " is null)";

            case "GREATER_THAN":
                try {
                    BigDecimal num = new BigDecimal(val);
                    params.addValue(pName, num);
                    summaryItems.add(fieldLabel + " > " + num);
                    return expr + " > :" + pName;
                } catch (Exception e) { return ""; }

            case "GREATER_THAN_OR_EQUAL":
                try {
                    BigDecimal num = new BigDecimal(val);
                    params.addValue(pName, num);
                    summaryItems.add(fieldLabel + " >= " + num);
                    return expr + " >= :" + pName;
                } catch (Exception e) { return ""; }

            case "LESS_THAN":
                try {
                    BigDecimal num = new BigDecimal(val);
                    params.addValue(pName, num);
                    summaryItems.add(fieldLabel + " < " + num);
                    return expr + " < :" + pName;
                } catch (Exception e) { return ""; }

            case "LESS_THAN_OR_EQUAL":
                try {
                    BigDecimal num = new BigDecimal(val);
                    params.addValue(pName, num);
                    summaryItems.add(fieldLabel + " <= " + num);
                    return expr + " <= :" + pName;
                } catch (Exception e) { return ""; }

            case "BETWEEN":
                if (field.kind() == CustomReportCatalog.Kind.NUMBER) {
                    try {
                        BigDecimal v1 = new BigDecimal(val);
                        BigDecimal v2 = new BigDecimal(rule.secondaryValue() != null ? rule.secondaryValue().trim() : val);
                        params.addValue(pName + "_1", v1);
                        params.addValue(pName + "_2", v2);
                        summaryItems.add(fieldLabel + " between " + v1 + " and " + v2);
                        return expr + " between :" + pName + "_1 and :" + pName + "_2";
                    } catch (Exception e) { return ""; }
                } else if (field.kind() == CustomReportCatalog.Kind.DATE) {
                    try {
                        LocalDate d1 = LocalDate.parse(val);
                        LocalDate d2 = LocalDate.parse(rule.secondaryValue() != null ? rule.secondaryValue().trim() : val);
                        params.addValue(pName + "_1", java.sql.Date.valueOf(d1));
                        params.addValue(pName + "_2", java.sql.Date.valueOf(d2));
                        summaryItems.add(fieldLabel + " between " + d1 + " and " + d2);
                        return expr + " between :" + pName + "_1 and :" + pName + "_2";
                    } catch (Exception e) { return ""; }
                }
                return "";

            case "CONTAINS":
                params.addValue(pName, "%" + escapeLike(val.toLowerCase(Locale.ROOT)) + "%");
                summaryItems.add(fieldLabel + " contains \"" + val + "\"");
                return "lower(cast(" + expr + " as text)) like :" + pName + " escape '!'";

            case "STARTS_WITH":
                params.addValue(pName, escapeLike(val.toLowerCase(Locale.ROOT)) + "%");
                summaryItems.add(fieldLabel + " starts with \"" + val + "\"");
                return "lower(cast(" + expr + " as text)) like :" + pName + " escape '!'";

            case "ENDS_WITH":
                params.addValue(pName, "%" + escapeLike(val.toLowerCase(Locale.ROOT)));
                summaryItems.add(fieldLabel + " ends with \"" + val + "\"");
                return "lower(cast(" + expr + " as text)) like :" + pName + " escape '!'";

            case "IS_EMPTY":
                summaryItems.add(fieldLabel + " is empty");
                return "(" + expr + " is null or trim(cast(" + expr + " as text)) = '')";

            case "IS_NOT_EMPTY":
                summaryItems.add(fieldLabel + " is not empty");
                return "(" + expr + " is not null and trim(cast(" + expr + " as text)) <> '')";

            case "IS_ANY_OF":
                List<String> list = rule.values() != null && !rule.values().isEmpty()
                        ? rule.values()
                        : Arrays.stream(val.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                if (list.isEmpty()) return "";
                params.addValue(pName, list);
                summaryItems.add(fieldLabel + " in [" + String.join(", ", list) + "]");
                return expr + " in (:" + pName + ")";

            case "IS_NONE_OF":
                List<String> noneList = rule.values() != null && !rule.values().isEmpty()
                        ? rule.values()
                        : Arrays.stream(val.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                if (noneList.isEmpty()) return "";
                params.addValue(pName, noneList);
                summaryItems.add(fieldLabel + " not in [" + String.join(", ", noneList) + "]");
                return "(" + expr + " not in (:" + pName + ") or " + expr + " is null)";

            case "BEFORE":
                try {
                    LocalDate d = LocalDate.parse(val);
                    params.addValue(pName, java.sql.Date.valueOf(d));
                    summaryItems.add(fieldLabel + " before " + d);
                    return expr + " < :" + pName;
                } catch (Exception e) { return ""; }

            case "AFTER":
                try {
                    LocalDate d = LocalDate.parse(val);
                    params.addValue(pName, java.sql.Date.valueOf(d));
                    summaryItems.add(fieldLabel + " after " + d);
                    return expr + " > :" + pName;
                } catch (Exception e) { return ""; }

            case "ON":
                try {
                    LocalDate d = LocalDate.parse(val);
                    params.addValue(pName, java.sql.Date.valueOf(d));
                    summaryItems.add(fieldLabel + " on " + d);
                    return expr + " = :" + pName;
                } catch (Exception e) { return ""; }

            case "YES":
                summaryItems.add(fieldLabel + " = Yes");
                return expr + " = true";

            case "NO":
                summaryItems.add(fieldLabel + " = No");
                return "(" + expr + " = false or " + expr + " is null)";

            default:
                return "";
        }
    }

    private static String escapeLike(String input) {
        return input.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private String buildInternshipFromClause() {
        return """
                from internship_details i
                join internship_onboarding o on o.id = i.onboarding_id
                join users u on u.id = o.student_user_id
                left join student_accounts sa on sa.user_id = u.id
                left join student_profiles p on p.user_id = u.id
                left join academic_sessions acs on acs.id = u.academic_session_id
                left join mentor_student_assignments a on a.student_user_id = u.id and a.active = true
                left join users mu on mu.id = a.mentor_user_id
                left join mentor_profiles mp on mp.user_id = a.mentor_user_id
                left join internship_locations loc on loc.internship_id = i.id
                left join (
                    select student_user_id, internship_id,
                           count(case when attendance_result = 'PRESENT' then 1 end) as present_days,
                           count(case when attendance_result = 'ABSENT' then 1 end) as absent_days,
                           count(case when attendance_result in ('PRESENT', 'ABSENT') then 1 end) as total_days,
                           round(count(case when attendance_result = 'PRESENT' then 1 end) * 100.0 / nullif(count(case when attendance_result in ('PRESENT', 'ABSENT') then 1 end), 0), 1) as attendance_percentage,
                           count(case when submitted_late = true then 1 end) as late_submissions
                    from attendance_records
                    where deleted_at is null
                    group by student_user_id, internship_id
                ) att on att.student_user_id = u.id and att.internship_id = i.id
                left join (
                    select onboarding_id, count(*) as docs_count
                    from internship_documents
                    where storage_key is not null
                    group by onboarding_id
                ) docs on docs.onboarding_id = o.id
                left join (
                    select wp_p.internship_id, count(*) as weekly_count
                    from weekly_progress_reports wp_rep
                    join weekly_internship_periods wp_p on wp_p.id = wp_rep.period_id
                    group by wp_p.internship_id
                ) wp on wp.internship_id = i.id
                left join (
                    select student_internship_id, count(*) as monthly_count
                    from monthly_progress_reports
                    where deleted_at is null and review_status = 'APPROVED'
                    group by student_internship_id
                ) mp_rep on mp_rep.student_internship_id = i.id
                """;
    }

    private String buildStudentFromClause() {
        return """
                from users u
                left join student_accounts sa on sa.user_id = u.id
                left join student_profiles p on p.user_id = u.id
                left join academic_sessions acs on acs.id = u.academic_session_id
                left join mentor_student_assignments a on a.student_user_id = u.id and a.active = true
                left join users mu on mu.id = a.mentor_user_id
                left join mentor_profiles mp on mp.user_id = a.mentor_user_id
                left join internship_onboarding o on o.student_user_id = u.id and o.internship_number = 1
                left join internship_details i on i.onboarding_id = o.id
                left join internship_locations loc on loc.internship_id = i.id
                left join (
                    select student_user_id,
                           count(case when attendance_result = 'PRESENT' then 1 end) as present_days,
                           count(case when attendance_result = 'ABSENT' then 1 end) as absent_days,
                           count(case when attendance_result in ('PRESENT', 'ABSENT') then 1 end) as total_days,
                           round(count(case when attendance_result = 'PRESENT' then 1 end) * 100.0 / nullif(count(case when attendance_result in ('PRESENT', 'ABSENT') then 1 end), 0), 1) as attendance_percentage,
                           count(case when submitted_late = true then 1 end) as late_submissions
                    from attendance_records
                    where deleted_at is null
                    group by student_user_id
                ) att on att.student_user_id = u.id
                left join (
                    select onboarding_id, count(*) as docs_count
                    from internship_documents
                    where storage_key is not null
                    group by onboarding_id
                ) docs on docs.onboarding_id = o.id
                left join (
                    select wp_p.internship_id, count(*) as weekly_count
                    from weekly_progress_reports wp_rep
                    join weekly_internship_periods wp_p on wp_p.id = wp_rep.period_id
                    group by wp_p.internship_id
                ) wp on wp.internship_id = i.id
                left join (
                    select student_internship_id, count(*) as monthly_count
                    from monthly_progress_reports
                    where deleted_at is null and review_status = 'APPROVED'
                    group by student_internship_id
                ) mp_rep on mp_rep.student_internship_id = i.id
                """;
    }
}
