package edu.institution.ims.dashboard.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.dashboard.report.custom.*;
import edu.institution.ims.security.JwtService;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:custom_report_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomReportBuilderIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired edu.institution.ims.security.JwtService jwt;
    @Autowired SavedReportConfigurationRepository savedRepo;

    private Cookie adminCookie;
    private Cookie studentCookie;

    @BeforeEach
    void setUp() {
        String passwordHash = passwords.encode("AdminPass123!");
        User admin = users.findByEmailIgnoreCase("admin_report_test@pallotti.edu").orElseGet(() ->
                users.saveAndFlush(new User("admin_report_test@pallotti.edu", passwordHash, Role.ADMIN, true))
        );

        User student = users.findByEmailIgnoreCase("student_report_test@pallotti.edu").orElseGet(() ->
                users.saveAndFlush(new User("student_report_test@pallotti.edu", passwordHash, Role.STUDENT, true))
        );

        adminCookie = new Cookie("ims_auth", jwt.create(edu.institution.ims.security.UserPrincipal.from(admin)));
        studentCookie = new Cookie("ims_auth", jwt.create(edu.institution.ims.security.UserPrincipal.from(student)));
    }

    @Test
    @DisplayName("Catalog endpoint exposes categorized fields and valid operators")
    void catalogEndpointReturnsFields() throws Exception {
        MvcResult res = mvc.perform(get("/api/v1/admin/reports/custom/catalog").cookie(adminCookie))
                .andExpect(status().isOk())
                .andReturn();

        List<CustomReportCatalog.Field> fields = json.readValue(
                res.getResponse().getContentAsString(),
                json.getTypeFactory().constructCollectionType(List.class, CustomReportCatalog.Field.class)
        );

        assertThat(fields).isNotEmpty();
        assertThat(fields).anyMatch(f -> f.id().equals("studentName") && f.category().equals("Student Information"));
        assertThat(fields).anyMatch(f -> f.id().equals("annualCtc") && f.category().equals("Financial Information"));
        assertThat(fields).anyMatch(f -> f.id().equals("attendancePercentage") && f.category().equals("Attendance Information"));
    }

    @Test
    @DisplayName("Reject unauthorized report requests from non-admin users")
    void rejectNonAdminUsers() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/custom/catalog").cookie(studentCookie))
                .andExpect(status().isForbidden());

        CustomReportRequest req = new CustomReportRequest(
                "Unauthorized Test", "INTERNSHIP", null, List.of("studentName"), null, "companyName", "studentName", "ASC", "PREVIEW", 0, 10, null
        );
        mvc.perform(post("/api/v1/admin/reports/custom/preview").cookie(studentCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Reject invalid / unknown column identifier to prevent SQL injection")
    void rejectInvalidColumnIdentifier() throws Exception {
        CustomReportRequest req = new CustomReportRequest(
                "SQL Injection Test", "INTERNSHIP", null,
                List.of("studentName", "invalid_col; drop table users; --"),
                null, "companyName", "studentName", "ASC", "PREVIEW", 0, 10, null
        );

        mvc.perform(post("/api/v1/admin/reports/custom/preview").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Reject missing report title")
    void rejectMissingTitle() throws Exception {
        CustomReportRequest req = new CustomReportRequest(
                "", "INTERNSHIP", null, List.of("studentName"), null, "companyName", "studentName", "ASC", "PREVIEW", 0, 10, null
        );

        mvc.perform(post("/api/v1/admin/reports/custom/preview").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Preview report includes institutional header and handles empty dataset")
    void previewEmptyDatasetWithInstitutionalHeader() throws Exception {
        CustomReportRequest req = new CustomReportRequest(
                "Test Department Report", "INTERNSHIP", null,
                List.of("studentName", "uid", "companyName", "annualCtc"),
                null, "companyName", "studentName", "ASC", "PREVIEW", 0, 50, null
        );

        MvcResult result = mvc.perform(post("/api/v1/admin/reports/custom/preview").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        CustomReportResponse resp = json.readValue(result.getResponse().getContentAsString(), CustomReportResponse.class);
        assertThat(resp.institutionName()).isEqualTo("St. Vincent Pallotti College of Engineering and Technology");
        assertThat(resp.departmentName()).isEqualTo("Department of Computer Engineering");
        assertThat(resp.headerTitle()).isEqualTo("INTERNSHIP MANAGEMENT REPORT");
        assertThat(resp.reportTitle()).isEqualTo("Test Department Report");
        assertThat(resp.columns()).hasSize(4);
    }

    @Test
    @DisplayName("Export CSV, Excel and PDF formats")
    void exportEndpointsReturnBinaryStreams() throws Exception {
        CustomReportRequest csvReq = new CustomReportRequest(
                "Export Test", "INTERNSHIP", null,
                List.of("studentName", "uid", "companyName"),
                null, "companyName", "studentName", "ASC", "CSV", 0, 50, null
        );

        mvc.perform(post("/api/v1/admin/reports/custom/export").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(csvReq)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Export_Test.csv\""));

        CustomReportRequest xlsxReq = new CustomReportRequest(
                "Export Test", "INTERNSHIP", null,
                List.of("studentName", "uid", "companyName"),
                null, "companyName", "studentName", "ASC", "EXCEL", 0, 50, null
        );

        mvc.perform(post("/api/v1/admin/reports/custom/export").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(xlsxReq)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("spreadsheetml")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Export_Test.xlsx\""));

        CustomReportRequest pdfReq = new CustomReportRequest(
                "Company Wise Internship Report", "INTERNSHIP", null,
                List.of("studentName", "uid", "companyName"),
                null, "companyName", "studentName", "ASC", "PDF", 0, 50, null
        );

        mvc.perform(post("/api/v1/admin/reports/custom/export").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(pdfReq)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/pdf")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Company_Wise_Internship_Report.pdf\""));
    }

    @Test
    @DisplayName("Filename sanitization handles special characters, spaces and deduplicates extensions")
    void filenameSanitizationPreservesExtensions() {
        assertThat(CustomReportService.sanitizeFilename("Internship Report", "pdf"))
                .isEqualTo("Internship_Report.pdf");
        assertThat(CustomReportService.sanitizeFilename("Company Wise Internship Report", "xlsx"))
                .isEqualTo("Company_Wise_Internship_Report.xlsx");
        assertThat(CustomReportService.sanitizeFilename("Internship_Report.pdf", "pdf"))
                .isEqualTo("Internship_Report.pdf");
        assertThat(CustomReportService.sanitizeFilename("Report: 2025/2026 (Final!)", "csv"))
                .isEqualTo("Report_2025_2026_Final.csv");
        assertThat(CustomReportService.sanitizeFilename("   ", "pdf"))
                .isEqualTo("Internship_Report.pdf");
    }

    @Test
    @DisplayName("Saved report configurations CRUD lifecycle")
    void savedReportConfigurationCrud() throws Exception {
        SaveReportRequest saveReq = new SaveReportRequest(
                "Saved CTC Report", "Students with CTC > 6 LPA", "INTERNSHIP",
                "{\"columns\":[\"studentName\",\"annualCtc\"],\"groupBy\":\"companyName\"}"
        );

        // 1. Create
        MvcResult createResult = mvc.perform(post("/api/v1/admin/reports/saved").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(saveReq)))
                .andExpect(status().isOk())
                .andReturn();

        SavedReportDto created = json.readValue(createResult.getResponse().getContentAsString(), SavedReportDto.class);
        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isEqualTo("Saved CTC Report");

        // 2. List
        MvcResult listResult = mvc.perform(get("/api/v1/admin/reports/saved").cookie(adminCookie))
                .andExpect(status().isOk())
                .andReturn();

        List<SavedReportDto> list = json.readValue(
                listResult.getResponse().getContentAsString(),
                json.getTypeFactory().constructCollectionType(List.class, SavedReportDto.class)
        );
        assertThat(list).anyMatch(r -> r.id().equals(created.id()));

        // 3. Get by ID
        mvc.perform(get("/api/v1/admin/reports/saved/" + created.id()).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Saved CTC Report"));

        // 4. Update
        SaveReportRequest updateReq = new SaveReportRequest(
                "Updated CTC Report", "Updated Description", "STUDENT",
                "{\"columns\":[\"studentName\"],\"groupBy\":\"NONE\"}"
        );
        mvc.perform(put("/api/v1/admin/reports/saved/" + created.id()).cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated CTC Report"))
                .andExpect(jsonPath("$.scope").value("STUDENT"));

        // 5. Delete
        mvc.perform(delete("/api/v1/admin/reports/saved/" + created.id()).cookie(adminCookie))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/admin/reports/saved/" + created.id()).cookie(adminCookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Nested filter groups and multiple company filtering")
    void nestedFiltersAndMultipleCompanies() throws Exception {
        CustomReportFilterRule rule1 = new CustomReportFilterRule("branch", "IS", "COMPUTER_ENGINEERING", null, null);
        CustomReportFilterRule rule2 = new CustomReportFilterRule("companyName", "IS_ANY_OF", null, null, List.of("Zoho", "Infosys", "TCS"));
        CustomReportFilterRule rule3 = new CustomReportFilterRule("annualCtc", "GREATER_THAN_OR_EQUAL", "6.0", null, null);

        CustomReportFilterGroup subGroup = new CustomReportFilterGroup("OR", List.of(rule2, rule3), null);
        CustomReportFilterGroup rootGroup = new CustomReportFilterGroup("AND", List.of(rule1), List.of(subGroup));

        CustomReportRequest req = new CustomReportRequest(
                "Complex Filter Report", "INTERNSHIP", null,
                List.of("studentName", "branch", "companyName", "annualCtc"),
                rootGroup, "companyName", "annualCtc", "DESC", "PREVIEW", 0, 50, null
        );

        MvcResult result = mvc.perform(post("/api/v1/admin/reports/custom/preview").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        CustomReportResponse resp = json.readValue(result.getResponse().getContentAsString(), CustomReportResponse.class);
        assertThat(resp.filterSummary()).contains("Branch is \"COMPUTER_ENGINEERING\"");
        assertThat(resp.filterSummary()).contains("Company Name in [Zoho, Infosys, TCS]");
        assertThat(resp.filterSummary()).contains("Annual CTC (LPA) >= 6.0");
    }

    @Test
    @DisplayName("Count endpoint returns totalRecords, totalStudents, and totalInternships")
    void countEndpointReturnsMatchingMetrics() throws Exception {
        CustomReportRequest req = new CustomReportRequest(
                "Count Test", "STUDENT", null,
                List.of("studentName", "uid"),
                null, "NONE", "studentName", "ASC", "PREVIEW", 0, 10, null
        );

        mvc.perform(post("/api/v1/admin/reports/custom/count").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("STUDENT"))
                .andExpect(jsonPath("$.totalRecords").isNumber())
                .andExpect(jsonPath("$.totalStudents").isNumber())
                .andExpect(jsonPath("$.totalInternships").isNumber());
    }
}
