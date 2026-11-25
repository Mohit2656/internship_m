package edu.institution.ims.dashboard.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.attendance.*;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.monthlyprogress.*;
import edu.institution.ims.security.*;
import edu.institution.ims.studentprofile.*;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:quick_reports;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ContextConfiguration(classes = {edu.institution.ims.ImsApplication.class, QuickReportsIntegrationTest.FixedClock.class})
class QuickReportsIntegrationTest {
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
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;

    String passwordHash;
    User admin, mentor;
    User studentDept, studentCdc, studentSelf;
    InternshipDetails internDept, internCdc, internSelf;

    @BeforeEach
    void setup() {
        passwordHash = passwords.encode("Password!123");
        admin = user("quick.admin@example.edu", Role.ADMIN);
        mentor = user("quick.mentor@example.edu", Role.MENTOR);
        mentors.saveAndFlush(new MentorProfile(mentor, "Dr. Report Mentor", "FAC-01", "Computer Engineering", "Professor", "9876543210"));

        // Student 1: Department Internship (Stipend 4000, Not Completed, Ongoing)
        studentDept = student("dept.student@example.edu", "Dept Student", "00101", 6);
        assignments.saveAndFlush(new MentorStudentAssignment(mentor, studentDept, admin, NOW));
        InternshipOnboarding ob1 = new InternshipOnboarding(studentDept);
        ob1.selectSource(InternshipSource.DEPARTMENT);
        ob1.markDocumentsComplete();
        ob1.confirmDiary();
        ob1.markInternshipDetailsComplete();
        onboardings.saveAndFlush(ob1);
        internDept = new InternshipDetails(ob1);
        internDept.apply("Google", CompanyType.IT, null, "Bangalore", "9999999999", "Sup1", "Lead", "9999999999", 3,
                TODAY.minusDays(30), TODAY.plusDays(60), 13, InternshipMode.ONLINE, new BigDecimal("4000.00"), false);
        internships.saveAndFlush(internDept);
        attendance.saveAndFlush(new AttendanceRecord(studentDept, internDept, TODAY, AttendanceResult.PRESENT, VerificationStatus.VERIFIED, AttendanceSubmissionType.BASIC, NOW));

        // Student 2: CDC Internship (Stipend 15000, Placement offer true, Completed)
        studentCdc = student("cdc.student@example.edu", "CDC Student", "00102", 6);
        assignments.saveAndFlush(new MentorStudentAssignment(mentor, studentCdc, admin, NOW));
        InternshipOnboarding ob2 = new InternshipOnboarding(studentCdc);
        ob2.selectSource(InternshipSource.CDC);
        ob2.markDocumentsComplete();
        ob2.confirmDiary();
        ob2.markInternshipDetailsComplete();
        onboardings.saveAndFlush(ob2);
        internCdc = new InternshipDetails(ob2);
        internCdc.apply("Microsoft", CompanyType.IT, null, "Hyderabad", "9999999998", "Sup2", "Manager", "9999999998", 3,
                TODAY.minusDays(90), TODAY.minusDays(10), 13, InternshipMode.OFFICE_REPORTING, new BigDecimal("15000.00"), true);
        internships.saveAndFlush(internCdc);
        jdbc.update("update internship_details set completion_status = 'COMPLETED', completed_at = ?, completion_reviewer_id = ?, completion_remarks = ? where id = ?",
                java.sql.Timestamp.from(NOW), admin.getId(), "Excellent work", internCdc.getId());
        monthly.saveAndFlush(new MonthlyProgressReport(internCdc, 1, TODAY.minusDays(80), TODAY.minusDays(50), "Report 1", "key1", "progress.png", "image/png", 100, NOW, null, null, NOW));
        jdbc.update("update monthly_progress_reports set review_status = 'APPROVED', reviewer_id = ?, reviewed_at = ? where id > 0",
                admin.getId(), java.sql.Timestamp.from(NOW));

        // Student 3: Self Internship (Unpaid, No placement offer, Start date in future -> Not Started / Not Joined)
        studentSelf = student("self.student@example.edu", "Self Student", "00103", 6);
        assignments.saveAndFlush(new MentorStudentAssignment(mentor, studentSelf, admin, NOW));
        InternshipOnboarding ob3 = new InternshipOnboarding(studentSelf);
        ob3.selectSource(InternshipSource.SELF);
        ob3.markDocumentsComplete();
        ob3.confirmDiary();
        ob3.markInternshipDetailsComplete();
        onboardings.saveAndFlush(ob3);
        internSelf = new InternshipDetails(ob3);
        internSelf.apply("Google", CompanyType.IT, null, "Mumbai", "9999999997", "Sup3", "Lead", "9999999997", 3,
                TODAY.plusDays(10), TODAY.plusDays(70), 13, InternshipMode.ONLINE, BigDecimal.ZERO, false);
        internships.saveAndFlush(internSelf);
    }

    private User user(String email, Role role) {
        return users.saveAndFlush(new User(email, passwordHash, role, true));
    }

    private User student(String email, String name, String uid, int semester) {
        User u = user(email, Role.STUDENT);
        accounts.saveAndFlush(new StudentAccount(u, name, uid, semester));
        StudentProfile p = new StudentProfile(u);
        p.apply(name, Branch.COMPUTER_ENGINEERING, uid, uid, "Address", null,
                email, "clg." + email, "9876543210", "G", "9876543210", "9876543210", "O+",
                "Mentor", "9876543210", "Coord", "9876543210", "CDC", "9876543210", "2026-2027", semester);
        profiles.saveAndFlush(p);
        return u;
    }

    private Cookie cookie(User u) {
        return new Cookie("ims_auth", jwt.create(UserPrincipal.from(u)));
    }

    private ResultActions requestTable(User u, String reportType) throws Exception {
        Map<String, Object> body = Map.of(
                "reportType", reportType,
                "fields", List.of(),
                "filters", Map.of(),
                "studentIds", List.of(),
                "page", 0,
                "format", "XLSX"
        );
        return mvc.perform(post("/api/v1/admin/reports/table")
                .cookie(cookie(u))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions requestExport(User u, String reportType, String format) throws Exception {
        Map<String, Object> body = Map.of(
                "reportType", reportType,
                "fields", List.of(),
                "filters", Map.of(),
                "studentIds", List.of(),
                "page", 0,
                "format", format
        );
        return mvc.perform(post("/api/v1/admin/reports/generate")
                .cookie(cookie(u))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    @Test
    void optionsForAdminExposesExactlyTenQuickReports() throws Exception {
        var result = mvc.perform(get("/api/v1/admin/reports/types").cookie(cookie(admin)))
                .andExpect(status().isOk())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        InstitutionalReportService.Options options = json.readValue(content, InstitutionalReportService.Options.class);

        assertThat(options.types()).hasSize(10);
        List<String> ids = options.types().stream().map(InstitutionalReportService.Type::id).toList();
        assertThat(ids).containsExactly(
                "OVERALL",
                "STIPEND",
                "COMPANY",
                "COMPANY_STIPEND",
                "COMPARISON",
                "PLACEMENT",
                "STATUS_ANALYSIS",
                "PARTICIPATION",
                "ACTIVE_ATTENDANCE",
                "MASTER_ANALYTICAL"
        );
    }

    @Test
    void task1OverallInternshipSummaryReport() throws Exception {
        requestTable(admin, "OVERALL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Overall Internship Summary Report"))
                .andExpect(jsonPath("$.rows[0].category").value("Department Internship"))
                .andExpect(jsonPath("$.rows[0].total").value(1))
                .andExpect(jsonPath("$.rows[1].category").value("CDC Internship"))
                .andExpect(jsonPath("$.rows[1].total").value(1))
                .andExpect(jsonPath("$.rows[2].category").value("Self Internship"))
                .andExpect(jsonPath("$.rows[2].total").value(1))
                .andExpect(jsonPath("$.rows[3].category").value("Total"))
                .andExpect(jsonPath("$.rows[3].total").value(3))
                .andExpect(jsonPath("$.rows[3].percentage").value(100.0));
    }

    @Test
    void task2StipendAnalysisReport() throws Exception {
        requestTable(admin, "STIPEND")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Stipend Analysis Report"))
                .andExpect(jsonPath("$.subTables[0].title").value("Table A: Category-wise Stipend Distribution"))
                .andExpect(jsonPath("$.subTables[0].rows[0].category").value("Department"))
                .andExpect(jsonPath("$.subTables[0].rows[0].paid").value(1))
                .andExpect(jsonPath("$.subTables[0].rows[0].unpaid").value(0))
                .andExpect(jsonPath("$.subTables[0].rows[1].category").value("CDC"))
                .andExpect(jsonPath("$.subTables[0].rows[1].paid").value(1))
                .andExpect(jsonPath("$.subTables[0].rows[2].category").value("Self"))
                .andExpect(jsonPath("$.subTables[0].rows[2].paid").value(0))
                .andExpect(jsonPath("$.subTables[0].rows[2].unpaid").value(1))
                .andExpect(jsonPath("$.subTables[1].title").value("Table B: Stipend Range Distribution"))
                .andExpect(jsonPath("$.subTables[1].rows[0].range").value("Unpaid"))
                .andExpect(jsonPath("$.subTables[1].rows[0].total").value(1))
                .andExpect(jsonPath("$.subTables[1].rows[1].range").value("₹1–₹5,000"))
                .andExpect(jsonPath("$.subTables[1].rows[1].total").value(1))
                .andExpect(jsonPath("$.subTables[1].rows[3].range").value("₹10,001–₹20,000"))
                .andExpect(jsonPath("$.subTables[1].rows[3].total").value(1));
    }

    @Test
    void task3CompanyWiseStudentDistribution() throws Exception {
        requestTable(admin, "COMPANY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Company-wise Student Distribution"))
                .andExpect(jsonPath("$.rows[0].company").value("Google"))
                .andExpect(jsonPath("$.rows[0].department").value(1))
                .andExpect(jsonPath("$.rows[0].self").value(1))
                .andExpect(jsonPath("$.rows[0].total").value(2))
                .andExpect(jsonPath("$.rows[1].company").value("Microsoft"))
                .andExpect(jsonPath("$.rows[1].cdc").value(1))
                .andExpect(jsonPath("$.rows[1].total").value(1))
                .andExpect(jsonPath("$.rows[2].company").value("Total"))
                .andExpect(jsonPath("$.rows[2].total").value(3));
    }

    @Test
    void task4CompanyPlusStipendAnalysis() throws Exception {
        requestTable(admin, "COMPANY_STIPEND")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Company + Stipend Analysis"))
                .andExpect(jsonPath("$.rows[0].company").value("Google"))
                .andExpect(jsonPath("$.rows[0].total").value(2))
                .andExpect(jsonPath("$.rows[0].paid").value(1))
                .andExpect(jsonPath("$.rows[0].unpaid").value(1))
                .andExpect(jsonPath("$.rows[1].company").value("Microsoft"))
                .andExpect(jsonPath("$.rows[1].paid").value(1))
                .andExpect(jsonPath("$.rows[1].offers").value(1))
                .andExpect(jsonPath("$.rows[2].company").value("Total"))
                .andExpect(jsonPath("$.rows[2].total").value(3))
                .andExpect(jsonPath("$.rows[2].offers").value(1));
    }

    @Test
    void task5DepartmentCdcSelfComparison() throws Exception {
        requestTable(admin, "COMPARISON")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Department / CDC / Self Comparison"))
                .andExpect(jsonPath("$.rows.length()").value(6))
                .andExpect(jsonPath("$.rows[0].parameter").value("Total Students"))
                .andExpect(jsonPath("$.rows[0].department").value(1))
                .andExpect(jsonPath("$.rows[0].cdc").value(1))
                .andExpect(jsonPath("$.rows[0].self").value(1))
                .andExpect(jsonPath("$.rows[0].total").value(3))
                .andExpect(jsonPath("$.rows[1].parameter").value("Stipend Students"))
                .andExpect(jsonPath("$.rows[1].total").value(2))
                .andExpect(jsonPath("$.rows[2].parameter").value("Non-Stipend"))
                .andExpect(jsonPath("$.rows[2].total").value(1))
                .andExpect(jsonPath("$.rows[3].parameter").value("Placement Offers"))
                .andExpect(jsonPath("$.rows[3].cdc").value(1))
                .andExpect(jsonPath("$.rows[4].parameter").value("Internship Completed"))
                .andExpect(jsonPath("$.rows[4].cdc").value(1))
                .andExpect(jsonPath("$.rows[5].parameter").value("Internship Ongoing"))
                .andExpect(jsonPath("$.rows[5].department").value(1));
    }

    @Test
    void task6PlacementOfferAnalysis() throws Exception {
        requestTable(admin, "PLACEMENT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Placement Offer Analysis"))
                .andExpect(jsonPath("$.subTables[0].title").value("Table A: Category-wise Placement Offers"))
                .andExpect(jsonPath("$.subTables[0].rows[1].category").value("CDC"))
                .andExpect(jsonPath("$.subTables[0].rows[1].offers").value(1))
                .andExpect(jsonPath("$.subTables[0].rows[1].noOffers").value(0))
                .andExpect(jsonPath("$.subTables[1].title").value("Table B: Company-wise Placement Offers"))
                .andExpect(jsonPath("$.subTables[1].rows[0].company").value("Microsoft"))
                .andExpect(jsonPath("$.subTables[1].rows[0].offers").value(1));
    }

    @Test
    void task7InternshipStatusAnalysis() throws Exception {
        requestTable(admin, "STATUS_ANALYSIS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Internship Status Analysis"))
                .andExpect(jsonPath("$.rows[0].category").value("Department"))
                .andExpect(jsonPath("$.rows[0].ongoing").value(1))
                .andExpect(jsonPath("$.rows[1].category").value("CDC"))
                .andExpect(jsonPath("$.rows[1].completed").value(1))
                .andExpect(jsonPath("$.rows[2].category").value("Self"))
                .andExpect(jsonPath("$.rows[2].notStarted").value(1));
    }

    @Test
    void task8ParticipationReport() throws Exception {
        requestTable(admin, "PARTICIPATION")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Student Working / Internship Participation Report"))
                .andExpect(jsonPath("$.rows[0].category").value("Department"))
                .andExpect(jsonPath("$.rows[0].joined").value(1))
                .andExpect(jsonPath("$.rows[1].category").value("CDC"))
                .andExpect(jsonPath("$.rows[1].completed").value(1))
                .andExpect(jsonPath("$.rows[2].category").value("Self"))
                .andExpect(jsonPath("$.rows[2].notJoined").value(1));
    }

    @Test
    void task9ActiveAttendanceReport() throws Exception {
        requestTable(admin, "ACTIVE_ATTENDANCE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Attendance / Active Internship Report"))
                .andExpect(jsonPath("$.subTables[0].title").value("Table A: Active Internship Attendance Metrics"))
                .andExpect(jsonPath("$.subTables[0].rows[0].metric").value("Students currently on internship"))
                .andExpect(jsonPath("$.subTables[0].rows[0].count").value(1))
                .andExpect(jsonPath("$.subTables[0].rows[4].metric").value("Students who completed internships"))
                .andExpect(jsonPath("$.subTables[0].rows[4].count").value(1))
                .andExpect(jsonPath("$.subTables[1].title").value("Table B: Active Intern Student List"))
                .andExpect(jsonPath("$.subTables[1].rows[0].uid").value("00101"));
    }

    @Test
    void task10MasterAnalyticalDashboard() throws Exception {
        requestTable(admin, "MASTER_ANALYTICAL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Master Analytical Dashboard Report"))
                .andExpect(jsonPath("$.summary.totalStudents").value(3))
                .andExpect(jsonPath("$.summary.department").value(1))
                .andExpect(jsonPath("$.summary.cdc").value(1))
                .andExpect(jsonPath("$.summary.self").value(1))
                .andExpect(jsonPath("$.subTables").isArray())
                .andExpect(jsonPath("$.subTables.length()").value(10));
    }

    @Test
    void exportsGenerateValidFilesForAllTenReports() throws Exception {
        for (String id : List.of("OVERALL", "STIPEND", "COMPANY", "COMPANY_STIPEND", "COMPARISON", "PLACEMENT", "STATUS_ANALYSIS", "PARTICIPATION", "ACTIVE_ATTENDANCE", "MASTER_ANALYTICAL")) {
            // Excel Export
            var xlsxRes = requestExport(admin, id, "XLSX").andExpect(status().isOk()).andReturn();
            assertThat(xlsxRes.getResponse().getHeader("Content-Disposition")).contains(".xlsx");
            byte[] xlsxBytes = xlsxRes.getResponse().getContentAsByteArray();
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsxBytes))) {
                Sheet sheet = workbook.getSheet("Students");
                assertThat(sheet).isNotNull();
                assertThat(sheet.getLastRowNum()).isGreaterThan(0);
            }

            // CSV Export
            var csvRes = requestExport(admin, id, "CSV").andExpect(status().isOk()).andReturn();
            assertThat(csvRes.getResponse().getHeader("Content-Disposition")).contains(".csv");
            String csvStr = csvRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(csvStr).isNotEmpty();

            // PDF Export
            var pdfRes = requestExport(admin, id, "PDF").andExpect(status().isOk()).andReturn();
            assertThat(pdfRes.getResponse().getHeader("Content-Disposition")).contains(".pdf");
            byte[] pdfBytes = pdfRes.getResponse().getContentAsByteArray();
            try (PDDocument doc = PDDocument.load(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(doc);
                assertThat(text).contains("Industry-Institute Interaction (III) Cell");
            }
        }
    }

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary
        Clock reportClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Kolkata"));
        }
    }
}
