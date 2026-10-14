package edu.institution.ims.dashboard.report;

import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.security.AadhaarEncryptionService;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** One-to-one joins for student pages; collections are fetched in batches, never joined together. */
// JdbcTemplate already translates SQL failures. Keep input validation exceptions as 400 responses.
@Component
public class DashboardReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardReportCatalog catalog;
    private final Clock clock;
    private final AadhaarEncryptionService encryption;
    public DashboardReportRepository(NamedParameterJdbcTemplate jdbc, DashboardReportCatalog catalog, Clock clock, AadhaarEncryptionService encryption) {
        this.jdbc = jdbc; this.catalog = catalog; this.clock = clock; this.encryption = encryption;
    }
    private static final String FROM = """
            from users u
            left join student_accounts sa on sa.user_id = u.id
            left join student_profiles p on p.user_id = u.id
            left join mentor_student_assignments a on a.student_user_id = u.id and a.active = true
            left join users mu on mu.id = a.mentor_user_id
            left join mentor_profiles mp on mp.user_id = a.mentor_user_id
            left join internship_onboarding o on o.student_user_id = u.id
            left join internship_details i on i.onboarding_id = o.id
            left join student_professional_profiles pp on pp.student_user_id = u.id
            left join student_resumes r on r.professional_profile_id = pp.id and r.is_active = true
            left join resume_analyses ra on ra.resume_id = r.id
            left join attendance_records t on t.student_user_id = u.id and t.attendance_date = :attendanceDate and t.deleted_at is null
            left join attendance_evidence te on te.attendance_id = t.id
            left join hybrid_work_schedule hs on hs.internship_id = i.id and hs.day_of_week = :dayOfWeek
            """;
    public record Scope(String where, MapSqlParameterSource params, Map<String, String> filters, LocalDate attendanceDate) {}

    public Scope scope(UserPrincipal user, Map<String, String> requested) {
        boolean admin = catalog.isAdmin(user);
        Map<String, DashboardReportCatalog.Filter> allowed = catalog.filters(user).stream().collect(Collectors.toMap(DashboardReportCatalog.Filter::id, Function.identity()));
        Map<String, String> filters = new LinkedHashMap<>();
        if (requested != null) requested.forEach((key, raw) -> {
            var filter = allowed.get(key);
            if (filter == null || raw == null || raw.length() > 254) throw new IllegalArgumentException("Unsupported or unauthorized report filter: " + key);
            String value = raw.trim();
            if (!value.isEmpty()) {
                if (filter.type().equals("select") && filter.choices().stream().noneMatch(c -> c.value().equals(value)))
                    throw new IllegalArgumentException("Invalid value for " + filter.label());
                if (filter.type().equals("date")) date(value);
                if (filter.type().equals("number")) positiveId(value);
                if (key.equals("academicYear") && !value.matches("\\d{4}-\\d{4}")) throw new IllegalArgumentException("Academic year must use YYYY-YYYY");
                filters.put(key, value);
            }
        });
        if (filters.containsKey("historyFrom") && filters.containsKey("historyTo") && date(filters.get("historyFrom")).isAfter(date(filters.get("historyTo"))))
            throw new IllegalArgumentException("History from must be on or before history through");
        LocalDate attendanceDate = filters.containsKey("attendanceDate") ? date(filters.get("attendanceDate")) : LocalDate.now(clock);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("attendanceDate", java.sql.Date.valueOf(attendanceDate))
                .addValue("dayOfWeek", attendanceDate.getDayOfWeek().name()).addValue("viewerId", user.id());
        StringBuilder where = new StringBuilder(" where u.role = 'STUDENT'");
        if (!admin) where.append(" and a.mentor_user_id = :viewerId");
        // Reject guessed / out-of-scope IDs even if another filter would produce an empty result.
        if (filters.containsKey("studentId")) {
            params.addValue("studentId", positiveId(filters.get("studentId")));
            Long count = jdbc.queryForObject("select count(*) " + FROM + where + " and u.id = :studentId", params, Long.class);
            if (count == null || count == 0) throw new AccessDeniedException("Student is not available in your authorized report scope");
        }
        filters.forEach((key, value) -> {
            String expression = switch (key) {
                case "studentId" -> "u.id = :studentId";
                case "mentorId" -> "a.mentor_user_id = :mentorId";
                case "branch" -> "p.branch = :branch";
                case "academicYear" -> "p.academic_year = :academicYear";
                case "semester" -> "coalesce(p.semester, sa.semester) = :semester";
                case "profileStatus" -> value.equals("COMPLETE") ? "p.id is not null" : "p.id is null";
                case "workflowStatus" -> "coalesce(o.workflow_status, 'NOT_SECURED') = :workflowStatus";
                case "internshipMode" -> "i.internship_mode = :internshipMode";
                case "internshipSource" -> "o.source = :internshipSource";
                case "companyType" -> "i.company_type = :companyType";
                case "mentorAssignment" -> value.equals("ASSIGNED") ? "a.id is not null" : "a.id is null";
                case "paid" -> value.equals("PAID") ? "i.stipend_per_month > 0" : "i.stipend_per_month = 0";
                case "fullTimeOffered" -> "i.full_time_employment_offered = :fullTimeOffered";
                case "accountActive" -> "u.active = :accountActive";
                case "attendanceStatus" -> "coalesce(t.attendance_result, 'NOT_SUBMITTED') = :attendanceStatus";
                case "verificationStatus" -> "(" + AttendanceLivenessSql.status("t", "te") + ") = :verificationStatus";
                case "company" -> "lower(i.company_name) like :company escape '!'";
                case "search" -> "(lower(coalesce(p.student_name, sa.student_name, '')) like :search escape '!' or lower(coalesce(p.uid, sa.uid, '')) like :search escape '!' or lower(u.email) like :search escape '!')";
                default -> null; // Date parameters configure the reference date or related history, not student membership.
            };
            if (expression != null) {
                where.append(" and ").append(expression);
                Object parameter = switch (key) {
                    case "studentId", "mentorId" -> positiveId(value);
                    case "semester" -> Integer.valueOf(value);
                    case "fullTimeOffered", "accountActive" -> value.equals("YES");
                    case "search", "company" -> "%" + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                    default -> value;
                };
                params.addValue(key, parameter);
            }
        });
        return new Scope(where.toString(), params, Collections.unmodifiableMap(filters), attendanceDate);
    }
    private static LocalDate date(String value) {
        try { LocalDate result = LocalDate.parse(value); if (result.getYear() < 1900 || result.getYear() > 9999) throw new IllegalArgumentException(); return result; }
        catch (RuntimeException e) { throw new IllegalArgumentException("Report dates must use YYYY-MM-DD between 1900 and 9999"); }
    }
    private static long positiveId(String value) {
        try { long id = Long.parseLong(value); if (id <= 0) throw new IllegalArgumentException(); return id; }
        catch (RuntimeException e) { throw new IllegalArgumentException("Student and mentor account IDs must be positive integers"); }
    }
    /** Authorized, one-row-per-student projection shared by institutional report types. */
    String reportStudentsSql(Scope scope) {
        return "select u.id as sid,coalesce(p.uid,sa.uid) as uid,coalesce(p.student_name,sa.student_name) as studentName,p.branch,mp.name as mentorName,i.id as internshipId,i.company_name as company,coalesce(o.source,'UNRECORDED') as source,i.stipend_per_month as stipend,i.full_time_employment_offered as offer,coalesce(o.workflow_status,'NOT_SECURED') as workflow,o.diary_issued as diary,coalesce(p.updated_at,p.created_at) as profileLastStudentUpdatedAt,coalesce(i.updated_at,i.created_at) as internshipLastStudentUpdatedAt " + FROM + scope.where();
    }
    public long count(Scope scope) {
        return Objects.requireNonNull(jdbc.queryForObject("select count(*) " + FROM + scope.where(), scope.params(), Long.class));
    }
    public List<Map<String, Object>> page(Scope scope, List<DashboardReportCatalog.Field> fields, long afterId, int limit) {
        String columns = fields.stream().filter(f -> f.expression() != null).map(f -> f.expression() + " as " + f.id()).collect(Collectors.joining(", "));
        String sql = "select u.id as report_student_id" + (columns.isEmpty() ? "" : ", " + columns) + " " + FROM + scope.where()
                + " and u.id > :afterId order by u.id limit :pageSize";
        MapSqlParameterSource params = new MapSqlParameterSource(scope.params().getValues()).addValue("afterId", afterId).addValue("pageSize", limit);
        return jdbc.query(sql, params, (rs, rowNumber) -> {
            Map<String, Object> row = new LinkedHashMap<>(); row.put("_id", rs.getLong("report_student_id"));
            for (var field : fields) {
                Object value = field.expression() == null ? null : rs.getObject(field.id());
                if (field.id().equals("aadhaarNumber") && value != null) value = encryption.decrypt(value.toString());
                row.put(field.id(), value);
            }
            return row;
        });
    }

    public void related(Scope scope, List<DashboardReportCatalog.Field> fields, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        Set<String> selected = fields.stream().map(DashboardReportCatalog.Field::id).collect(Collectors.toSet());
        Map<Long, Map<String, Object>> byId = rows.stream().collect(Collectors.toMap(r -> ((Number) r.get("_id")).longValue(), Function.identity()));
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", new ArrayList<>(byId.keySet()));
        String history = "";
        if (scope.filters().containsKey("historyFrom")) { params.addValue("historyFrom", java.sql.Date.valueOf(date(scope.filters().get("historyFrom")))); history += " and %s >= :historyFrom"; }
        if (scope.filters().containsKey("historyTo")) { params.addValue("historyTo", java.sql.Date.valueOf(date(scope.filters().get("historyTo")))); history += " and %s <= :historyTo"; }
        String attendanceWindow = history.replace("%s", "ar.attendance_date");
        String monthlyWindow = history.replace("%s", "m.period_start");

        if (has(selected, "documents", "documentsCount")) {
            Map<Long, Set<String>> uploaded = new HashMap<>();
            batch("select o.student_user_id as sid, d.document_type, d.original_file_name, d.uploaded_at, d.content_type, d.file_size from internship_documents d join internship_onboarding o on o.id=d.onboarding_id where o.student_user_id in (:ids) order by d.document_type", params, rs -> {
                long id = rs.getLong("sid"); uploaded.computeIfAbsent(id, unused -> new HashSet<>()).add(rs.getString("document_type"));
                append(byId.get(id), selected, "documents", rs.getString("document_type") + ": Uploaded — " + describe(rs, "original_file_name", "uploaded_at", "content_type", "file_size"));
            });
            byId.forEach((id, row) -> {
                Set<String> types = uploaded.getOrDefault(id, Set.of()); row.put("documentsCount", types.size());
                for (String type : List.of("OFFER_LETTER", "UNDERTAKING", "DEPARTMENT_NOC", "STUDENT_NOC"))
                    if (!types.contains(type)) append(row, selected, "documents", type + ": Missing");
            });
        }
        if (selected.contains("hybridSchedule")) batch("select o.student_user_id as sid, s.day_of_week, s.work_mode from hybrid_work_schedule s join internship_details i on i.id=s.internship_id join internship_onboarding o on o.id=i.onboarding_id where o.student_user_id in (:ids) order by case s.day_of_week when 'MONDAY' then 1 when 'TUESDAY' then 2 when 'WEDNESDAY' then 3 when 'THURSDAY' then 4 when 'FRIDAY' then 5 when 'SATURDAY' then 6 else 7 end", params,
                rs -> append(byId.get(rs.getLong("sid")), selected, "hybridSchedule", describe(rs, "day_of_week", "work_mode")));
        if (has(selected, "skills", "skillProvenance")) batch("select pp.student_user_id as sid, s.display_name, s.source from student_skills s join student_professional_profiles pp on pp.id=s.professional_profile_id where pp.student_user_id in (:ids) and s.is_active=true order by s.display_name", params, rs -> {
            var row = byId.get(rs.getLong("sid")); append(row, selected, "skills", rs.getString("display_name")); append(row, selected, "skillProvenance", describe(rs, "display_name", "source"));
        });
        if (selected.contains("skillSuggestions")) batch("select pp.student_user_id as sid, s.display_name, s.review_status from student_skill_suggestions s join student_professional_profiles pp on pp.id=s.professional_profile_id join resume_analyses ra on ra.id=s.resume_analysis_id join student_resumes r on r.id=ra.resume_id where pp.student_user_id in (:ids) and r.is_active=true order by s.display_name", params,
                rs -> append(byId.get(rs.getLong("sid")), selected, "skillSuggestions", describe(rs, "display_name", "review_status")));
        if (selected.contains("resumeHistory")) batch("select pp.student_user_id as sid, r.original_filename, r.parsing_status, r.is_active, r.uploaded_at from student_resumes r join student_professional_profiles pp on pp.id=r.professional_profile_id where pp.student_user_id in (:ids) order by r.uploaded_at, r.id", params,
                rs -> append(byId.get(rs.getLong("sid")), selected, "resumeHistory", describe(rs, "original_filename", "parsing_status", "is_active", "uploaded_at")));
        String analysisJoin = " join resume_analyses ra on ra.id=x.analysis_id join student_resumes r on r.id=ra.resume_id and r.is_active=true join student_professional_profiles pp on pp.id=r.professional_profile_id where pp.student_user_id in (:ids) order by x.sort_order, x.id";
        if (selected.contains("extractedSkills")) batch("select pp.student_user_id as sid, x.name from resume_extracted_skills x" + analysisJoin, params,
                rs -> append(byId.get(rs.getLong("sid")), selected, "extractedSkills", rs.getString("name")));
        if (selected.contains("certifications")) batch("select pp.student_user_id as sid, x.name from resume_extracted_certifications x" + analysisJoin, params,
                rs -> append(byId.get(rs.getLong("sid")), selected, "certifications", rs.getString("name")));
        if (selected.contains("education")) batch("select pp.student_user_id as sid, x.degree, x.field, x.institution from resume_extracted_education x" + analysisJoin, params,
                rs -> append(byId.get(rs.getLong("sid")), selected, "education", describe(rs, "degree", "field", "institution")));
        if (selected.contains("experience")) batch("select pp.student_user_id as sid, x.title, x.company, x.start_date, x.end_date from resume_extracted_experiences x" + analysisJoin, params,
                rs -> append(byId.get(rs.getLong("sid")), selected, "experience", describe(rs, "title", "company", "start_date", "end_date")));
        if (has(selected, "attendanceCount", "presentCount", "absentCount", "verifiedCount", "unverifiedCount", "notPerformedCount", "historicalCount")) {
            for (var row : rows) for (String key : List.of("attendanceCount", "presentCount", "absentCount", "verifiedCount", "unverifiedCount", "notPerformedCount", "historicalCount")) if (selected.contains(key)) row.put(key, 0L);
            String liveness = AttendanceLivenessSql.status("ar", "e");
            batch("select ar.student_user_id as sid, count(*) as attendance_count,"
                    + "sum(case when ar.attendance_result='PRESENT' then 1 else 0 end) as present_count,"
                    + "sum(case when ar.attendance_result='ABSENT' then 1 else 0 end) as absent_count,"
                    + "sum(case when (" + liveness + ")='VERIFIED' then 1 else 0 end) as verified_count,"
                    + "sum(case when (" + liveness + ")='FAILED' then 1 else 0 end) as unverified_count,"
                    + "sum(case when (" + liveness + ")='NOT_PERFORMED' then 1 else 0 end) as not_performed_count,"
                    + "sum(case when (" + liveness + ")='HISTORICAL' then 1 else 0 end) as historical_count "
                    + "from attendance_records ar left join attendance_evidence e on e.attendance_id=ar.id "
                    + "where ar.deleted_at is null and ar.student_user_id in (:ids)"
                    + attendanceWindow + " group by ar.student_user_id", params, rs -> {
                var row = byId.get(rs.getLong("sid"));
                for (String[] pair : new String[][]{{"attendanceCount","attendance_count"},{"presentCount","present_count"},{"absentCount","absent_count"},{"verifiedCount","verified_count"},{"unverifiedCount","unverified_count"},{"notPerformedCount","not_performed_count"},{"historicalCount","historical_count"}})
                    if (selected.contains(pair[0])) row.put(pair[0], rs.getLong(pair[1]));
            });
        }
        if (selected.contains("attendanceHistory")) batch("select ar.student_user_id as sid, ar.attendance_date, ar.attendance_result, " + AttendanceLivenessSql.status("ar", "e") + " as liveness_status, ar.submission_type, ar.server_submitted_at from attendance_records ar left join attendance_evidence e on e.attendance_id=ar.id where ar.deleted_at is null and ar.student_user_id in (:ids)" + attendanceWindow + " order by ar.attendance_date", params,
                rs -> append(byId.get(rs.getLong("sid")), selected, "attendanceHistory", describe(rs, "attendance_date", "attendance_result", "liveness_status", "submission_type", "server_submitted_at")));
        if (selected.contains("attendanceEvidence")) batch("select ar.student_user_id as sid, ar.attendance_date, " + AttendanceLivenessSql.status("ar", "e") + " as liveness_status, " + AttendanceLivenessSql.reason("ar", "e") + " as liveness_reason, e.* from attendance_evidence e join attendance_records ar on ar.id=e.attendance_id where ar.deleted_at is null and ar.student_user_id in (:ids)" + attendanceWindow + " order by ar.attendance_date", params, rs -> {
            List<String> parts = new ArrayList<>(); parts.add(text(rs.getObject("attendance_date")));
            for (String column : List.of("liveness_status", "liveness_reason", "expected_work_mode", "daily_work_summary", "metadata_consistent", "camera_capture_used", "image_media_type", "image_size_bytes", "ai_challenge_type", "ai_face_detected", "ai_single_face", "ai_quality_passed", "ai_liveness_passed", "ai_model_version", "ai_processed_at", "ai_failure_reason")) {
                Object value = rs.getObject(column); if (value != null) parts.add(column.replace('_', ' ') + ": " + text(value));
            }
            append(byId.get(rs.getLong("sid")), selected, "attendanceEvidence", String.join(" | ", parts));
        });
        if (selected.contains("monthlyReportCount") && !has(selected, "monthlyReports", "monthlyPhotos")) {
            rows.forEach(row -> row.put("monthlyReportCount", 0L));
            batch("select o.student_user_id as sid, count(*) as reports_count from monthly_progress_reports m join internship_details i on i.id=m.student_internship_id join internship_onboarding o on o.id=i.onboarding_id where m.deleted_at is null and o.student_user_id in (:ids)" + monthlyWindow + " group by o.student_user_id", params,
                    rs -> byId.get(rs.getLong("sid")).put("monthlyReportCount", rs.getLong("reports_count")));
        }
        if (has(selected, "monthlyReports", "monthlyPhotos")) {
            rows.forEach(row -> row.put("monthlyReportCount", 0L));
            batch("select o.student_user_id as sid, m.* from monthly_progress_reports m join internship_details i on i.id=m.student_internship_id join internship_onboarding o on o.id=i.onboarding_id where m.deleted_at is null and o.student_user_id in (:ids)" + monthlyWindow + " order by m.report_month_number", params, rs -> {
                var row = byId.get(rs.getLong("sid")); row.put("monthlyReportCount", ((Number) row.get("monthlyReportCount")).longValue() + 1);
                String month = "Month " + rs.getInt("report_month_number") + ": ";
                append(row, selected, "monthlyReports", month + describe(rs, "period_start", "period_end", "work_description", "submitted_at", "review_status", "revision", "reviewed_at", "rejection_reason"));
                append(row, selected, "monthlyPhotos", month + describe(rs, "photo_original_name", "photo_mime_type", "photo_size", "image_width", "image_height", "image_sha256"));
            });
        }
        if (selected.contains("assignmentHistory")) batch("select a.student_user_id as sid, mp.name, mu.email, au.email as assigned_by_email, a.assigned_at, a.ended_at, a.active from mentor_student_assignments a join users mu on mu.id=a.mentor_user_id left join mentor_profiles mp on mp.user_id=mu.id join users au on au.id=a.assigned_by where a.student_user_id in (:ids) order by a.assigned_at", params,
                rs -> append(byId.get(rs.getLong("sid")), selected, "assignmentHistory", describe(rs, "name", "email", "assigned_by_email", "assigned_at", "ended_at", "active")));
    }
    private static boolean has(Set<String> selected, String... keys) { return Arrays.stream(keys).anyMatch(selected::contains); }
    private void batch(String sql, MapSqlParameterSource params, RowCallbackHandler handler) { jdbc.query(sql, params, handler); }
    private static void append(Map<String, Object> row, Set<String> selected, String key, String value) {
        if (!selected.contains(key)) return;
        Object previous = row.get(key);
        StringBuilder builder = previous instanceof StringBuilder b ? b : new StringBuilder();
        if (!builder.isEmpty()) builder.append('\n'); builder.append(value); row.put(key, builder);
    }
    private String describe(ResultSet rs, String... columns) throws SQLException {
        List<String> values = new ArrayList<>();
        for (String column : columns) {
            Object value = rs.getObject(column);
            if (value instanceof OffsetDateTime time) value = time.toInstant().atZone(clock.getZone()).toString();
            else if (value instanceof Timestamp time) value = time.toInstant().atZone(clock.getZone()).toString();
            if (value != null) values.add(text(value));
        }
        return String.join(" | ", values);
    }
    public static String text(Object value) {
        if (value == null) return "";
        if (value instanceof Boolean b) return b ? "Yes" : "No";
        if (value instanceof Timestamp t) return t.toInstant().toString();
        if (value instanceof OffsetDateTime t) return t.toInstant().toString();
        return value.toString();
    }
}
