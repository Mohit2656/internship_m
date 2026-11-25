package edu.institution.ims.dashboard.report;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static edu.institution.ims.dashboard.report.DashboardReportCatalog.Kind.*;

/**
 * Report types extend the dashboard's authorization, filters and streaming
 * POI/CSV writer.
 */
@Service
@PreAuthorize("hasAnyRole('ADMIN','MENTOR')")
public class InstitutionalReportService {
    private final DashboardReportRepository repository;
    private final DashboardReportCatalog catalog;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public InstitutionalReportService(DashboardReportRepository repository, DashboardReportCatalog catalog,
            NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Type(String id, String title, String description, boolean available, String historicalSheet,
            List<DashboardReportCatalog.Field> fields) {
    }

    public record Options(List<Type> types, List<DashboardReportCatalog.Filter> filters, String scope, String date) {
    }

    public record SubTable(String id, String title, List<DashboardReportCatalog.Field> fields,
            List<Map<String, Object>> rows, Map<String, Object> summary) {
    }

    public record Table(List<DashboardReportCatalog.Field> fields, List<Map<String, Object>> rows, long totalRows,
            int page, Map<String, Object> summary, String title, List<SubTable> subTables) {
        public Table(List<DashboardReportCatalog.Field> fields, List<Map<String, Object>> rows, long totalRows,
                int page, Map<String, Object> summary, String title) {
            this(fields, rows, totalRows, page, summary, title, List.of());
        }
    }

    private static DashboardReportCatalog.Field field(String id, String label, DashboardReportCatalog.Kind kind) {
        return new DashboardReportCatalog.Field(id, label, "Report columns", kind, true, null, false);
    }

    private static List<DashboardReportCatalog.Field> fields(String... pairs) {
        List<DashboardReportCatalog.Field> result = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.add(field(pairs[i], pairs[i + 1],
                    pairs[i].equals("category") || pairs[i].equals("company") || pairs[i].equals("parameter")
                            || pairs[i].equals("range") || pairs[i].equals("metric") ? TEXT : NUMBER));
        }
        return result;
    }

    public Options options(UserPrincipal user) {
        List<Type> types = new ArrayList<>();
        if (catalog.isAdmin(user)) {
            // Exactly the 10 Quick Reports requested from the III Cell document
            types.add(type("OVERALL", "Overall Internship Summary Report",
                    "Source categories and student distribution across Department, CDC, and Self.",
                    fields("category", "Internship Category", "total", "No. of Students", "percentage", "% of Total")));
            types.add(type("STIPEND", "Stipend Analysis Report",
                    "Category-wise stipend analysis and stipend range distribution.", fields("category", "Category",
                            "paid", "Stipend Students", "unpaid", "Non-Stipend Students", "total", "Total")));
            types.add(type("COMPANY", "Company-wise Student Distribution",
                    "Student distribution across companies by internship category.", fields("company", "Company",
                            "department", "Department", "cdc", "CDC", "self", "Self", "total", "Total")));
            types.add(type("COMPANY_STIPEND", "Company + Stipend Analysis",
                    "Company counts, stipend status and recorded placement offers.",
                    fields("company", "Company", "total", "Total Students", "paid", "Stipend", "unpaid", "Non-Stipend",
                            "offers", "Placement Offer")));
            types.add(type("COMPARISON", "Department / CDC / Self Comparison",
                    "Comprehensive parameter comparison across Department, CDC, and Self categories.",
                    fields("parameter", "Parameter", "department", "Department", "cdc", "CDC", "self", "Self", "total",
                            "Total")));
            types.add(type("PLACEMENT", "Placement Offer Analysis",
                    "Category-wise and company-wise placement / PPO offers analysis.", fields("category", "Category",
                            "total", "Total Students", "offers", "Placement Offer", "noOffers", "No Placement Offer")));
            types.add(type("STATUS_ANALYSIS", "Internship Status Analysis",
                    "Internship lifecycle status (Not Started, Ongoing, Completed, Pending) across categories.",
                    fields("category", "Category", "notStarted", "Not Started", "ongoing", "Ongoing", "completed",
                            "Completed", "pending", "Pending", "total", "Total")));
            types.add(type("PARTICIPATION", "Student Working / Internship Participation",
                    "Student working status tracking (Selected, Joined, Not Joined, Completed).",
                    fields("category", "Category", "selected", "Selected", "joined", "Joined", "notJoined",
                            "Not Joined", "completed", "Completed", "total", "Total")));
            types.add(type("ACTIVE_ATTENDANCE", "Attendance / Active Internship Report",
                    "Active internship monitoring, regular attendees, defaulters, and active intern list.",
                    fields("metric", "Attendance Metric", "count", "Number of Students")));
            types.add(type("MASTER_ANALYTICAL", "Master Analytical Dashboard Report",
                    "Consolidated executive report with top key metrics and analytical sub-tables A–H.",
                    fields("parameter", "Metric", "department", "Department", "cdc", "CDC", "self", "Self", "total",
                            "Total")));
        } else {
            // Mentor report types
            types.add(new Type("COMPLETIONS", "Internship Completions",
                    "Official IIIC completion decisions and pending/in-progress internships by assigned academic session.",
                    true, "Internship Completions", completionFields()));
            types.add(new Type("ROSTER", "Student / Faculty Mentor Allotment",
                    "Current student, company, source, stipend and faculty mentor details.", true, "Main report ",
                    rosterFields()));
            types.add(type("OVERALL", "Overall Internship Summary",
                    "Source categories, including students without a recorded source.", fields("category", "Category",
                            "total", "Number of Students", "percentage", "Percentage of Total")));
            types.add(type("STIPEND", "Stipend Analysis", "Paid, unpaid and unavailable stipend data by source.",
                    fields("category", "Category", "paid", "Stipend Students", "unpaid", "Non-Stipend Students",
                            "unknown", "Stipend Unavailable", "total", "Total")));
            types.add(type("COMPANY", "Company-wise Student Distribution",
                    "Companies and student counts by internship source.",
                    fields("company", "Company", "department", "Department", "cdc", "CDC", "self", "Self", "unknown",
                            "Source Unavailable", "total", "Total")));
            types.add(type("COMPANY_STIPEND", "Company + Stipend Analysis",
                    "Company counts, stipend status and recorded full-time offers.",
                    fields("company", "Company", "total", "Total Students", "paid", "Stipend Students", "unpaid",
                            "Non-Stipend Students", "offers", "Placement Offers")));
            types.add(type("COMPARISON", "Department / CDC / Self Comparison",
                    "Available source comparisons. Diary issued is not diary submitted.",
                    fields("parameter", "Parameter", "department", "Department", "cdc", "CDC", "self", "Self",
                            "unknown", "Source Unavailable", "total", "Total")));
            types.add(type("PLACEMENT", "Placement Offer Analysis",
                    "Uses the recorded full-time employment offered flag; unknowns remain separate.",
                    fields("category", "Category", "total", "Total Students", "offers", "Placement Offer", "noOffers",
                            "No Placement Offer", "unknown", "Offer Unavailable")));
            types.add(type("WORKFLOW", "Internship Status Analysis",
                    "Actual onboarding workflow states; not a completion or joining status.",
                    fields("category", "Category", "total", "Students")));
            types.add(new Type("MONTHLY_PROGRESS", "Monthly Progress Reports",
                    "Uploaded reports and explicit faculty mentor review decisions.", true, "Attedance report ",
                    monthlyFields()));
            types.add(new Type("ATTENDANCE", "Attendance Records",
                    "Daily attendance including automatic absences after the submission deadline.", true,
                    "Attedance report ", attendanceFields()));
            types.add(new Type("ATTENDANCE_SUMMARY", "Student Attendance Summary",
                    "Daily present/absent counts and percentages among stored records.", true, "Attedance report ",
                    summaryFields()));
            types.add(new Type("DIARY_ISSUANCE", "Diary Issuance", "Uses the existing diary-issued flag.", true,
                    "DATA reguired  from students", fields("category", "Category", "issued", "Diary Issued",
                            "notIssued", "Diary Not Issued", "total", "Total")));
        }

        List<DashboardReportCatalog.Filter> filters = new ArrayList<>(catalog.filters(user));
        filters.removeIf(
                f -> Set.of("attendanceStatus", "verificationStatus", "historyFrom", "historyTo", "attendanceDate")
                        .contains(f.id()));
        filters.add(new DashboardReportCatalog.Filter("historyFrom", "Attendance from", "date", List.of(),
                "Inclusive attendance record date"));
        filters.add(new DashboardReportCatalog.Filter("historyTo", "Attendance through", "date", List.of(),
                "Inclusive attendance record date"));
        filters.add(
                new DashboardReportCatalog.Filter("attendanceStatus", "Attendance status", "select",
                        List.of(new DashboardReportCatalog.Choice("PRESENT", "Present"),
                                new DashboardReportCatalog.Choice("ABSENT", "Absent")),
                        "Filters stored attendance records"));
        filters.add(new DashboardReportCatalog.Filter("verificationStatus", "Liveness status", "select",
                List.of(new DashboardReportCatalog.Choice("VERIFIED", "Liveness verified"),
                        new DashboardReportCatalog.Choice("FAILED", "Liveness failed"),
                        new DashboardReportCatalog.Choice("NOT_PERFORMED", "Liveness not performed"),
                        new DashboardReportCatalog.Choice("HISTORICAL", "Historical verification"),
                        new DashboardReportCatalog.Choice("NOT_APPLICABLE",
                                "Automatic absence — liveness not applicable")),
                "Filters current liveness outcomes; historical location decisions are not reassessed"));
        filters.add(new DashboardReportCatalog.Filter("monthlyStatus", "Monthly report review", "select",
                List.of(new DashboardReportCatalog.Choice("PENDING_MENTOR_REVIEW", "Pending faculty mentor review"),
                        new DashboardReportCatalog.Choice("APPROVED", "Approved"),
                        new DashboardReportCatalog.Choice("REJECTED", "Rejected")),
                "Monthly progress only"));
        if (!catalog.isAdmin(user)) {
            filters.add(new DashboardReportCatalog.Filter("paid", "Stipend", "select",
                    List.of(new DashboardReportCatalog.Choice("PAID", "Paid"),
                            new DashboardReportCatalog.Choice("UNPAID", "Unpaid")),
                    ""));
            filters.add(new DashboardReportCatalog.Filter("fullTimeOffered", "Full-time employment offered", "select",
                    List.of(new DashboardReportCatalog.Choice("YES", "Yes"),
                            new DashboardReportCatalog.Choice("NO", "No")),
                    ""));
        }
        return new Options(types, filters,
                catalog.isAdmin(user) ? "Authorized institutional students" : "Only your currently assigned students",
                LocalDate.now(clock).toString());
    }

    private List<Type> allAvailableTypes(UserPrincipal user) {
        List<Type> list = new ArrayList<>(options(user).types());
        // Ensure standard legacy and alias types can always be resolved
        Map<String, Type> standard = new LinkedHashMap<>();
        standard.put("COMPLETIONS", new Type("COMPLETIONS", "Internship Completions",
                "Official IIIC completion decisions", true, "Internship Completions", completionFields()));
        standard.put("ROSTER", new Type("ROSTER", "Student / Faculty Mentor Allotment", "Current student allotment",
                true, "Main report ", rosterFields()));
        standard.put("MONTHLY_PROGRESS", new Type("MONTHLY_PROGRESS", "Monthly Progress Reports", "Uploaded reports",
                true, "Attedance report ", monthlyFields()));
        standard.put("ATTENDANCE", new Type("ATTENDANCE", "Attendance Records", "Daily attendance", true,
                "Attedance report ", attendanceFields()));
        standard.put("ATTENDANCE_SUMMARY", new Type("ATTENDANCE_SUMMARY", "Student Attendance Summary",
                "Attendance counts", true, "Attedance report ", summaryFields()));
        standard.put("DIARY_ISSUANCE",
                new Type("DIARY_ISSUANCE", "Diary Issuance", "Diary issued flag", true, "DATA reguired  from students",
                        fields("category", "Category", "issued", "Diary Issued", "notIssued", "Diary Not Issued",
                                "total", "Total")));
        standard.put("WORKFLOW", type("WORKFLOW", "Internship Status Analysis", "Workflow states",
                fields("category", "Category", "total", "Students")));
        standard.put("OVERALL_SUMMARY", type("OVERALL_SUMMARY", "Overall Internship Summary Report",
                "Overall internship summary",
                fields("category", "Internship Category", "total", "No. of Students", "percentage", "% of Total")));
        standard.put("STIPEND_ANALYSIS",
                type("STIPEND_ANALYSIS", "Stipend Analysis Report", "Stipend analysis", fields("category", "Category",
                        "paid", "Stipend Students", "unpaid", "Non-Stipend Students", "total", "Total")));
        standard.put("COMPANY_DISTRIBUTION",
                type("COMPANY_DISTRIBUTION", "Company-wise Student Distribution", "Company distribution",
                        fields("company", "Company", "department", "Department", "cdc", "CDC", "self", "Self", "total",
                                "Total")));
        standard.put("CATEGORY_COMPARISON",
                type("CATEGORY_COMPARISON", "Department / CDC / Self Comparison", "Category comparison",
                        fields("parameter", "Parameter", "department", "Department", "cdc", "CDC", "self", "Self",
                                "total", "Total")));
        standard.put("PLACEMENT_ANALYSIS",
                type("PLACEMENT_ANALYSIS", "Placement Offer Analysis", "Placement analysis",
                        fields("category", "Category", "total", "Total Students", "offers", "Placement Offer",
                                "noOffers", "No Placement Offer")));
        standard.put("ACTIVE_ATTENDANCE", type("ACTIVE_ATTENDANCE", "Attendance / Active Internship Report",
                "Active internship report", fields("metric", "Attendance Metric", "count", "Number of Students")));
        standard.put("STATUS_ANALYSIS",
                type("STATUS_ANALYSIS", "Internship Status Analysis", "Internship lifecycle status",
                        fields("category", "Category", "notStarted", "Not Started", "ongoing", "Ongoing", "completed",
                                "Completed", "pending", "Pending", "total", "Total")));
        standard.put("PARTICIPATION",
                type("PARTICIPATION", "Student Working / Internship Participation", "Student participation",
                        fields("category", "Category", "selected", "Selected", "joined", "Joined", "notJoined",
                                "Not Joined", "completed", "Completed", "total", "Total")));
        standard.put("MASTER_ANALYTICAL",
                type("MASTER_ANALYTICAL", "Master Analytical Dashboard Report", "Master analytical dashboard",
                        fields("parameter", "Metric", "department", "Department", "cdc", "CDC", "self", "Self", "total",
                                "Total")));
        standard.put("SEMINAR", new Type("SEMINAR", "Project Seminar Evaluation", "Unavailable", false,
                "Marks report format ", List.of()));
        standard.put("EVALUATION", new Type("EVALUATION", "Evaluation and Diary Submission", "Unavailable", false,
                "Marks report format ", List.of()));

        for (var entry : standard.entrySet()) {
            if (list.stream().noneMatch(t -> t.id().equals(entry.getKey()))) {
                list.add(entry.getValue());
            }
        }
        return list;
    }

    private Type type(String id, String title, String description, List<DashboardReportCatalog.Field> fields) {
        return new Type(id, title, description, true, "CDC self  Dept wise student rep", fields);
    }

    private List<DashboardReportCatalog.Field> identity() {
        return List.of(field("studentId", "Student account ID", TEXT), field("uid", "UID", TEXT),
                field("studentName", "Student", TEXT), field("branch", "Branch", TEXT),
                field("mentorName", "Faculty Mentor", TEXT), field("company", "Company", TEXT),
                field("workflow", "Internship workflow", TEXT));
    }

    private List<DashboardReportCatalog.Field> completionFields() {
        var f = new ArrayList<>(identity());
        f.addAll(List.of(field("academicSession", "Academic session", TEXT), field("startDate", "Start date", DATE),
                field("endDate", "End date", DATE), field("completionStatus", "Completion status", TEXT),
                field("completionDate", "Completion date", DATETIME),
                field("completionReviewer", "IIIC reviewer", TEXT),
                field("completionRemarks", "Completion remarks", TEXT)));
        return f;
    }

    private List<DashboardReportCatalog.Field> monthlyFields() {
        var f = new ArrayList<>(identity());
        f.add(field("recordId", "Monthly record ID", NUMBER));
        f.add(field("monthNumber", "Report Month", NUMBER));
        f.addAll(List.of(field("reviewStatus", "Review status", TEXT), field("revision", "Revision", NUMBER),
                field("periodStart", "Period start", DATE), field("periodEnd", "Period end", DATE),
                field("workDescription", "Work description", TEXT),
                field("submittedAt", "Last updated by student", DATETIME), field("reviewerId", "Reviewer ID", NUMBER),
                field("reviewedAt", "Reviewed at", DATETIME), field("rejectionReason", "Rejection reason", TEXT),
                field("imageSha256", "Image SHA-256", TEXT)));
        return f;
    }

    private List<DashboardReportCatalog.Field> attendanceFields() {
        List<DashboardReportCatalog.Field> f = new ArrayList<>(identity());
        f.add(field("recordId", "Attendance record ID", NUMBER));
        f.add(field("automaticAbsent", "Automatically recorded absence", BOOLEAN));
        f.add(field("generatedAt", "Absence automatically recorded at", DATETIME));
        f.add(field("imageSizeBytes", "Camera evidence bytes", NUMBER));
        f.add(field("metadataConsistent", "Metadata consistent", BOOLEAN));
        f.addAll(List.of(field("attendanceLatitude", "Submission latitude", NUMBER),
                field("attendanceLongitude", "Submission longitude", NUMBER),
                field("gpsAccuracyMeters", "Submission GPS accuracy (m)", NUMBER)));
        f.addAll(List.of(field("locationVerificationStatus", "GPS location verification", TEXT),
                field("locationCapturedAt", "GPS location captured at", DATETIME),
                field("workplaceRevision", "Approved workplace revision", NUMBER),
                field("locationDistanceMeters", "Distance from approved workplace (m)", NUMBER),
                field("geofenceRadiusMeters", "Workplace radius (m)", NUMBER)));
        f.addAll(List.of(field("date", "Attendance date", DATE),
                field("submittedAt", "Last updated by student", DATETIME), field("result", "Attendance status", TEXT),
                field("verification", "Liveness status", TEXT), field("submissionType", "Submission type", TEXT),
                field("workMode", "Expected work mode", TEXT), field("reason", "Liveness reason", TEXT),
                field("liveness", "Current liveness verified", BOOLEAN)));
        return f;
    }

    private List<DashboardReportCatalog.Field> rosterFields() {
        List<DashboardReportCatalog.Field> f = new ArrayList<>(identity());
        f.addAll(List.of(field("source", "Internship source", TEXT), field("stipend", "Monthly stipend (INR)", NUMBER),
                field("offer", "Full-time offer", BOOLEAN),
                field("profileLastStudentUpdatedAt", "Profile last updated by student", DATETIME),
                field("internshipLastStudentUpdatedAt", "Internship last updated by student", DATETIME)));
        return f;
    }

    private List<DashboardReportCatalog.Field> summaryFields() {
        List<DashboardReportCatalog.Field> f = new ArrayList<>(identity());
        f.addAll(fields("total", "Stored Records", "present", "Present", "absent", "Absent", "verified",
                "Liveness verified", "unverified", "Liveness failed", "notPerformed", "Liveness not performed",
                "historical", "Historical verification", "percentage", "Present / Stored Records (%)"));
        return f;
    }

    private record Query(Type type, List<DashboardReportCatalog.Field> fields, String sql, MapSqlParameterSource params,
            long totalStudents, String baseSql) {
    }

    private Query query(UserPrincipal user, ReportTableRequest request) {
        String reqType = request.reportType();
        String resolvedType = switch (reqType) {
            case "OVERALL_SUMMARY" -> "OVERALL";
            case "STIPEND_ANALYSIS" -> "STIPEND";
            case "COMPANY_DISTRIBUTION" -> "COMPANY";
            case "CATEGORY_COMPARISON" -> "COMPARISON";
            case "PLACEMENT_ANALYSIS" -> "PLACEMENT";
            case "ACTIVE_ATTENDANCE" -> "ACTIVE_ATTENDANCE";
            default -> reqType;
        };

        var allTypes = allAvailableTypes(user);
        Type type = allTypes.stream().filter(t -> t.id().equals(resolvedType)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported report type: " + reqType));
        if (!type.available())
            throw new IllegalArgumentException(type.description());

        boolean attendance = type.id().startsWith("ATTENDANCE") || type.id().equals("ACTIVE_ATTENDANCE");

        var options = options(user);
        Map<String, String> filters = request.filters() == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.filters());
        for (var entry : filters.entrySet()) {
            var f = options.filters().stream().filter(v -> v.id().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported report filter: " + entry.getKey()));
            entry.setValue(DashboardReportCatalog.filterValue(f, entry.getValue()));
        }
        filters.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        for (String id : List.of("historyFrom", "historyTo", "attendanceStatus", "verificationStatus")) {
            if (!attendance && filters.containsKey(id) && !filters.get(id).isBlank()) {
                throw new IllegalArgumentException("Attendance filters require an attendance report");
            }
        }
        Map<String, String> studentFilters = new LinkedHashMap<>(filters);
        for (String id : List.of("historyFrom", "historyTo", "attendanceStatus", "verificationStatus"))
            studentFilters.remove(id);

        String paid = studentFilters.remove("paid"), offer = studentFilters.remove("fullTimeOffered");
        String monthlyWindow = "";
        String monthlyStatus = studentFilters.remove("monthlyStatus");
        if (monthlyStatus != null && !monthlyStatus.isBlank()) {
            if (!type.id().equals("MONTHLY_PROGRESS"))
                throw new IllegalArgumentException("Monthly review filters require a monthly report");
            monthlyWindow = " and m.review_status = :monthlyStatus";
        }

        var scope = repository.scope(user, studentFilters);
        String studentSql = repository.reportStudentsSql(scope);
        MapSqlParameterSource params = new MapSqlParameterSource(scope.params().getValues());
        params.addValue("today", java.sql.Date.valueOf(edu.institution.ims.internship.InternshipLifecycle.today(clock)));
        if (paid != null && !paid.isBlank())
            studentSql += " and i.stipend_per_month " + (paid.equals("PAID") ? "> 0" : "= 0");
        if (offer != null && !offer.isBlank()) {
            studentSql += " and i.full_time_employment_offered = :reportOffer";
            params.addValue("reportOffer", offer.equals("YES"));
        }
        if (request.studentIds() != null && !request.studentIds().isEmpty()) {
            if (new HashSet<>(request.studentIds()).size() != request.studentIds().size())
                throw new IllegalArgumentException("Duplicate student IDs");
            params.addValue("reportIds", request.studentIds());
            var unfiltered = repository.scope(user, Map.of());
            Long authorized = jdbc.queryForObject(
                    "select count(*) from (" + repository.reportStudentsSql(unfiltered)
                            + ") authorized where sid in (:reportIds)",
                    new MapSqlParameterSource(unfiltered.params().getValues()).addValue("reportIds",
                            request.studentIds()),
                    Long.class);
            if (authorized == null || authorized != request.studentIds().size())
                throw new AccessDeniedException("One or more students are outside your authorized report scope");
            studentSql += " and u.id in (:reportIds)";
        }
        if (monthlyStatus != null && !monthlyStatus.isBlank())
            params.addValue("monthlyStatus", monthlyStatus);

        String base = "(" + studentSql + ") s";
        String window = "";
        LocalDate from = null, to = null;
        try {
            if (filters.containsKey("historyFrom"))
                from = DashboardReportRepository.date(filters.get("historyFrom"));
            if (filters.containsKey("historyTo"))
                to = DashboardReportRepository.date(filters.get("historyTo"));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Attendance dates must use YYYY-MM-DD");
        }
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException("Attendance from must be on or before through");
        if (from != null) {
            window += " and ar.attendance_date >= :reportFrom";
            params.addValue("reportFrom", java.sql.Date.valueOf(from));
        }
        if (to != null) {
            window += " and ar.attendance_date <= :reportTo";
            params.addValue("reportTo", java.sql.Date.valueOf(to));
        }
        if (filters.containsKey("attendanceStatus") && !filters.get("attendanceStatus").isBlank()) {
            window += " and ar.attendance_result = :attendanceStatus";
            params.addValue("attendanceStatus", filters.get("attendanceStatus"));
        }
        if (filters.containsKey("verificationStatus") && !filters.get("verificationStatus").isBlank()) {
            window += " and (" + AttendanceLivenessSql.status("ar", "e") + ") = :verificationStatus";
            params.addValue("verificationStatus", filters.get("verificationStatus"));
        }

        String liveness = AttendanceLivenessSql.status("ar", "e");
        String summaryAttendance = "(select ar.*, " + liveness
                + " as liveness_outcome from attendance_records ar left join attendance_evidence e on e.attendance_id=ar.id where ar.deleted_at is null"
                + window + ") ar";

        String sql = switch (type.id()) {
            case "MONTHLY_PROGRESS" ->
                "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,m.id as recordId,m.report_month_number as monthNumber,m.review_status as reviewStatus,m.revision,m.period_start as periodStart,m.period_end as periodEnd,m.work_description as workDescription,m.submitted_at as submittedAt,m.reviewer_id as reviewerId,m.reviewed_at as reviewedAt,m.rejection_reason as rejectionReason,m.image_sha256 as imageSha256 from "
                        + base
                        + " join monthly_progress_reports m on m.student_internship_id=s.internshipId and m.deleted_at is null where 1=1"
                        + monthlyWindow;
            case "COMPLETIONS" ->
                "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,s.academicSession,s.startDate,s.endDate,s.completionStatus,s.completionDate,s.completionReviewer,s.completionRemarks from "
                        + base + " where s.internshipId is not null";
            case "ROSTER" ->
                "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,s.source,s.stipend,s.offer,s.profileLastStudentUpdatedAt,s.internshipLastStudentUpdatedAt from "
                        + base;
            case "OVERALL" ->
                "select source as category,count(*) as total,cast(cast(count(*) as decimal(18,4))*100/nullif((select count(*) from "
                        + base + "),0) as decimal(12,4)) as percentage from " + base + " group by source";
            case "STIPEND" -> "select source as category,count(*) as total," + sums()
                    + ",sum(case when stipend is null then 1 else 0 end) as \"unknown\" from " + base
                    + " group by source";
            case "COMPANY" ->
                "select company," + sources("1=1") + " from " + base + " where company is not null group by company";
            case "COMPANY_STIPEND" -> "select company,count(*) as total," + sums()
                    + ",sum(case when offer=true then 1 else 0 end) as offers from " + base
                    + " where company is not null group by company";
            case "PLACEMENT" ->
                "select source as category,count(*) as total,sum(case when offer=true then 1 else 0 end) as offers,sum(case when offer=false then 1 else 0 end) as noOffers,sum(case when offer is null then 1 else 0 end) as \"unknown\" from "
                        + base + " group by source";
            case "WORKFLOW" -> "select workflow as category,count(*) as total from " + base + " group by workflow";
            case "DIARY_ISSUANCE" ->
                "select source as category,count(*) as total,sum(case when diary=true then 1 else 0 end) as issued,sum(case when diary=false or diary is null then 1 else 0 end) as notIssued from "
                        + base + " group by source";
            case "COMPARISON" -> comparison(base);
            case "ATTENDANCE" ->
                "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,ar.id as recordId,case when e.latitude between -90 and 90 and e.longitude between -180 and 180 then e.latitude end as attendanceLatitude,case when e.latitude between -90 and 90 and e.longitude between -180 and 180 then e.longitude end as attendanceLongitude,case when e.latitude between -90 and 90 and e.longitude between -180 and 180 and e.gps_accuracy_meters between 0 and 999999.99 then e.gps_accuracy_meters end as gpsAccuracyMeters,ar.attendance_date as date,case when ar.submission_type<>'AUTO_ABSENT' then ar.server_submitted_at end as submittedAt,case when ar.submission_type='AUTO_ABSENT' then true else false end as automaticAbsent,case when ar.submission_type='AUTO_ABSENT' then ar.server_submitted_at end as generatedAt,ar.attendance_result as result,"
                        + liveness
                        + " as verification,ar.submission_type as submissionType,e.expected_work_mode as workMode,"
                        + AttendanceLivenessSql.reason("ar", "e") + " as reason,case when (" + liveness
                        + ")='VERIFIED' then true when (" + liveness
                        + ")='FAILED' then false else null end as liveness,e.image_size_bytes as imageSizeBytes,e.metadata_consistent as metadataConsistent,e.location_verification_status as locationVerificationStatus,e.location_captured_at as locationCapturedAt,e.workplace_revision as workplaceRevision,e.distance_meters as locationDistanceMeters,e.geofence_radius_meters as geofenceRadiusMeters from "
                        + base
                        + " join attendance_records ar on ar.student_user_id=s.sid and ar.internship_id=s.internshipId and ar.deleted_at is null left join attendance_evidence e on e.attendance_id=ar.id where 1=1"
                        + window;
            case "ATTENDANCE_SUMMARY" ->
                "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,count(ar.id) as total,sum(case when ar.attendance_result='PRESENT' then 1 else 0 end) as present,sum(case when ar.attendance_result='ABSENT' then 1 else 0 end) as absent,sum(case when ar.liveness_outcome='VERIFIED' then 1 else 0 end) as verified,sum(case when ar.liveness_outcome='FAILED' then 1 else 0 end) as unverified,sum(case when ar.liveness_outcome='NOT_PERFORMED' then 1 else 0 end) as notPerformed,sum(case when ar.liveness_outcome='HISTORICAL' then 1 else 0 end) as historical,cast(100.0*sum(case when ar.attendance_result='PRESENT' then 1 else 0 end)/nullif(count(ar.id),0) as decimal(12,4)) as percentage from "
                        + base + " left join " + summaryAttendance
                        + " on ar.student_user_id=s.sid and ar.internship_id=s.internshipId"
                        + " group by s.sid,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow";
            default ->
                "select sid, uid, studentName, branch, mentorName, company, workflow, source, stipend, offer, diary, completionStatus, startDate, endDate from "
                        + base;
        };

        List<DashboardReportCatalog.Field> selected = type.fields();
        if (request.fields() != null && !request.fields().isEmpty()) {
            if (new HashSet<>(request.fields()).size() != request.fields().size())
                throw new IllegalArgumentException("Duplicate report fields");
            selected = request.fields().stream()
                    .map(id -> type.fields().stream().filter(f -> f.id().equals(id)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Unsupported report field: " + id)))
                    .toList();
        }

        long studentCount = Objects.requireNonNull(
                jdbc.queryForObject("select count(*) from (" + studentSql + ") report_students", params, Long.class));
        return new Query(type, selected, sql, params, studentCount, studentSql);
    }

    private static String sums() {
        return "sum(case when stipend>0 then 1 else 0 end) as paid,sum(case when stipend=0 then 1 else 0 end) as unpaid";
    }

    private static String sources(String condition) {
        return "coalesce(sum(case when source='DEPARTMENT' and " + condition
                + " then 1 else 0 end),0) as department,coalesce(sum(case when source='CDC' and " + condition
                + " then 1 else 0 end),0) as cdc,coalesce(sum(case when source='SELF' and " + condition
                + " then 1 else 0 end),0) as self,coalesce(sum(case when source='UNRECORDED' and " + condition
                + " then 1 else 0 end),0) as \"unknown\",coalesce(sum(case when " + condition
                + " then 1 else 0 end),0) as total";
    }

    private static String comparison(String base) {
        return List.of(new String[] { "Total Students", "1=1" }, new String[] { "Stipend Students", "stipend>0" },
                new String[] { "Non-Stipend Students", "stipend=0" }, new String[] { "Placement Offers", "offer=true" },
                new String[] { "Diary Issued", "diary=true" },
                new String[] { "Details Complete", "workflow='INTERNSHIP_DETAILS_COMPLETE'" }).stream()
                .map(p -> "select '" + p[0] + "' as parameter," + sources(p[1]) + " from " + base)
                .collect(Collectors.joining(" union all "));
    }

    private List<Map<String, Object>> rows(Query query, int offset, int size) {
        String order = query.type().id().equals("ATTENDANCE") ? "date,studentId,recordId"
                : query.type().id().equals("MONTHLY_PROGRESS") ? "studentId,monthNumber,recordId"
                        : Set.of("ATTENDANCE_SUMMARY", "ROSTER", "COMPLETIONS").contains(query.type().id())
                                ? "studentId"
                                : "1";
        return jdbc.query(
                "select * from (" + query.sql() + ") report order by " + order
                        + " limit :reportSize offset :reportOffset",
                new MapSqlParameterSource(query.params().getValues()).addValue("reportSize", size)
                        .addValue("reportOffset", offset),
                (rs, num) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (var f : query.fields()) {
                        Object value = rs.getObject(f.id());
                        if (value instanceof java.sql.Timestamp t)
                            value = t.toInstant().atZone(clock.getZone()).toOffsetDateTime();
                        row.put(f.id(), value);
                    }
                    return row;
                });
    }

    private Map<String, Object> summary(Query query) {
        if (query.type().id().equals("COMPLETIONS")) {
            Map<String, Object> counts = jdbc.queryForMap(
                    "select count(*) as total,sum(case when completionStatus='COMPLETED' then 1 else 0 end) as completed,sum(case when completionStatus='COMPLETION_PENDING' then 1 else 0 end) as pending from ("
                            + query.sql() + ") report",
                    query.params());
            long total = ((Number) counts.get("total")).longValue(),
                    completed = counts.get("completed") == null ? 0 : ((Number) counts.get("completed")).longValue(),
                    pending = counts.get("pending") == null ? 0 : ((Number) counts.get("pending")).longValue();
            return Map.of("totalStudents", query.totalStudents(), "totalInternships", total, "completedInternships",
                    completed, "activeInternships", total - completed - pending, "completionPending", pending,
                    "completionPercentage", total == 0 ? 0 : Math.round(10000.0 * completed / total) / 100.0);
        }
        if (query.type().id().equals("ATTENDANCE"))
            return jdbc.queryForMap(
                    "select count(*) as records,sum(case when result='PRESENT' then 1 else 0 end) as present,sum(case when result='ABSENT' then 1 else 0 end) as absent,sum(case when verification='VERIFIED' then 1 else 0 end) as verified,sum(case when verification='FAILED' then 1 else 0 end) as unverified,sum(case when verification='NOT_PERFORMED' then 1 else 0 end) as \"notPerformed\",sum(case when verification='HISTORICAL' then 1 else 0 end) as historical,cast(100.0*sum(case when result='PRESENT' then 1 else 0 end)/nullif(count(*),0) as decimal(12,4)) as \"presentRecordPercentage\" from ("
                            + query.sql() + ") report",
                    query.params());
        return Map.of();
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0.0 : Math.round(part * 10000.0 / total) / 100.0;
    }

    // =========================================================================
    // 10 QUICK REPORTS ACCORDING TO III CELL DOCUMENT
    // =========================================================================

    // Report 1: Overall Internship Summary Report
    private Table buildOverallSummary(Query q) {
        String base = "(" + q.baseSql() + ") b";
        String sql = "select coalesce(source, 'UNRECORDED') as source, count(distinct sid) as total from " + base
                + " group by coalesce(source, 'UNRECORDED')";
        Map<String, Long> map = new HashMap<>();
        jdbc.query(sql, q.params(), rs -> {
            map.put(rs.getString("source"), rs.getLong("total"));
        });
        long dept = map.getOrDefault("DEPARTMENT", 0L);
        long cdc = map.getOrDefault("CDC", 0L);
        long self = map.getOrDefault("SELF", 0L);
        long unrec = map.getOrDefault("UNRECORDED", 0L);
        long total = dept + cdc + self + unrec;

        List<DashboardReportCatalog.Field> fields = List.of(
                field("category", "Internship Category", TEXT),
                field("total", "No. of Students", NUMBER),
                field("percentage", "% of Total", NUMBER));
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("category", "Department Internship", "total", dept, "percentage", pct(dept, total)));
        rows.add(Map.of("category", "CDC Internship", "cdc", cdc, "total", cdc, "percentage", pct(cdc, total)));
        rows.add(Map.of("category", "Self Internship", "total", self, "percentage", pct(self, total)));
        if (unrec > 0) {
            rows.add(Map.of("category", "Other / Unrecorded", "total", unrec, "percentage", pct(unrec, total)));
        }
        rows.add(Map.of("category", "Total", "total", total, "percentage", 100.0, "isTotal", true));

        Map<String, Object> summary = Map.of(
                "totalStudents", total,
                "department", dept,
                "cdc", cdc,
                "self", self);
        return new Table(fields, rows, rows.size(), 0, summary, "Overall Internship Summary Report", List.of());
    }

    // Report 2: Stipend Analysis Report (Table A: Category-wise, Table B:
    // Range-wise)
    private Table buildStipendAnalysis(Query q) {
        String base = "(" + q.baseSql() + ") b";
        String sqlA = "select coalesce(source, 'UNRECORDED') as source, " +
                "count(distinct case when stipend > 0 then sid end) as paid, " +
                "count(distinct case when stipend = 0 or stipend is null then sid end) as unpaid, " +
                "count(distinct sid) as total from " + base + " group by coalesce(source, 'UNRECORDED')";

        Map<String, Map<String, Long>> aMap = new HashMap<>();
        jdbc.query(sqlA, q.params(), rs -> {
            aMap.put(rs.getString("source"), Map.of(
                    "paid", rs.getLong("paid"),
                    "unpaid", rs.getLong("unpaid"),
                    "total", rs.getLong("total")));
        });

        long dPaid = aMap.getOrDefault("DEPARTMENT", Map.of()).getOrDefault("paid", 0L);
        long dUnpaid = aMap.getOrDefault("DEPARTMENT", Map.of()).getOrDefault("unpaid", 0L);
        long dTotal = aMap.getOrDefault("DEPARTMENT", Map.of()).getOrDefault("total", 0L);

        long cPaid = aMap.getOrDefault("CDC", Map.of()).getOrDefault("paid", 0L);
        long cUnpaid = aMap.getOrDefault("CDC", Map.of()).getOrDefault("unpaid", 0L);
        long cTotal = aMap.getOrDefault("CDC", Map.of()).getOrDefault("total", 0L);

        long sPaid = aMap.getOrDefault("SELF", Map.of()).getOrDefault("paid", 0L);
        long sUnpaid = aMap.getOrDefault("SELF", Map.of()).getOrDefault("unpaid", 0L);
        long sTotal = aMap.getOrDefault("SELF", Map.of()).getOrDefault("total", 0L);

        long uPaid = aMap.getOrDefault("UNRECORDED", Map.of()).getOrDefault("paid", 0L);
        long uUnpaid = aMap.getOrDefault("UNRECORDED", Map.of()).getOrDefault("unpaid", 0L);
        long uTotal = aMap.getOrDefault("UNRECORDED", Map.of()).getOrDefault("total", 0L);

        long totPaid = dPaid + cPaid + sPaid + uPaid;
        long totUnpaid = dUnpaid + cUnpaid + sUnpaid + uUnpaid;
        long totStudents = totPaid + totUnpaid;

        List<DashboardReportCatalog.Field> fieldsA = List.of(
                field("category", "Category", TEXT),
                field("paid", "Stipend Students", NUMBER),
                field("unpaid", "Non-Stipend Students", NUMBER),
                field("total", "Total", NUMBER));
        List<Map<String, Object>> rowsA = new ArrayList<>();
        rowsA.add(Map.of("category", "Department", "paid", dPaid, "unpaid", dUnpaid, "total", dTotal));
        rowsA.add(Map.of("category", "CDC", "paid", cPaid, "unpaid", cUnpaid, "total", cTotal));
        rowsA.add(Map.of("category", "Self", "paid", sPaid, "unpaid", sUnpaid, "total", sTotal));
        if (uTotal > 0) {
            rowsA.add(Map.of("category", "Other / Unrecorded", "paid", uPaid, "unpaid", uUnpaid, "total", uTotal));
        }
        rowsA.add(Map.of("category", "Total", "paid", totPaid, "unpaid", totUnpaid, "total", totStudents, "isTotal",
                true));

        // Table B: Stipend Range Distribution
        String sqlB = "select " +
                "count(distinct case when stipend = 0 or stipend is null then sid end) as unpaid, " +
                "count(distinct case when stipend > 0 and stipend <= 5000 then sid end) as r1, " +
                "count(distinct case when stipend > 5000 and stipend <= 10000 then sid end) as r2, " +
                "count(distinct case when stipend > 10000 and stipend <= 20000 then sid end) as r3, " +
                "count(distinct case when stipend > 20000 then sid end) as r4, " +
                "count(distinct sid) as total from " + base;

        Map<String, Object> rB = jdbc.queryForMap(sqlB, q.params());
        long unpaid = ((Number) rB.get("unpaid")).longValue();
        long r1 = ((Number) rB.get("r1")).longValue();
        long r2 = ((Number) rB.get("r2")).longValue();
        long r3 = ((Number) rB.get("r3")).longValue();
        long r4 = ((Number) rB.get("r4")).longValue();
        long bTotal = ((Number) rB.get("total")).longValue();

        List<DashboardReportCatalog.Field> fieldsB = List.of(
                field("range", "Stipend Range", TEXT),
                field("total", "No. of Students", NUMBER));
        List<Map<String, Object>> rowsB = new ArrayList<>();
        rowsB.add(Map.of("range", "Unpaid", "total", unpaid));
        rowsB.add(Map.of("range", "₹1–₹5,000", "total", r1));
        rowsB.add(Map.of("range", "₹5,001–₹10,000", "total", r2));
        rowsB.add(Map.of("range", "₹10,001–₹20,000", "total", r3));
        rowsB.add(Map.of("range", "Above ₹20,000", "total", r4));
        rowsB.add(Map.of("range", "Total", "total", bTotal, "isTotal", true));

        List<SubTable> subTables = List.of(
                new SubTable("table-a", "Table A: Category-wise Stipend Distribution", fieldsA, rowsA, Map.of()),
                new SubTable("table-b", "Table B: Stipend Range Distribution", fieldsB, rowsB, Map.of()));
        Map<String, Object> summary = Map.of(
                "totalStudents", totStudents,
                "stipendStudents", totPaid,
                "nonStipendStudents", totUnpaid);
        return new Table(fieldsA, rowsA, rowsA.size(), 0, summary, "Stipend Analysis Report", subTables);
    }

    // Report 3: Company-wise Student Distribution
    private Table buildCompanyDistribution(Query q) {
        String base = "(" + q.baseSql() + ") b";
        String sql = "select company, " +
                "count(distinct case when source='DEPARTMENT' then sid end) as department, " +
                "count(distinct case when source='CDC' then sid end) as cdc, " +
                "count(distinct case when source='SELF' then sid end) as self, " +
                "count(distinct sid) as total " +
                "from " + base + " where company is not null and trim(company) <> '' " +
                "group by company order by total desc, company asc";

        List<DashboardReportCatalog.Field> fields = List.of(
                field("company", "Company", TEXT),
                field("department", "Department", NUMBER),
                field("cdc", "CDC", NUMBER),
                field("self", "Self", NUMBER),
                field("total", "Total", NUMBER));
        List<Map<String, Object>> rows = new ArrayList<>();
        long deptSum = 0, cdcSum = 0, selfSum = 0, totalSum = 0;

        List<Map<String, Object>> results = jdbc.query(sql, q.params(), (rs, num) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("company", rs.getString("company"));
            r.put("department", rs.getLong("department"));
            r.put("cdc", rs.getLong("cdc"));
            r.put("self", rs.getLong("self"));
            r.put("total", rs.getLong("total"));
            return r;
        });

        for (var r : results) {
            deptSum += ((Number) r.get("department")).longValue();
            cdcSum += ((Number) r.get("cdc")).longValue();
            selfSum += ((Number) r.get("self")).longValue();
            totalSum += ((Number) r.get("total")).longValue();
            rows.add(r);
        }
        rows.add(Map.of(
                "company", "Total",
                "department", deptSum,
                "cdc", cdcSum,
                "self", selfSum,
                "total", totalSum,
                "isTotal", true));

        Map<String, Object> summary = Map.of(
                "totalCompanies", results.size(),
                "totalStudents", totalSum,
                "department", deptSum,
                "cdc", cdcSum,
                "self", selfSum);
        return new Table(fields, rows, rows.size(), 0, summary, "Company-wise Student Distribution", List.of());
    }

    // Report 4: Company + Stipend Analysis
    private Table buildCompanyStipend(Query q) {
        String base = "(" + q.baseSql() + ") b";
        String sql = "select company, " +
                "count(distinct sid) as total, " +
                "count(distinct case when stipend > 0 then sid end) as paid, " +
                "count(distinct case when stipend = 0 or stipend is null then sid end) as unpaid, " +
                "count(distinct case when offer = true then sid end) as offers " +
                "from " + base + " where company is not null and trim(company) <> '' " +
                "group by company order by total desc, company asc";

        List<DashboardReportCatalog.Field> fields = List.of(
                field("company", "Company", TEXT),
                field("total", "Total Students", NUMBER),
                field("paid", "Stipend", NUMBER),
                field("unpaid", "Non-Stipend", NUMBER),
                field("offers", "Placement Offer", NUMBER));
        List<Map<String, Object>> rows = new ArrayList<>();
        long totSum = 0, paidSum = 0, unpaidSum = 0, offersSum = 0;

        List<Map<String, Object>> results = jdbc.query(sql, q.params(), (rs, num) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("company", rs.getString("company"));
            r.put("total", rs.getLong("total"));
            r.put("paid", rs.getLong("paid"));
            r.put("unpaid", rs.getLong("unpaid"));
            r.put("offers", rs.getLong("offers"));
            return r;
        });

        for (var r : results) {
            totSum += ((Number) r.get("total")).longValue();
            paidSum += ((Number) r.get("paid")).longValue();
            unpaidSum += ((Number) r.get("unpaid")).longValue();
            offersSum += ((Number) r.get("offers")).longValue();
            rows.add(r);
        }
        rows.add(Map.of(
                "company", "Total",
                "total", totSum,
                "paid", paidSum,
                "unpaid", unpaidSum,
                "offers", offersSum,
                "isTotal", true));

        Map<String, Object> summary = Map.of(
                "totalCompanies", results.size(),
                "totalStudents", totSum,
                "stipendStudents", paidSum,
                "nonStipendStudents", unpaidSum,
                "placementOffers", offersSum);
        return new Table(fields, rows, rows.size(), 0, summary, "Company + Stipend Analysis", List.of());
    }

    // Report 5: Department / CDC / Self Comparison (6 parameters)
    private Table buildCategoryComparison(Query q) {
        String base = "(" + q.baseSql() + ") b";
        String sql = "select " +
                "coalesce(source, 'UNRECORDED') as source, " +
                "count(distinct sid) as total_students, " +
                "count(distinct case when stipend > 0 then sid end) as stipend_students, " +
                "count(distinct case when stipend = 0 or stipend is null then sid end) as non_stipend, " +
                "count(distinct case when offer = true then sid end) as placement_offers, " +
                "count(distinct case when completionStatus = 'COMPLETED' then sid end) as completed, " +
                "count(distinct case when (startDate <= :today and (endDate is null or endDate >= :today) and (completionStatus is null or completionStatus != 'COMPLETED') and workflow = 'INTERNSHIP_DETAILS_COMPLETE') then sid end) as ongoing " +
                "from " + base + " group by coalesce(source, 'UNRECORDED')";

        Map<String, Map<String, Long>> map = new HashMap<>();
        jdbc.query(sql, q.params(), rs -> {
            Map<String, Long> m = new HashMap<>();
            m.put("total_students", rs.getLong("total_students"));
            m.put("stipend_students", rs.getLong("stipend_students"));
            m.put("non_stipend", rs.getLong("non_stipend"));
            m.put("placement_offers", rs.getLong("placement_offers"));
            m.put("completed", rs.getLong("completed"));
            m.put("ongoing", rs.getLong("ongoing"));
            map.put(rs.getString("source"), m);
        });

        List<DashboardReportCatalog.Field> fields = List.of(
                field("parameter", "Parameter", TEXT),
                field("department", "Department", NUMBER),
                field("cdc", "CDC", NUMBER),
                field("self", "Self", NUMBER),
                field("total", "Total", NUMBER));

        String[][] params = new String[][] {
                { "Total Students", "total_students" },
                { "Stipend Students", "stipend_students" },
                { "Non-Stipend", "non_stipend" },
                { "Placement Offers", "placement_offers" },
                { "Internship Completed", "completed" },
                { "Internship Ongoing", "ongoing" }
        };

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String[] p : params) {
            String label = p[0];
            String key = p[1];
            long dept = map.getOrDefault("DEPARTMENT", Map.of()).getOrDefault(key, 0L);
            long cdc = map.getOrDefault("CDC", Map.of()).getOrDefault(key, 0L);
            long self = map.getOrDefault("SELF", Map.of()).getOrDefault(key, 0L);
            long other = map.getOrDefault("UNRECORDED", Map.of()).getOrDefault(key, 0L);
            long tot = dept + cdc + self + other;
            rows.add(Map.of(
                    "parameter", label,
                    "department", dept,
                    "cdc", cdc,
                    "self", self,
                    "total", tot));
        }

        Map<String, Object> summary = Map.of(
                "totalParameters", rows.size());
        return new Table(fields, rows, rows.size(), 0, summary, "Department / CDC / Self Comparison", List.of());
    }

    // Report 6: Placement Offer Analysis (Table A: Category-wise, Table B:
    // Company-wise)
    private Table buildPlacementAnalysis(Query q) {
        String base = "(" + q.baseSql() + ") b";
        String sqlA = "select coalesce(source, 'UNRECORDED') as source, " +
                "count(distinct sid) as total, " +
                "count(distinct case when offer = true then sid end) as offers, " +
                "count(distinct case when offer = false or offer is null then sid end) as no_offers " +
                "from " + base + " group by coalesce(source, 'UNRECORDED')";

        Map<String, Map<String, Long>> aMap = new HashMap<>();
        jdbc.query(sqlA, q.params(), rs -> {
            aMap.put(rs.getString("source"), Map.of(
                    "total", rs.getLong("total"),
                    "offers", rs.getLong("offers"),
                    "no_offers", rs.getLong("no_offers")));
        });

        long dTot = aMap.getOrDefault("DEPARTMENT", Map.of()).getOrDefault("total", 0L);
        long dOff = aMap.getOrDefault("DEPARTMENT", Map.of()).getOrDefault("offers", 0L);
        long dNo = aMap.getOrDefault("DEPARTMENT", Map.of()).getOrDefault("no_offers", 0L);

        long cTot = aMap.getOrDefault("CDC", Map.of()).getOrDefault("total", 0L);
        long cOff = aMap.getOrDefault("CDC", Map.of()).getOrDefault("offers", 0L);
        long cNo = aMap.getOrDefault("CDC", Map.of()).getOrDefault("no_offers", 0L);

        long sTot = aMap.getOrDefault("SELF", Map.of()).getOrDefault("total", 0L);
        long sOff = aMap.getOrDefault("SELF", Map.of()).getOrDefault("offers", 0L);
        long sNo = aMap.getOrDefault("SELF", Map.of()).getOrDefault("no_offers", 0L);

        long uTot = aMap.getOrDefault("UNRECORDED", Map.of()).getOrDefault("total", 0L);
        long uOff = aMap.getOrDefault("UNRECORDED", Map.of()).getOrDefault("offers", 0L);
        long uNo = aMap.getOrDefault("UNRECORDED", Map.of()).getOrDefault("no_offers", 0L);

        long grandTot = dTot + cTot + sTot + uTot;
        long grandOff = dOff + cOff + sOff + uOff;
        long grandNo = dNo + cNo + sNo + uNo;

        List<DashboardReportCatalog.Field> fieldsA = List.of(
                field("category", "Category", TEXT),
                field("total", "Total Students", NUMBER),
                field("offers", "Placement Offer", NUMBER),
                field("noOffers", "No Placement Offer", NUMBER));
        List<Map<String, Object>> rowsA = new ArrayList<>();
        rowsA.add(Map.of("category", "Department", "total", dTot, "offers", dOff, "noOffers", dNo));
        rowsA.add(Map.of("category", "CDC", "total", cTot, "offers", cOff, "noOffers", cNo));
        rowsA.add(Map.of("category", "Self", "total", sTot, "offers", sOff, "noOffers", sNo));
        if (uTot > 0) {
            rowsA.add(Map.of("category", "Other / Unrecorded", "total", uTot, "offers", uOff, "noOffers", uNo));
        }
        rowsA.add(Map.of("category", "Total", "total", grandTot, "offers", grandOff, "noOffers", grandNo, "isTotal",
                true));

        // Table B: Company-wise Placement Offers
        String sqlB = "select company, " +
                "count(distinct sid) as total, " +
                "count(distinct case when offer = true then sid end) as offers " +
                "from " + base + " where company is not null and trim(company) <> '' " +
                "group by company order by offers desc, total desc, company asc";

        List<DashboardReportCatalog.Field> fieldsB = List.of(
                field("company", "Company", TEXT),
                field("total", "Number of Interns", NUMBER),
                field("offers", "Placement/PPO Offers", NUMBER));
        List<Map<String, Object>> rowsB = new ArrayList<>();
        long bTotSum = 0, bOffSum = 0;

        List<Map<String, Object>> bResults = jdbc.query(sqlB, q.params(), (rs, num) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("company", rs.getString("company"));
            r.put("total", rs.getLong("total"));
            r.put("offers", rs.getLong("offers"));
            return r;
        });

        for (var r : bResults) {
            bTotSum += ((Number) r.get("total")).longValue();
            bOffSum += ((Number) r.get("offers")).longValue();
            rowsB.add(r);
        }
        rowsB.add(Map.of(
                "company", "Total",
                "total", bTotSum,
                "offers", bOffSum,
                "isTotal", true));

        List<SubTable> subTables = List.of(
                new SubTable("table-a", "Table A: Category-wise Placement Offers", fieldsA, rowsA, Map.of()),
                new SubTable("table-b", "Table B: Company-wise Placement Offers", fieldsB, rowsB, Map.of()));
        Map<String, Object> summary = Map.of(
                "totalStudents", grandTot,
                "placementOffers", grandOff,
                "noPlacementOffer", grandNo);
        return new Table(fieldsA, rowsA, rowsA.size(), 0, summary, "Placement Offer Analysis", subTables);
    }

    // Report 7: Internship Status Analysis
    private Table buildStatusAnalysis(Query q) {
        String base = "(" + q.baseSql() + ") b";
        String sql = "select coalesce(source, 'UNRECORDED') as source, " +
                "count(distinct case when completionStatus = 'COMPLETED' then sid end) as completed, " +
                "count(distinct case when completionStatus = 'COMPLETION_PENDING' or (endDate < :today and (completionStatus is null or completionStatus != 'COMPLETED')) then sid end) as pending, "
                +
                "count(distinct case when startDate <= :today and (endDate is null or endDate >= :today) and workflow = 'INTERNSHIP_DETAILS_COMPLETE' and (completionStatus is null or completionStatus not in ('COMPLETED', 'COMPLETION_PENDING')) then sid end) as ongoing, "
                +
                "count(distinct case when startDate > :today or startDate is null or workflow in ('NOT_SECURED', 'INTERNSHIP_REQUEST_RAISED', 'DOCUMENTS_SUBMITTED') then sid end) as not_started, "
                +
                "count(distinct sid) as total " +
                "from " + base + " group by coalesce(source, 'UNRECORDED')";

        Map<String, Map<String, Long>> map = new HashMap<>();
        jdbc.query(sql, q.params(), rs -> {
            map.put(rs.getString("source"), Map.of(
                    "completed", rs.getLong("completed"),
                    "pending", rs.getLong("pending"),
                    "ongoing", rs.getLong("ongoing"),
                    "not_started", rs.getLong("not_started"),
                    "total", rs.getLong("total")));
        });

        List<DashboardReportCatalog.Field> fields = List.of(
                field("category", "Category", TEXT),
                field("notStarted", "Not Started", NUMBER),
                field("ongoing", "Ongoing", NUMBER),
                field("completed", "Completed", NUMBER),
                field("pending", "Pending", NUMBER),
                field("total", "Total", NUMBER));

        String[] cats = new String[] { "DEPARTMENT", "CDC", "SELF" };
        String[] labels = new String[] { "Department", "CDC", "Self" };

        List<Map<String, Object>> rows = new ArrayList<>();
        long nsSum = 0, onSum = 0, compSum = 0, pendSum = 0, totSum = 0;

        for (int i = 0; i < cats.length; i++) {
            var m = map.getOrDefault(cats[i], Map.of());
            long ns = m.getOrDefault("not_started", 0L);
            long on = m.getOrDefault("ongoing", 0L);
            long comp = m.getOrDefault("completed", 0L);
            long pend = m.getOrDefault("pending", 0L);
            long tot = m.getOrDefault("total", 0L);

            nsSum += ns;
            onSum += on;
            compSum += comp;
            pendSum += pend;
            totSum += tot;
            rows.add(Map.of("category", labels[i], "notStarted", ns, "ongoing", on, "completed", comp, "pending", pend,
                    "total", tot));
        }

        if (map.containsKey("UNRECORDED")) {
            var m = map.get("UNRECORDED");
            long ns = m.getOrDefault("not_started", 0L);
            long on = m.getOrDefault("ongoing", 0L);
            long comp = m.getOrDefault("completed", 0L);
            long pend = m.getOrDefault("pending", 0L);
            long tot = m.getOrDefault("total", 0L);
            if (tot > 0) {
                nsSum += ns;
                onSum += on;
                compSum += comp;
                pendSum += pend;
                totSum += tot;
                rows.add(Map.of("category", "Other / Unrecorded", "notStarted", ns, "ongoing", on, "completed", comp,
                        "pending", pend, "total", tot));
            }
        }

        rows.add(Map.of("category", "Total", "notStarted", nsSum, "ongoing", onSum, "completed", compSum, "pending",
                pendSum, "total", totSum, "isTotal", true));

        Map<String, Object> summary = Map.of(
                "totalStudents", totSum,
                "ongoing", onSum,
                "completed", compSum,
                "pending", pendSum,
                "notStarted", nsSum);
        return new Table(fields, rows, rows.size(), 0, summary, "Internship Status Analysis", List.of());
    }

    // Report 8: Student Working / Internship Participation Report
    private Table buildParticipation(Query q) {
        String base = "(" + q.baseSql() + ") b";
        String sql = "select coalesce(source, 'UNRECORDED') as source, " +
                "count(distinct case when internshipId is not null or workflow != 'NOT_SECURED' then sid end) as selected, "
                +
                "count(distinct case when startDate <= :today and workflow = 'INTERNSHIP_DETAILS_COMPLETE' then sid end) as joined, "
                +
                "count(distinct case when (startDate > :today or startDate is null or workflow != 'INTERNSHIP_DETAILS_COMPLETE') and (completionStatus is null or completionStatus != 'COMPLETED') then sid end) as not_joined, "
                +
                "count(distinct case when completionStatus = 'COMPLETED' then sid end) as completed, " +
                "count(distinct sid) as total " +
                "from " + base + " group by coalesce(source, 'UNRECORDED')";

        Map<String, Map<String, Long>> map = new HashMap<>();
        jdbc.query(sql, q.params(), rs -> {
            map.put(rs.getString("source"), Map.of(
                    "selected", rs.getLong("selected"),
                    "joined", rs.getLong("joined"),
                    "not_joined", rs.getLong("not_joined"),
                    "completed", rs.getLong("completed"),
                    "total", rs.getLong("total")));
        });

        List<DashboardReportCatalog.Field> fields = List.of(
                field("category", "Category", TEXT),
                field("selected", "Selected", NUMBER),
                field("joined", "Joined", NUMBER),
                field("notJoined", "Not Joined", NUMBER),
                field("completed", "Completed", NUMBER),
                field("total", "Total", NUMBER));

        String[] cats = new String[] { "DEPARTMENT", "CDC", "SELF" };
        String[] labels = new String[] { "Department", "CDC", "Self" };

        List<Map<String, Object>> rows = new ArrayList<>();
        long selSum = 0, jSum = 0, njSum = 0, compSum = 0, totSum = 0;

        for (int i = 0; i < cats.length; i++) {
            var m = map.getOrDefault(cats[i], Map.of());
            long sel = m.getOrDefault("selected", 0L);
            long j = m.getOrDefault("joined", 0L);
            long nj = m.getOrDefault("not_joined", 0L);
            long comp = m.getOrDefault("completed", 0L);
            long tot = m.getOrDefault("total", 0L);

            selSum += sel;
            jSum += j;
            njSum += nj;
            compSum += comp;
            totSum += tot;
            rows.add(Map.of("category", labels[i], "selected", sel, "joined", j, "notJoined", nj, "completed", comp,
                    "total", tot));
        }

        if (map.containsKey("UNRECORDED")) {
            var m = map.get("UNRECORDED");
            long sel = m.getOrDefault("selected", 0L);
            long j = m.getOrDefault("joined", 0L);
            long nj = m.getOrDefault("not_joined", 0L);
            long comp = m.getOrDefault("completed", 0L);
            long tot = m.getOrDefault("total", 0L);
            if (tot > 0) {
                selSum += sel;
                jSum += j;
                njSum += nj;
                compSum += comp;
                totSum += tot;
                rows.add(Map.of("category", "Other / Unrecorded", "selected", sel, "joined", j, "notJoined", nj,
                        "completed", comp, "total", tot));
            }
        }

        rows.add(Map.of("category", "Total", "selected", selSum, "joined", jSum, "notJoined", njSum, "completed",
                compSum, "total", totSum, "isTotal", true));

        Map<String, Object> summary = Map.of(
                "totalStudents", totSum,
                "selected", selSum,
                "joined", jSum,
                "notJoined", njSum,
                "completed", compSum);
        return new Table(fields, rows, rows.size(), 0, summary, "Student Working / Internship Participation Report",
                List.of());
    }

    // Report 9: Attendance / Active Internship Report (Summary Table + Active
    // Intern List)
    private Table buildActiveAttendance(Query q) {
        String base = "(" + q.baseSql() + ") b";
        // 1. Active interns query
        String activeInternsSql = "select b.sid, b.uid, b.studentName, b.company, coalesce(b.source, 'UNRECORDED') as source, "
                + "count(distinct ar.attendance_date) as total_attendance, "
                + "count(distinct case when ar.attendance_result = 'PRESENT' then ar.attendance_date end) as present_attendance "
                + "from " + base
                + " left join attendance_records ar on ar.student_user_id = b.sid and ar.internship_id = b.internshipId and ar.deleted_at is null "
                +
                "where b.startDate <= :today and (b.endDate is null or b.endDate >= :today) and (b.completionStatus is null or b.completionStatus != 'COMPLETED') and b.workflow = 'INTERNSHIP_DETAILS_COMPLETE' "
                +
                "group by b.sid, b.uid, b.studentName, b.company, b.source order by b.studentName asc";

        List<Map<String, Object>> activeStudents = jdbc.query(activeInternsSql, q.params(), (rs, num) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", rs.getLong("sid"));
            row.put("uid", rs.getString("uid"));
            row.put("studentName", rs.getString("studentName"));
            row.put("company", rs.getString("company"));
            row.put("category", rs.getString("source"));
            long tot = rs.getLong("total_attendance");
            long pres = rs.getLong("present_attendance");
            double pct = tot == 0 ? 0.0 : Math.round(pres * 1000.0 / tot) / 10.0;
            row.put("attendancePercentage", tot == 0 ? "—" : pct + "%");
            row.put("attendanceStatus", tot == 0 ? "Not Updated" : pct >= 75.0 ? "Regular" : "Defaulter");
            row.put("rawPct", pct);
            row.put("rawTotal", tot);
            return row;
        });

        long currentlyOnInternship = activeStudents.size();
        long regularlyAttending = activeStudents.stream().filter(
                s -> ((Number) s.get("rawTotal")).longValue() > 0 && ((Number) s.get("rawPct")).doubleValue() >= 75.0)
                .count();
        long defaulters = activeStudents.stream().filter(
                s -> ((Number) s.get("rawTotal")).longValue() > 0 && ((Number) s.get("rawPct")).doubleValue() < 75.0)
                .count();
        long notUpdated = activeStudents.stream().filter(s -> ((Number) s.get("rawTotal")).longValue() == 0).count();

        // Count completed
        Long completedCount = jdbc.queryForObject(
                "select count(distinct sid) from " + base + " where completionStatus = 'COMPLETED'", q.params(),
                Long.class);
        long completed = completedCount == null ? 0 : completedCount;

        List<DashboardReportCatalog.Field> fieldsA = List.of(
                field("metric", "Attendance Metric", TEXT),
                field("count", "Number of Students", NUMBER));
        List<Map<String, Object>> rowsA = new ArrayList<>();
        rowsA.add(Map.of("metric", "Students currently on internship", "count", currentlyOnInternship));
        rowsA.add(Map.of("metric", "Students regularly attending", "count", regularlyAttending));
        rowsA.add(Map.of("metric", "Attendance defaulters", "count", defaulters));
        rowsA.add(Map.of("metric", "Students whose attendance is not updated", "count", notUpdated));
        rowsA.add(Map.of("metric", "Students who completed internships", "count", completed));

        List<DashboardReportCatalog.Field> fieldsB = List.of(
                field("uid", "UID", TEXT),
                field("studentName", "Student Name", TEXT),
                field("company", "Company", TEXT),
                field("category", "Internship Category", TEXT),
                field("attendancePercentage", "Attendance %", TEXT),
                field("attendanceStatus", "Attendance Status", TEXT));
        List<Map<String, Object>> rowsB = new ArrayList<>(activeStudents);

        List<SubTable> subTables = List.of(
                new SubTable("summary", "Table A: Active Internship Attendance Metrics", fieldsA, rowsA, Map.of()),
                new SubTable("student-list", "Table B: Active Intern Student List", fieldsB, rowsB, Map.of()));

        Map<String, Object> summary = Map.of(
                "activeInterns", currentlyOnInternship,
                "regularlyAttending", regularlyAttending,
                "defaulters", defaulters,
                "notUpdated", notUpdated,
                "completed", completed);
        return new Table(fieldsA, rowsA, rowsA.size(), 0, summary, "Attendance / Active Internship Report", subTables);
    }

    // Report 10: Master Analytical Dashboard Report (SubTables A through H)
    private Table buildMasterAnalytical(Query q) {
        Table overall = buildOverallSummary(q);
        Table stipend = buildStipendAnalysis(q);
        Table companyDist = buildCompanyDistribution(q);
        Table companyStip = buildCompanyStipend(q);
        Table placement = buildPlacementAnalysis(q);
        Table status = buildStatusAnalysis(q);
        Table attendance = buildActiveAttendance(q);
        Table comparison = buildCategoryComparison(q);

        List<SubTable> allSubTables = new ArrayList<>();
        allSubTables.add(new SubTable("sub-a", "A. Internship Category Distribution", overall.fields(), overall.rows(),
                overall.summary()));
        if (stipend.subTables() != null && !stipend.subTables().isEmpty()) {
            allSubTables.add(stipend.subTables().get(0));
            allSubTables.add(stipend.subTables().get(1));
        } else {
            allSubTables.add(
                    new SubTable("sub-b", "B. Stipend Analysis", stipend.fields(), stipend.rows(), stipend.summary()));
        }
        allSubTables.add(new SubTable("sub-c", "C. Company-wise Student Distribution", companyDist.fields(),
                companyDist.rows(), companyDist.summary()));
        allSubTables.add(new SubTable("sub-d", "D. Company-wise Stipend", companyStip.fields(), companyStip.rows(),
                companyStip.summary()));
        if (placement.subTables() != null && !placement.subTables().isEmpty()) {
            allSubTables.add(placement.subTables().get(0));
            allSubTables.add(placement.subTables().get(1));
        } else {
            allSubTables.add(new SubTable("sub-e", "E. Placement Analysis", placement.fields(), placement.rows(),
                    placement.summary()));
        }
        allSubTables
                .add(new SubTable("sub-f", "F. Internship Status", status.fields(), status.rows(), status.summary()));
        if (attendance.subTables() != null && !attendance.subTables().isEmpty()) {
            allSubTables.add(attendance.subTables().get(0));
        }
        allSubTables.add(new SubTable("sub-h", "H. Department / CDC / Self Comparison", comparison.fields(),
                comparison.rows(), comparison.summary()));

        // Summary metrics
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalStudents", overall.summary().getOrDefault("totalStudents", 0));
        summary.put("department", overall.summary().getOrDefault("department", 0));
        summary.put("cdc", overall.summary().getOrDefault("cdc", 0));
        summary.put("self", overall.summary().getOrDefault("self", 0));
        summary.put("stipendStudents", stipend.summary().getOrDefault("stipendStudents", 0));
        summary.put("nonStipendStudents", stipend.summary().getOrDefault("nonStipendStudents", 0));
        summary.put("placementOffers", placement.summary().getOrDefault("placementOffers", 0));
        summary.put("ongoing", status.summary().getOrDefault("ongoing", 0));
        summary.put("completed", status.summary().getOrDefault("completed", 0));

        return new Table(comparison.fields(), comparison.rows(), comparison.rows().size(), 0, summary,
                "Master Analytical Dashboard Report", allSubTables);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Table preview(UserPrincipal user, ReportTableRequest request) {
        Query q = query(user, request);
        return switch (q.type().id()) {
            case "OVERALL", "OVERALL_SUMMARY" -> buildOverallSummary(q);
            case "STIPEND", "STIPEND_ANALYSIS" -> buildStipendAnalysis(q);
            case "COMPANY", "COMPANY_DISTRIBUTION" -> buildCompanyDistribution(q);
            case "COMPANY_STIPEND" -> buildCompanyStipend(q);
            case "COMPARISON", "CATEGORY_COMPARISON" -> buildCategoryComparison(q);
            case "PLACEMENT", "PLACEMENT_ANALYSIS" -> buildPlacementAnalysis(q);
            case "STATUS_ANALYSIS" -> buildStatusAnalysis(q);
            case "PARTICIPATION" -> buildParticipation(q);
            case "ACTIVE_ATTENDANCE" -> buildActiveAttendance(q);
            case "MASTER_ANALYTICAL" -> buildMasterAnalytical(q);
            default -> {
                Long total = jdbc.queryForObject("select count(*) from (" + q.sql() + ") report", q.params(),
                        Long.class);
                yield new Table(q.fields(), rows(q, request.page() * 50, 50), total == null ? 0 : total, request.page(),
                        summary(q), q.type().title());
            }
        };
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardReportService.Download export(UserPrincipal user, ReportTableRequest request) throws IOException {
        Query q = query(user, request);
        if (request.format() == null)
            throw new IllegalArgumentException("Choose XLSX, CSV or PDF");
        String extension = request.format().extension();
        Path file = Files.createTempFile("ims-institutional-report-", "." + extension);

        try {
            Table previewTable = preview(user, request);
            int count = 0;
            try (DashboardReportWriter writer = new DashboardReportWriter(file, request.format(), previewTable.fields(),
                    clock.getZone(), q.type().title(), q.type().historicalSheet())) {
                if (previewTable.subTables() != null && !previewTable.subTables().isEmpty()) {
                    for (var st : previewTable.subTables()) {
                        writer.subTable(st.title(), st.fields(), st.rows());
                    }
                    count = writer.count();
                } else if (!previewTable.rows().isEmpty()
                        && !Set.of("COMPLETIONS", "ROSTER", "MONTHLY_PROGRESS", "ATTENDANCE", "ATTENDANCE_SUMMARY")
                                .contains(q.type().id())) {
                    for (var row : previewTable.rows()) {
                        writer.row(row);
                    }
                    count = writer.count();
                } else {
                    int offset = 0;
                    while (true) {
                        var batch = rows(q, offset, 250);
                        if (batch.isEmpty())
                            break;
                        for (var row : batch)
                            writer.row(row);
                        offset += batch.size();
                    }
                    count = writer.count();
                }

                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("Generated at", ZonedDateTime.now(clock).toString());
                metadata.put("Generated by", user.email());
                metadata.put("Access scope", options(user).scope());
                metadata.put("Record count", Integer.toString(count));
                metadata.put("Applied filters", Objects.toString(request.filters(), "None"));
                metadata.put("Selected students",
                        request.studentIds() == null || request.studentIds().isEmpty() ? "All within authorized filters"
                                : request.studentIds().toString());
                metadata.put("Report definition", q.type().description());
                metadata.put("Historical reference", q.type().historicalSheet());
                if (previewTable.summary() != null) {
                    previewTable.summary().forEach((k, v) -> metadata.put(k, String.valueOf(v)));
                }
                writer.finish(metadata);
            }
            return new DashboardReportService.Download(file,
                    q.type().id().toLowerCase(Locale.ROOT) + "-" + LocalDate.now(clock) + "." + extension,
                    request.format().contentType(), count);
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(file);
            throw ex;
        }
    }
}
