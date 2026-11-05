package edu.institution.ims.dashboard.report.custom;

import edu.institution.ims.dashboard.report.DashboardReportService;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.UserRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CustomReportService {

    private final CustomReportCatalog catalog;
    private final CustomReportQueryBuilder queryBuilder;
    private final InstitutionalReportProperties institutionalProps;
    private final SavedReportConfigurationRepository savedRepo;
    private final UserRepository userRepo;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Kolkata");

    public CustomReportService(CustomReportCatalog catalog,
                               CustomReportQueryBuilder queryBuilder,
                               InstitutionalReportProperties institutionalProps,
                               SavedReportConfigurationRepository savedRepo,
                               UserRepository userRepo,
                               NamedParameterJdbcTemplate jdbc,
                               Clock clock) {
        this.catalog = catalog;
        this.queryBuilder = queryBuilder;
        this.institutionalProps = institutionalProps;
        this.savedRepo = savedRepo;
        this.userRepo = userRepo;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private void requireAdmin(UserPrincipal user) {
        if (user == null || !"ADMIN".equals(user.role())) {
            throw new AccessDeniedException("Only authorized III Cell administrators can access custom reports");
        }
    }

    public List<CustomReportCatalog.Field> getCatalogFields(UserPrincipal user) {
        requireAdmin(user);
        return catalog.getAllFields();
    }

    public List<String> getDistinctCompanies(UserPrincipal user, Long academicSessionId) {
        requireAdmin(user);
        String sql = """
                select distinct trim(i.company_name) as cname
                from internship_details i
                join internship_onboarding o on o.id = i.onboarding_id
                join users u on u.id = o.student_user_id
                where u.active = true
                  and i.company_name is not null
                  and trim(i.company_name) <> ''
                """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (academicSessionId != null && academicSessionId > 0) {
            sql += " and u.academic_session_id = :sessionId";
            params.addValue("sessionId", academicSessionId);
        }
        sql += " order by cname asc";
        return jdbc.query(sql, params, (rs, rowNum) -> rs.getString("cname"));
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public CustomReportResponse preview(UserPrincipal user, CustomReportRequest request) {
        requireAdmin(user);
        CustomReportQueryBuilder.BuiltQuery built = queryBuilder.build(request);

        int pageSize = request.size() != null && request.size() > 0 ? Math.min(request.size(), 200) : 50;
        int pageNumber = request.page() != null && request.page() >= 0 ? request.page() : 0;
        int offset = pageNumber * pageSize;

        // Total matching count
        Long totalRecords = jdbc.queryForObject(built.countSql(), built.params(), Long.class);
        long recordsCount = totalRecords != null ? totalRecords : 0;

        // Distinct student count
        String studentCountSql = "select count(distinct u.id) " + built.fromClause() + " " + built.whereClause();
        Long totalStudents = jdbc.queryForObject(studentCountSql, built.params(), Long.class);

        // Distinct internship count
        String internshipCountSql = "select count(distinct i.id) " + built.fromClause() + " " + built.whereClause();
        Long totalInternships = jdbc.queryForObject(internshipCountSql, built.params(), Long.class);

        // Paginated query
        MapSqlParameterSource pageParams = new MapSqlParameterSource(built.params().getValues());
        pageParams.addValue("previewLimit", pageSize);
        pageParams.addValue("previewOffset", offset);
        String paginatedSql = built.selectSql() + " limit :previewLimit offset :previewOffset";

        List<Map<String, Object>> rows = fetchRows(paginatedSql, pageParams, built.selectedFields(), built.groupByFieldId());

        // Group rows and calculate group summaries
        List<CustomReportResponse.GroupData> groups = groupRows(rows, built.groupByFieldId());

        // Compute overall summary
        Map<String, Object> overallSummary = calculateOverallSummary(rows);

        // Academic session name
        String sessionName = resolveSessionName(request.academicSessionId());

        int totalPages = pageSize > 0 ? (int) Math.ceil((double) recordsCount / pageSize) : 1;
        String generatedAt = TIMESTAMP_FORMATTER.withZone(REPORT_ZONE).format(Instant.now(clock));

        return new CustomReportResponse(
                institutionalProps.getName(),
                institutionalProps.getDepartment(),
                institutionalProps.getHeader(),
                request.title(),
                sessionName,
                generatedAt,
                REPORT_ZONE.getId(),
                built.filterSummary(),
                built.selectedFields(),
                request.scope() == null ? "INTERNSHIP" : request.scope(),
                built.groupByFieldId(),
                totalStudents != null ? totalStudents : 0,
                totalInternships != null ? totalInternships : 0,
                recordsCount,
                overallSummary,
                groups,
                pageNumber,
                pageSize,
                totalPages
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> count(UserPrincipal user, CustomReportRequest request) {
        requireAdmin(user);
        CustomReportQueryBuilder.BuiltQuery built = queryBuilder.build(request);

        Long totalRecords = jdbc.queryForObject(built.countSql(), built.params(), Long.class);
        String studentCountSql = "select count(distinct u.id) " + built.fromClause() + " " + built.whereClause();
        Long totalStudents = jdbc.queryForObject(studentCountSql, built.params(), Long.class);
        String internshipCountSql = "select count(distinct i.id) " + built.fromClause() + " " + built.whereClause();
        Long totalInternships = jdbc.queryForObject(internshipCountSql, built.params(), Long.class);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalRecords", totalRecords != null ? totalRecords : 0L);
        res.put("totalStudents", totalStudents != null ? totalStudents : 0L);
        res.put("totalInternships", totalInternships != null ? totalInternships : 0L);
        res.put("scope", request.scope() == null ? "INTERNSHIP" : request.scope());
        return res;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public DashboardReportService.Download export(UserPrincipal user, CustomReportRequest request) throws IOException {
        requireAdmin(user);
        CustomReportQueryBuilder.BuiltQuery built = queryBuilder.build(request);

        // Fetch all rows for export (safe limit 50,000)
        MapSqlParameterSource exportParams = new MapSqlParameterSource(built.params().getValues());
        exportParams.addValue("exportLimit", 50000);
        String exportSql = built.selectSql() + " limit :exportLimit";

        List<Map<String, Object>> allRows = fetchRows(exportSql, exportParams, built.selectedFields(), built.groupByFieldId());
        List<CustomReportResponse.GroupData> groups = groupRows(allRows, built.groupByFieldId());

        String format = request.format() == null ? "XLSX" : request.format().toUpperCase(Locale.ROOT);
        String extension = switch (format) {
            case "CSV" -> "csv";
            case "PDF" -> "pdf";
            default -> "xlsx";
        };
        String contentType = switch (format) {
            case "CSV" -> "text/csv; charset=UTF-8";
            case "PDF" -> "application/pdf";
            default -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        };

        String filename = sanitizeFilename(request.title(), extension);

        Path tempFile = Files.createTempFile("ims-custom-report-", "." + extension);
        String sessionName = resolveSessionName(request.academicSessionId());
        String generatedAt = TIMESTAMP_FORMATTER.withZone(REPORT_ZONE).format(Instant.now(clock));

        try {
            if ("PDF".equals(format)) {
                try (CustomReportPdfWriter pdfWriter = new CustomReportPdfWriter(
                        tempFile, built.selectedFields(),
                        institutionalProps.getName(), institutionalProps.getDepartment(), institutionalProps.getHeader(),
                        request.title(), sessionName, generatedAt, built.filterSummary(), built.groupByFieldId())) {
                    pdfWriter.writeGroups(groups);
                }
            } else {
                try (CustomReportWriter writer = new CustomReportWriter(
                        tempFile, format, built.selectedFields(),
                        institutionalProps.getName(), institutionalProps.getDepartment(), institutionalProps.getHeader(),
                        request.title(), sessionName, generatedAt, built.filterSummary(), built.groupByFieldId())) {
                    writer.writeGroups(groups);
                }
            }
            return new DashboardReportService.Download(tempFile, filename, contentType, allRows.size());
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    public static String sanitizeFilename(String rawTitle, String extension) {
        String ext = extension.toLowerCase(Locale.ROOT);
        String base = rawTitle == null ? "" : rawTitle.trim();
        if (base.toLowerCase(Locale.ROOT).endsWith("." + ext)) {
            base = base.substring(0, base.length() - (ext.length() + 1)).trim();
        }
        String sanitized = base.replaceAll("[^a-zA-Z0-9_-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (sanitized.isBlank()) {
            sanitized = "Internship_Report";
        }
        return sanitized + "." + ext;
    }

    private List<Map<String, Object>> fetchRows(String sql, MapSqlParameterSource params,
                                                List<CustomReportCatalog.Field> fields, String groupByFieldId) {
        return jdbc.query(sql, params, (rs, rowNum) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            for (var field : fields) {
                Object val = rs.getObject(field.id());
                map.put(field.id(), val);
            }
            if (!"NONE".equalsIgnoreCase(groupByFieldId) && !map.containsKey(groupByFieldId)) {
                map.put(groupByFieldId, rs.getObject(groupByFieldId));
            }
            return map;
        });
    }

    private List<CustomReportResponse.GroupData> groupRows(List<Map<String, Object>> rows, String groupByFieldId) {
        if ("NONE".equalsIgnoreCase(groupByFieldId)) {
            Map<String, Object> sum = calculateGroupSummary(rows);
            return List.of(new CustomReportResponse.GroupData("All Records", rows.size(), sum, rows));
        }

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (var row : rows) {
            Object gVal = row.get(groupByFieldId);
            String groupKey = gVal == null || gVal.toString().trim().isBlank() ? "Unassigned" : gVal.toString().trim();
            grouped.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(row);
        }

        List<CustomReportResponse.GroupData> list = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Map<String, Object> summary = calculateGroupSummary(entry.getValue());
            list.add(new CustomReportResponse.GroupData(entry.getKey(), entry.getValue().size(), summary, entry.getValue()));
        }
        return list;
    }

    private Map<String, Object> calculateGroupSummary(List<Map<String, Object>> groupRows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (groupRows == null || groupRows.isEmpty()) return summary;

        double ctcSum = 0; int ctcCount = 0;
        double stipendSum = 0; int stipendCount = 0;
        double attSum = 0; int attCount = 0;

        for (var r : groupRows) {
            if (r.get("annualCtc") instanceof Number n) {
                ctcSum += n.doubleValue();
                ctcCount++;
            }
            if (r.get("stipend") instanceof Number n) {
                stipendSum += n.doubleValue();
                stipendCount++;
            }
            if (r.get("attendancePercentage") instanceof Number n) {
                attSum += n.doubleValue();
                attCount++;
            }
        }

        if (ctcCount > 0) {
            summary.put("Average CTC (LPA)", BigDecimal.valueOf(ctcSum / ctcCount).setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
        if (stipendCount > 0) {
            summary.put("Average Stipend", BigDecimal.valueOf(stipendSum / stipendCount).setScale(1, RoundingMode.HALF_UP).doubleValue());
        }
        if (attCount > 0) {
            summary.put("Average Attendance (%)", BigDecimal.valueOf(attSum / attCount).setScale(1, RoundingMode.HALF_UP).doubleValue());
        }
        return summary;
    }

    private Map<String, Object> calculateOverallSummary(List<Map<String, Object>> rows) {
        return calculateGroupSummary(rows);
    }

    private String resolveSessionName(Long academicSessionId) {
        if (academicSessionId == null || academicSessionId <= 0) return "All Academic Sessions";
        try {
            return jdbc.queryForObject(
                    "select name from academic_sessions where id = :id",
                    new MapSqlParameterSource("id", academicSessionId),
                    String.class
            );
        } catch (Exception e) {
            return "Session #" + academicSessionId;
        }
    }

    // --- Saved Report Configurations CRUD ---

    @Transactional(readOnly = true)
    public List<SavedReportDto> listSavedReports(UserPrincipal user) {
        requireAdmin(user);
        return savedRepo.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SavedReportDto getSavedReport(UserPrincipal user, Long id) {
        requireAdmin(user);
        SavedReportConfiguration saved = savedRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Saved report configuration not found"));
        return toDto(saved);
    }

    @Transactional
    public SavedReportDto saveReport(UserPrincipal user, SaveReportRequest request) {
        requireAdmin(user);
        var creator = userRepo.findById(user.id())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        SavedReportConfiguration entity = new SavedReportConfiguration(
                request.title(), request.description(), request.scope(), request.configurationJson(), creator
        );
        return toDto(savedRepo.save(entity));
    }

    @Transactional
    public SavedReportDto updateReport(UserPrincipal user, Long id, SaveReportRequest request) {
        requireAdmin(user);
        SavedReportConfiguration entity = savedRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Saved report configuration not found"));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        if (request.scope() != null && !request.scope().isBlank()) {
            entity.setScope(request.scope());
        }
        entity.setConfigurationJson(request.configurationJson());
        return toDto(savedRepo.save(entity));
    }

    @Transactional
    public void deleteReport(UserPrincipal user, Long id) {
        requireAdmin(user);
        if (!savedRepo.existsById(id)) {
            throw new IllegalArgumentException("Saved report configuration not found");
        }
        savedRepo.deleteById(id);
    }

    private SavedReportDto toDto(SavedReportConfiguration s) {
        return new SavedReportDto(
                s.getId(),
                s.getTitle(),
                s.getDescription(),
                s.getScope(),
                s.getConfigurationJson(),
                s.getCreatedBy() != null ? s.getCreatedBy().getId() : null,
                s.getCreatedBy() != null ? s.getCreatedBy().getEmail() : null,
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
