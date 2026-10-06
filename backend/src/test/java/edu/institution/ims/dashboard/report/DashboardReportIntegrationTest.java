package edu.institution.ims.dashboard.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.attendance.*;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.monthlyprogress.*;
import edu.institution.ims.professionalprofile.*;
import edu.institution.ims.security.*;
import edu.institution.ims.studentprofile.*;
import edu.institution.ims.studentprofile.security.AadhaarEncryptionService;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:dashboard_reports;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ContextConfiguration(classes = {edu.institution.ims.ImsApplication.class, DashboardReportIntegrationTest.FixedClock.class})
class DashboardReportIntegrationTest {
    static final Instant NOW = Instant.parse("2026-10-05T06:30:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository accounts;
    @Autowired StudentProfileRepository profiles;
    @Autowired MentorProfileRepository mentors;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired AttendanceRepository attendance;
    @Autowired MonthlyProgressReportRepository monthly;
    @Autowired StudentProfessionalProfileRepository professional;
    @Autowired AadhaarEncryptionService encryption;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired DashboardReportCatalog catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entities;
    String passwordHash;
    User admin, mentorA, mentorB, studentA, studentB, missing;
    InternshipDetails internship;

    @BeforeEach void setup() {
        passwordHash = passwords.encode("ReportPassword!23");
        admin = user("report.admin@example.edu", Role.ADMIN);
        mentorA = user("report.mentor.a@example.edu", Role.MENTOR);
        mentorB = user("report.mentor.b@example.edu", Role.MENTOR);
        mentors.saveAndFlush(new MentorProfile(mentorA, "Dr. Mentor A", "EXPORT-A", "Computer Engineering", "Professor", "9876543210"));
        mentors.saveAndFlush(new MentorProfile(mentorB, "Dr. Mentor B", "EXPORT-B", "Civil", "Professor", "9876543211"));
        studentA = student("report.student.a@example.edu", "Ada, \"अदा\"\nStudent", "00001", 6);
        studentB = student("report.student.b@example.edu", "=HYPERLINK(\"https://example.com\")", "00002", 4);
        missing = user("report.missing@example.edu", Role.STUDENT); accounts.saveAndFlush(new StudentAccount(missing));
        StudentProfile profile = new StudentProfile(studentA);
        profile.apply("Ada, \"अदा\"\nStudent", Branch.COMPUTER_ENGINEERING, "00001", "001", "Private address", null,
                "ada@example.com", "ada@college.edu", "09876543210", "Guardian", "9876543210", "9876543211", "A+",
                "Profile Mentor", "9876543210", "Coordinator", "9876543210", "CDC", "9876543210", "2026-2027", 6);
        profile.setEncryptedAadhaar(encryption.encrypt("123456789012"), "9012"); profiles.saveAndFlush(profile);
        assignments.saveAndFlush(new MentorStudentAssignment(mentorA, studentA, admin, NOW));
        assignments.saveAndFlush(new MentorStudentAssignment(mentorB, studentB, admin, NOW));
        InternshipOnboarding onboarding = new InternshipOnboarding(studentA); onboarding.selectSource(InternshipSource.SELF);
        onboarding.markDocumentsComplete(); onboarding.confirmDiary(); onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
        internship = new InternshipDetails(onboarding);
        internship.apply("Alpha, Ltd", CompanyType.IT, null, "Company address", "9999999999", "Supervisor", "Lead", "9999999999", 3,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1), 13, InternshipMode.ONLINE, new BigDecimal("12345.67"), true);
        internships.saveAndFlush(internship);
        attendance.saveAndFlush(new AttendanceRecord(studentA, internship, TODAY, AttendanceResult.PRESENT, VerificationStatus.VERIFIED, AttendanceSubmissionType.BASIC, NOW));
        attendance.saveAndFlush(new AttendanceRecord(studentA, internship, TODAY.minusDays(1), AttendanceResult.PRESENT, VerificationStatus.UNVERIFIED, AttendanceSubmissionType.BASIC, NOW.minusSeconds(86400)));
        monthly.saveAndFlush(new MonthlyProgressReport(internship, 1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "Built API, tested \"exports\"\nSecond line", "private-photo-key", "progress.png", "image/png", 120, NOW, null, null, NOW));
        monthly.saveAndFlush(new MonthlyProgressReport(internship, 2, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "Second month", "private-photo-key-2", "second.png", "image/png", 130, null, null, null, NOW));
        StudentProfessionalProfile pp = new StudentProfessionalProfile(studentA); pp.update("Software engineer", "Backend development"); professional.saveAndFlush(pp);
        jdbc.update("insert into student_skills (professional_profile_id, display_name, normalized_name, active_normalized_name, source, is_active, created_at, updated_at) values (?, ?, ?, ?, 'STUDENT_ADDED', true, ?, ?)", pp.getId(), "Java", "java", "java", NOW, NOW);
        jdbc.update("insert into student_skills (professional_profile_id, display_name, normalized_name, active_normalized_name, source, is_active, created_at, updated_at) values (?, ?, ?, ?, 'STUDENT_ADDED', true, ?, ?)", pp.getId(), "SQL", "sql", "sql", NOW, NOW);
        entities.clear();
    }

    @Test void optionsAreRoleSpecificAndNeverExposeSqlOrPrivateStorageFields() throws Exception {
        var result = mvc.perform(get("/api/v1/admin/dashboard/reports/options").cookie(cookie(admin))).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("aadhaarNumber", "resumeFilename", "attendanceHistory").doesNotContain("expression", "password_hash", "storage_key", "aadhaar_encrypted");
        result = mvc.perform(get("/api/v1/mentor/dashboard/reports/options").cookie(cookie(mentorA))).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("skills", "monthlyReports").doesNotContain("aadhaarNumber", "permanentAddress", "resumeFilename", "attendanceHistory", "mentorId\",\"label\":\"Assigned mentor account ID\",\"type");
    }

    @Test void excelHasExactlySelectedColumnsTypedValuesMetadataAndFormatting() throws Exception {
        List<String> fields = List.of("uid", "studentName", "stipend", "startDate", "maskedAadhaar", "aadhaarNumber", "attendanceSubmittedAt");
        var response = export(admin, fields, Map.of("studentId", studentA.getId().toString()), "XLSX");
        assertThat(response.getResponse().getHeader("Content-Disposition")).contains("attachment", ".xlsx");
        assertThat(response.getResponse().getHeader("Cache-Control")).contains("no-store");
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheet("Students"); assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) fields.size());
            assertThat(sheet.getLastRowNum()).isEqualTo(1); assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Student UID");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("00001");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).contains("अदा", "\n");
            assertThat(sheet.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(12345.67);
            assertThat(DateUtil.isCellDateFormatted(sheet.getRow(1).getCell(3))).isTrue();
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("XXXX XXXX 9012");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("123456789012");
            assertThat(sheet.getRow(1).getCell(6).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 10, 5, 12, 0));
            assertThat(sheet.getColumnWidth(1)).isGreaterThan(20 * 256);
            assertThat(workbook.getSheet("Report Details").getRow(2).getCell(1).getStringCellValue()).contains("2026-10-05", "Asia/Kolkata");
        }
    }

    @Test void csvUsesUtf8BomEscapesSpecialCharactersAndNeutralizesFormulas() throws Exception {
        var response = export(admin, List.of("studentName", "uid", "companyName"), Map.of(), "CSV");
        byte[] bytes = response.getResponse().getContentAsByteArray(); assertThat(bytes).startsWith((byte) 0xef, (byte) 0xbb, (byte) 0xbf);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\ufeff\"Student name\",\"Student UID\",\"Company name\"\r\n")
                .contains("\"Ada, \"\"अदा\"\"\nStudent\",\"00001\",\"Alpha, Ltd\"\r\n")
                .contains("\"'=HYPERLINK(\"\"https://example.com\"\")\",\"00002\",\"\"\r\n")
                .endsWith("\"\",\"\",\"\"\r\n");
        try (Workbook workbook = workbook(export(admin, List.of("studentName"), Map.of("studentId", studentB.getId().toString()), "XLSX"))) {
            assertThat(workbook.getSheet("Students").getRow(1).getCell(0).getCellType()).isEqualTo(CellType.STRING);
        }
    }

    @Test void filtersComposeWithinAuthorizedStudentsAndEscapeSearchWildcards() throws Exception {
        preview(admin, List.of("uid"), Map.of("branch", "COMPUTER_ENGINEERING", "semester", "6", "academicYear", "2026-2027", "company", "ALPHA", "mentorId", mentorA.getId().toString(), "paid", "PAID", "fullTimeOffered", "YES", "verificationStatus", "VERIFIED", "workflowStatus", "INTERNSHIP_DETAILS_COMPLETE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentCount").value(1)).andExpect(jsonPath("$.students[0].uid").value("00001"));
        preview(admin, List.of("uid"), Map.of("search", "%")).andExpect(status().isOk()).andExpect(jsonPath("$.studentCount").value(0));
        preview(admin, List.of("uid"), Map.of("profileStatus", "INCOMPLETE", "attendanceStatus", "NOT_SUBMITTED")).andExpect(status().isOk()).andExpect(jsonPath("$.studentCount").value(2));
        preview(admin, List.of("uid"), Map.of("mentorAssignment", "UNASSIGNED")).andExpect(status().isOk()).andExpect(jsonPath("$.studentCount").value(1));
    }

    @Test void emptyResultsAndNullDataProduceValidFiles() throws Exception {
        var empty = export(admin, List.of("uid", "companyName"), Map.of("company", "No such company"), "XLSX");
        try (Workbook workbook = workbook(empty)) { assertThat(workbook.getSheet("Students").getLastRowNum()).isZero(); }
        var csv = export(admin, List.of("uid", "companyName"), Map.of("company", "No such company"), "CSV");
        assertThat(csv.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("\ufeff\"Student UID\",\"Company name\"\r\n");
        try (Workbook workbook = workbook(export(admin, List.of("uid", "stipend", "attendanceStatus", "documentsCount"), Map.of("studentId", missing.getId().toString()), "XLSX"))) {
            Row row = workbook.getSheet("Students").getRow(1);
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("NOT_SUBMITTED");
            assertThat(row.getCell(3).getNumericCellValue()).isZero();
        }
    }

    @Test void allFieldsAndCollectionsRemainOneRowPerStudentWithNoPrivateKeys() throws Exception {
        List<String> fields = catalog.fields(UserPrincipal.from(admin)).stream().map(DashboardReportCatalog.Field::id).toList();
        try (Workbook workbook = workbook(export(admin, fields, Map.of(), "XLSX"))) {
            Sheet sheet = workbook.getSheet("Students"); assertThat(sheet.getLastRowNum()).isEqualTo(3);
            Row first = sheet.getRow(1);
            assertThat(first.getCell(fields.indexOf("skills")).getStringCellValue()).isEqualTo("Java\nSQL");
            assertThat(first.getCell(fields.indexOf("attendanceCount")).getNumericCellValue()).isEqualTo(2);
            assertThat(first.getCell(fields.indexOf("monthlyReportCount")).getNumericCellValue()).isEqualTo(2);
            assertThat(first.getCell(fields.indexOf("monthlyReports")).getStringCellValue()).contains("Month 1:", "Month 2:", "Built API");
            assertThat(first.getCell(fields.indexOf("documents")).getStringCellValue()).contains("OFFER_LETTER: Missing", "STUDENT_NOC: Missing");
            for (Cell cell : first) assertThat(new DataFormatter().formatCellValue(cell)).doesNotContain("private-photo-key");
        }
        List<String> mentorFields = catalog.fields(UserPrincipal.from(mentorA)).stream().map(DashboardReportCatalog.Field::id).toList();
        try (Workbook workbook = workbook(export(mentorA, mentorFields, Map.of(), "XLSX"))) { assertThat(workbook.getSheet("Students").getLastRowNum()).isEqualTo(1); }
    }

    @Test void historyDateWindowLimitsCollectionsButDoesNotRemoveStudents() throws Exception {
        List<String> fields = List.of("uid", "attendanceCount", "verifiedCount", "unverifiedCount", "attendanceHistory", "monthlyReportCount", "monthlyReports");
        try (Workbook workbook = workbook(export(admin, fields, Map.of("historyFrom", "2026-10-05", "historyTo", "2026-10-05"), "XLSX"))) {
            Sheet sheet = workbook.getSheet("Students"); assertThat(sheet.getLastRowNum()).isEqualTo(3);
            Row row = sheet.getRow(1); assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(1); assertThat(row.getCell(3).getNumericCellValue()).isZero();
            assertThat(row.getCell(4).getStringCellValue()).contains("2026-10-05").doesNotContain("2026-10-04");
            assertThat(row.getCell(5).getNumericCellValue()).isZero();
        }
    }

    @Test void populatedDocumentsResumeVersionsAnalysisLocationAndHybridScheduleAreExportedSafely() throws Exception {
        long profileId = professional.findByStudentId(studentA.getId()).orElseThrow().getId();
        jdbc.update("insert into student_resumes (professional_profile_id, active_profile_id, original_filename, storage_key, content_type, file_size, uploaded_at, is_active, parsing_status) values (?, null, 'old.pdf', 'private-resume-old', 'application/pdf', 100, ?, false, 'NOT_PARSED')", profileId, NOW.minusSeconds(100));
        jdbc.update("insert into student_resumes (professional_profile_id, active_profile_id, original_filename, storage_key, content_type, file_size, uploaded_at, is_active, parsing_status) values (?, ?, 'current.pdf', 'private-resume-current', 'application/pdf', 110, ?, true, 'PARSED')", profileId, profileId, NOW);
        long resumeId = jdbc.queryForObject("select id from student_resumes where storage_key='private-resume-current'", Long.class);
        jdbc.update("insert into resume_analyses (resume_id, status, model_name, model_version, started_at, completed_at, created_at, updated_at) values (?, 'PARSED', 'local-model', 'v1', ?, ?, ?, ?)", resumeId, NOW, NOW, NOW, NOW);
        long analysisId = jdbc.queryForObject("select id from resume_analyses where resume_id=?", Long.class, resumeId);
        jdbc.update("insert into resume_extracted_skills (analysis_id, name, sort_order) values (?, 'Kubernetes', 0)", analysisId);
        long extractedId = jdbc.queryForObject("select id from resume_extracted_skills where analysis_id=?", Long.class, analysisId);
        jdbc.update("insert into resume_extracted_experiences (analysis_id, title, company, start_date, end_date, sort_order) values (?, 'Developer', 'Previous Company', '2025', '2026', 0)", analysisId);
        jdbc.update("insert into resume_extracted_education (analysis_id, degree, field, institution, sort_order) values (?, 'BE', 'Computer Engineering', 'College', 0)", analysisId);
        jdbc.update("insert into resume_extracted_certifications (analysis_id, name, sort_order) values (?, 'Cloud Certificate', 0)", analysisId);
        jdbc.update("insert into student_skill_suggestions (professional_profile_id, resume_analysis_id, extracted_skill_id, display_name, normalized_name, review_status, created_at) values (?, ?, ?, 'Kubernetes', 'kubernetes', 'PENDING', ?)", profileId, analysisId, extractedId, NOW);
        jdbc.update("insert into internship_documents (onboarding_id, document_type, original_file_name, storage_key, content_type, file_size, uploaded_at) values (?, 'OFFER_LETTER', 'offer.pdf', 'private-document-key', 'application/pdf', 100, ?)", internship.getOnboarding().getId(), NOW);
        jdbc.update("update internship_details set internship_mode='HYBRID' where id=?", internship.getId());
        jdbc.update("insert into hybrid_work_schedule (internship_id, day_of_week, work_mode, created_at, updated_at) values (?, 'MONDAY', 'REMOTE', ?, ?)", internship.getId(), NOW, NOW);
        jdbc.update("insert into internship_locations (internship_id, latitude, longitude, geofence_radius_meters, status, created_at, updated_at, confirmed_at, confirmed_by) values (?, 21.125, 79.0875, 200, 'CONFIRMED', ?, ?, ?, ?)", internship.getId(), NOW, NOW, NOW, admin.getId());
        long attendanceId = attendance.findByStudentIdAndAttendanceDate(studentA.getId(), TODAY).orElseThrow().getId();
        jdbc.update("insert into attendance_evidence (attendance_id, camera_capture_used, verification_reason, image_media_type, image_size_bytes, daily_work_summary, expected_work_mode, ai_face_detected, ai_single_face, ai_quality_passed, ai_liveness_passed, created_at) values (?, true, 'EDGE_AI_VERIFIED', 'image/jpeg', 100, 'Developed and tested backend endpoints', 'REMOTE', true, true, true, true, ?)", attendanceId, NOW);
        List<String> fields = List.of("resumeFilename", "resumeHistory", "extractedSkills", "experience", "education", "certifications", "skillSuggestions", "documents", "documentsCount", "hybridSchedule", "todayWorkMode", "locationStatus", "locationConfirmedBy", "attendanceEvidence");
        try (Workbook workbook = workbook(export(admin, fields, Map.of("studentId", studentA.getId().toString()), "XLSX"))) {
            Row row = workbook.getSheet("Students").getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("current.pdf");
            assertThat(row.getCell(1).getStringCellValue()).contains("old.pdf", "current.pdf");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("Kubernetes");
            assertThat(row.getCell(3).getStringCellValue()).contains("Developer", "Previous Company");
            assertThat(row.getCell(4).getStringCellValue()).contains("BE", "Computer Engineering", "College");
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("Cloud Certificate");
            assertThat(row.getCell(6).getStringCellValue()).contains("Kubernetes", "PENDING");
            assertThat(row.getCell(7).getStringCellValue()).contains("OFFER_LETTER: Uploaded", "offer.pdf", "STUDENT_NOC: Missing");
            assertThat(row.getCell(8).getNumericCellValue()).isEqualTo(1);
            assertThat(row.getCell(9).getStringCellValue()).contains("MONDAY", "REMOTE");
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("REMOTE");
            assertThat(row.getCell(11).getStringCellValue()).isEqualTo("CONFIRMED");
            assertThat(row.getCell(12).getStringCellValue()).isEqualTo(admin.getEmail());
            assertThat(row.getCell(13).getStringCellValue()).contains("EDGE_AI_VERIFIED", "ai liveness passed: Yes");
            for (Cell cell : row) assertThat(new DataFormatter().formatCellValue(cell)).doesNotContain("private-resume", "private-document-key");
        }
    }

    @Test void mentorCannotExportGuessedIdsOtherAssignmentsOrPrivilegedColumns() throws Exception {
        var response = export(mentorA, List.of("uid", "studentName"), Map.of(), "CSV");
        assertThat(response.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("00001").doesNotContain("00002", missing.getEmail());
        preview(mentorA, List.of("uid"), Map.of("company", "Other company")).andExpect(status().isOk()).andExpect(jsonPath("$.studentCount").value(0));
        postReport(mentorA, "export", List.of("uid"), Map.of("studentId", studentB.getId().toString()), "CSV").andExpect(status().isForbidden());
        preview(mentorA, List.of("uid"), Map.of("studentId", missing.getId().toString())).andExpect(status().isForbidden());
        for (String field : List.of("aadhaarNumber", "permanentAddress", "resumeFilename", "attendanceHistory", "attendanceEvidence", "stipend"))
            postReport(mentorA, "export", List.of(field), Map.of(), "XLSX").andExpect(status().isBadRequest());
        preview(mentorA, List.of("uid"), Map.of("mentorId", mentorB.getId().toString())).andExpect(status().isBadRequest());
        preview(mentorA, List.of("uid"), Map.of("attendanceDate", "2026-10-04")).andExpect(status().isBadRequest());
        jdbc.update("update mentor_student_assignments set active=false, active_student_user_id=null, ended_at=? where student_user_id=?", NOW.plusSeconds(1), studentA.getId());
        preview(mentorA, List.of("uid"), Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.studentCount").value(0));
    }

    @Test void securityFiltersRejectUnauthenticatedWrongRoleInactiveAndPasswordChangeAccounts() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard/reports/options")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/dashboard/reports/options").cookie(cookie(mentorA))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mentor/dashboard/reports/options").cookie(cookie(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mentor/dashboard/reports/options").cookie(cookie(studentA))).andExpect(status().isForbidden());
        mentorA.requirePasswordChange(); users.saveAndFlush(mentorA);
        preview(mentorA, List.of("uid"), Map.of()).andExpect(status().isForbidden());
        jdbc.update("update users set active=false where id=?", mentorB.getId());
        entities.clear();
        mvc.perform(get("/api/v1/mentor/dashboard/reports/options").cookie(cookie(mentorB))).andExpect(status().isUnauthorized());
    }

    @Test void invalidRequestsNeverGenerateFilesOrAcceptArbitraryColumnsAndSql() throws Exception {
        preview(admin, List.of(), Map.of()).andExpect(status().isBadRequest());
        preview(admin, List.of("uid", "uid"), Map.of()).andExpect(status().isBadRequest());
        preview(admin, List.of("password_hash"), Map.of()).andExpect(status().isBadRequest());
        preview(admin, List.of("u.email from users"), Map.of()).andExpect(status().isBadRequest());
        preview(admin, List.of("uid"), Map.of("sql", "1=1")).andExpect(status().isBadRequest());
        preview(admin, List.of("uid"), Map.of("semester", "9")).andExpect(status().isBadRequest());
        preview(admin, List.of("uid"), Map.of("studentId", "-1")).andExpect(status().isBadRequest());
        preview(admin, List.of("uid"), Map.of("studentId", admin.getId().toString())).andExpect(status().isForbidden());
        preview(admin, List.of("uid"), Map.of("attendanceDate", "not-a-date")).andExpect(status().isBadRequest());
        preview(admin, List.of("uid"), Map.of("historyFrom", "2026-10-06", "historyTo", "2026-10-01")).andExpect(status().isBadRequest());
        postReport(admin, "export", List.of("uid"), Map.of(), "PDF").andExpect(status().isBadRequest());
    }

    @Test void batchingExportsMoreThanOneStudentPageAndExistingDashboardsStillRespond() throws Exception {
        for (int i = 0; i < 251; i++) user("report.batch." + i + "@example.edu", Role.STUDENT);
        var response = export(admin, List.of("loginEmail"), Map.of(), "XLSX");
        try (Workbook workbook = workbook(response)) { assertThat(workbook.getSheet("Students").getLastRowNum()).isEqualTo(254); }
        mvc.perform(get("/api/v1/admin/dashboard/summary").cookie(cookie(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mentor/attendance/today").cookie(cookie(mentorA))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test void oversizedExcelCellsFailClearlyAndCsvRetainsTheirContent() throws Exception {
        Path xlsxFile = Files.createTempFile("report-limit-test-", ".xlsx");
        var field = catalog.selected(UserPrincipal.from(admin), List.of("monthlyReports"));
        assertThatThrownBy(() -> {
            try (var writer = new DashboardReportWriter(xlsxFile, DashboardReportRequest.Format.XLSX, field, ZoneId.of("Asia/Kolkata"))) {
                writer.row(Map.of("monthlyReports", "x".repeat(32768)));
            }
        }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32,767");
        assertThat(Files.exists(xlsxFile)).isFalse();
        Path file = Files.createTempFile("report-limit-test-", ".csv");
        try {
            try (var writer = new DashboardReportWriter(file, DashboardReportRequest.Format.CSV, field, ZoneId.of("Asia/Kolkata"))) {
                writer.row(Map.of("monthlyReports", "x".repeat(32768))); writer.finish(Map.of());
            }
            assertThat(Files.readString(file)).contains("x".repeat(32768));
        } finally { Files.deleteIfExists(file); }
    }

    private User user(String email, Role role) { return users.saveAndFlush(new User(email, passwordHash, role, true)); }
    private User student(String email, String name, String uid, int semester) {
        User user = user(email, Role.STUDENT); accounts.saveAndFlush(new StudentAccount(user, name, uid, semester)); return user;
    }
    private Cookie cookie(User user) { return new Cookie("ims_auth", jwt.create(UserPrincipal.from(user))); }
    private ResultActions preview(User user, List<String> fields, Map<String, String> filters) throws Exception { return postReport(user, "preview", fields, filters, "XLSX"); }
    private ResultActions postReport(User user, String action, List<String> fields, Map<String, String> filters, String format) throws Exception {
        String path = user.getRole() == Role.ADMIN ? "admin" : "mentor";
        return mvc.perform(post("/api/v1/" + path + "/dashboard/reports/" + action).cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("fields", fields, "filters", filters, "format", format))));
    }
    private MvcResult export(User user, List<String> fields, Map<String, String> filters, String format) throws Exception {
        return postReport(user, "export", fields, filters, format).andExpect(status().isOk()).andReturn();
    }
    private Workbook workbook(MvcResult response) throws IOException { return WorkbookFactory.create(new ByteArrayInputStream(response.getResponse().getContentAsByteArray())); }

    private ResultActions typedReport(User user, String action, String type, List<String> fields, Map<String,String> filters, List<Long> ids, String format) throws Exception {
        Map<String,Object> body=new LinkedHashMap<>();body.put("reportType",type);body.put("fields",fields);body.put("filters",filters);body.put("studentIds",ids);body.put("page",0);body.put("format",format);
        return mvc.perform(post("/api/v1/"+(user.getRole()==Role.ADMIN?"admin":"mentor")+"/reports/"+action).cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    @Test void everyAvailableInstitutionalReportPreviewsAndExportsBothFormats() throws Exception {
        var options=mvc.perform(get("/api/v1/admin/reports/types").cookie(cookie(admin))).andExpect(status().isOk()).andReturn();
        var types=json.readTree(options.getResponse().getContentAsString()).get("types");
        for(var type:types) {
            if(!type.get("available").asBoolean())continue;
            List<String> fields=new ArrayList<>();type.get("fields").forEach(f->fields.add(f.get("id").asText()));
            typedReport(admin,"table",type.get("id").asText(),fields,Map.of(),List.of(),"XLSX").andExpect(status().isOk());
            for(String format:List.of("XLSX","CSV")) {
                var response=typedReport(admin,"generate",type.get("id").asText(),fields,Map.of(),List.of(),format).andExpect(status().isOk()).andReturn();
                if(format.equals("XLSX"))try(Workbook book=workbook(response)) {
                    String previewDir=System.getProperty("report.previewDir");
                    if(previewDir!=null) {Files.createDirectories(Path.of(previewDir));Files.write(Path.of(previewDir,type.get("id").asText()+".xlsx"),response.getResponse().getContentAsByteArray());}
                    Sheet sheet=book.getSheet("Students");assertThat(sheet.getRow(3).getLastCellNum()).isEqualTo((short)fields.size());
                    assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();assertThat(sheet.getNumMergedRegions()).isEqualTo(2);
                    assertThat(book.getFontAt(sheet.getRow(3).getCell(0).getCellStyle().getFontIndex()).getFontName()).isEqualTo("Times New Roman");
                    if(type.get("id").asText().equals("ROSTER")) {
                        assertThat(sheet.getRow(4).getCell(fields.indexOf("uid")).getStringCellValue()).isEqualTo("00001");
                        Cell amount=sheet.getRow(4).getCell(fields.indexOf("stipend"));assertThat(amount.getNumericCellValue()).isEqualTo(12345.67);assertThat(amount.getCellStyle().getDataFormatString()).contains("INR");
                    }
                } else assertThat(response.getResponse().getContentAsByteArray()).startsWith((byte)0xef,(byte)0xbb,(byte)0xbf);
            }
        }
    }
    @Test void typedReportsRejectGuessedIdsUnknownFieldsFiltersAndUnavailableTypes() throws Exception {
        for(String action:List.of("table","generate")) {
            typedReport(mentorA,action,"ATTENDANCE",List.of("uid"),Map.of("company","does not match"),List.of(studentB.getId()),"CSV").andExpect(status().isForbidden());
            typedReport(mentorA,action,"ATTENDANCE",List.of("uid"),Map.of(),List.of(studentA.getId(),studentB.getId()),"XLSX").andExpect(status().isForbidden());
            typedReport(mentorA,action,"ATTENDANCE",List.of("uid"),Map.of("studentId",studentB.getId().toString()),List.of(),"CSV").andExpect(status().isForbidden());
            typedReport(admin,action,"ATTENDANCE",List.of("password_hash"),Map.of(),List.of(),"XLSX").andExpect(status().isBadRequest());
            typedReport(admin,action,"ATTENDANCE",List.of("uid"),Map.of("sql","1=1"),List.of(),"XLSX").andExpect(status().isBadRequest());
            typedReport(admin,action,"SEMINAR",List.of("uid"),Map.of(),List.of(),"XLSX").andExpect(status().isBadRequest());
        }
    }
    @Test void mentorAttendanceHistoryAndFilteredExportsUseSameAuthorizedDataset() throws Exception {
        typedReport(mentorA,"table","ATTENDANCE",List.of("uid","date","result","verification"),Map.of(),List.of(),"XLSX")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRows").value(2)).andExpect(jsonPath("$.summary.present").value(2)).andExpect(jsonPath("$.summary.verified").value(1));
        var filters=Map.of("historyFrom","2026-10-04","historyTo","2026-10-04","verificationStatus","UNVERIFIED");
        typedReport(mentorA,"table","ATTENDANCE",List.of("uid","date","verification"),filters,List.of(),"XLSX")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRows").value(1)).andExpect(jsonPath("$.rows[0].uid").value("00001"));
        var csv=typedReport(mentorA,"generate","ATTENDANCE",List.of("uid","date","verification"),filters,List.of(),"CSV").andExpect(status().isOk()).andReturn();
        assertThat(csv.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("2026-10-04","UNVERIFIED").doesNotContain("2026-10-05","00002");
        typedReport(mentorB,"table","ATTENDANCE",List.of("uid"),Map.of(),List.of(),"XLSX").andExpect(status().isOk()).andExpect(jsonPath("$.totalRows").value(0));
    }
    @Test void summaryUsesStoredRecordsWithoutInventingWorkingDaysOrAbsentSubmissions() throws Exception {
        typedReport(mentorA,"table","ATTENDANCE_SUMMARY",List.of("uid","total","present","absent","percentage"),Map.of(),List.of(studentA.getId()),"XLSX")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].total").value(2)).andExpect(jsonPath("$.rows[0].absent").value(0)).andExpect(jsonPath("$.rows[0].percentage").value(100));
        typedReport(admin,"table","ATTENDANCE_SUMMARY",List.of("uid","total","percentage"),Map.of("studentId",missing.getId().toString()),List.of(),"XLSX")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].total").value(0)).andExpect(jsonPath("$.rows[0].percentage").isEmpty());
    }
    @Test void summariesPreserveUnknownDataAndActualWorkflow() throws Exception {
        typedReport(admin,"table","OVERALL",List.of("category","total","percentage"),Map.of(),List.of(studentA.getId()),"XLSX")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].category").value("SELF")).andExpect(jsonPath("$.rows[0].total").value(1)).andExpect(jsonPath("$.rows[0].percentage").value(100));
        typedReport(mentorA,"table","STIPEND",List.of("category","paid","unpaid","unknown","total"),Map.of(),List.of(),"XLSX")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].paid").value(1)).andExpect(jsonPath("$.rows[0].unpaid").value(0));
        typedReport(admin,"table","WORKFLOW",List.of("category","total"),Map.of("studentId",studentA.getId().toString()),List.of(),"XLSX")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].category").value("INTERNSHIP_DETAILS_COMPLETE"));
    }

    @TestConfiguration static class FixedClock {
        @Bean @Primary Clock reportClock() { return Clock.fixed(NOW, ZoneId.of("Asia/Kolkata")); }
    }
}
