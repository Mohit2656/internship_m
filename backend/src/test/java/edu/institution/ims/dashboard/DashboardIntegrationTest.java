package edu.institution.ims.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.*;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class DashboardIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired StudentAccountRepository accounts;
    @Autowired StudentProfileRepository profiles; @Autowired InternshipOnboardingRepository onboardings; @Autowired InternshipDocumentRepository documents;
    @Autowired InternshipDetailsRepository details; @Autowired PasswordEncoder encoder;
    Cookie adminCookie; Cookie studentCookie;

    @BeforeEach void setUp() throws Exception {
        details.deleteAll(); documents.deleteAll(); onboardings.deleteAll(); profiles.deleteAll(); accounts.deleteAll(); users.deleteAll();
        User admin = users.save(new User("incharge@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        adminCookie = login(admin.getEmail(), "AdminPassword!23");
    }

    @Test void dashboardAggregatesLifecycleAndInternshipOutcomesInTheDatabase() throws Exception {
        studentCookie = student("incomplete@example.edu", "Incomplete"); // A: profile incomplete
        Cookie profileOnly = student("profile@example.edu", "Profile"); completeProfile(profileOnly, "B"); // B: no onboarding
        Cookie documentsPending = student("pending@example.edu", "Pending"); completeProfile(documentsPending, "C"); secure(documentsPending, "SELF"); // C
        Cookie diaryPending = student("diary@example.edu", "Diary"); completeProfile(diaryPending, "D"); secure(diaryPending, "DEPARTMENT"); completeDocuments(diaryPending); // D
        Cookie detailsPending = student("details-pending@example.edu", "Details Pending"); completeProfile(detailsPending, "E"); secure(detailsPending, "CDC"); completeDocuments(detailsPending); confirmDiary(detailsPending); // E
        Cookie paid = student("paid@example.edu", "Paid"); completeProfile(paid, "F"); secure(paid, "SELF"); completeDocuments(paid); confirmDiary(paid); createDetails(paid, "  Acme Systems  ", "IT", "HYBRID", 15000, true); // F
        Cookie unpaid = student("unpaid@example.edu", "Unpaid"); completeProfile(unpaid, "G"); secure(unpaid, "SELF"); completeDocuments(unpaid); confirmDiary(unpaid); createDetails(unpaid, "acme systems", "OTHER", "ONLINE", 0, false); // G

        mvc.perform(get("/api/v1/admin/dashboard/summary").cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.students.total").value(7)).andExpect(jsonPath("$.students.profileComplete").value(6)).andExpect(jsonPath("$.students.profileIncomplete").value(1))
                .andExpect(jsonPath("$.internshipProgress.secured").value(5)).andExpect(jsonPath("$.internshipProgress.notSecured").value(2))
                .andExpect(jsonPath("$.internshipProgress.documentsPending").value(1)).andExpect(jsonPath("$.internshipProgress.documentsComplete").value(4))
                .andExpect(jsonPath("$.internshipProgress.diaryIssued").value(3)).andExpect(jsonPath("$.internshipProgress.detailsComplete").value(2))
                .andExpect(jsonPath("$.sources[0].count").value(1)).andExpect(jsonPath("$.sources[1].count").value(1)).andExpect(jsonPath("$.sources[2].count").value(3))
                .andExpect(jsonPath("$.companyTypes[0].count").value(1)).andExpect(jsonPath("$.companyTypes[3].count").value(1))
                .andExpect(jsonPath("$.internshipModes[1].count").value(1)).andExpect(jsonPath("$.internshipModes[2].count").value(1))
                .andExpect(jsonPath("$.stipend.paid").value(1)).andExpect(jsonPath("$.stipend.unpaid").value(1)).andExpect(jsonPath("$.stipend.averagePaidMonthlyStipend").value(15000))
                .andExpect(jsonPath("$.fullTimeEmployment.yes").value(1)).andExpect(jsonPath("$.fullTimeEmployment.no").value(1))
                .andExpect(jsonPath("$.topCompanies[0].students").value(2)).andExpect(jsonPath("$.attention.profileIncomplete").value(1))
                .andExpect(jsonPath("$.attention.internshipNotSecured").value(2)).andExpect(jsonPath("$.attention.documentsPending").value(1))
                .andExpect(jsonPath("$.attention.diaryPending").value(1)).andExpect(jsonPath("$.attention.internshipDetailsPending").value(1));

        mvc.perform(get("/api/v1/admin/students").param("attention", "DIARY_PENDING").cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].studentName").value("Student D"));
        mvc.perform(get("/api/v1/admin/students").param("companyType", "IT").param("paid", "true").cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].studentName").value("Student F"));
    }

    @Test void emptyDashboardUsesSafeZeroValues() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard/summary").cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.students.total").value(0)).andExpect(jsonPath("$.internshipProgress.secured").value(0))
                .andExpect(jsonPath("$.stipend.paid").value(0)).andExpect(jsonPath("$.stipend.unpaid").value(0))
                .andExpect(jsonPath("$.stipend.averagePaidMonthlyStipend").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.topCompanies").isEmpty());
    }

    @Test void studentCannotAccessInstitutionDashboard() throws Exception {
        studentCookie = student("student@example.edu", "Student");
        mvc.perform(get("/api/v1/admin/dashboard/summary").cookie(studentCookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard/summary")).andExpect(status().isUnauthorized());
    }

    private Cookie student(String email, String ignored) throws Exception { User user = users.save(new User(email, encoder.encode("StudentPassword!23"), Role.STUDENT, true)); accounts.save(new StudentAccount(user)); return login(email, "StudentPassword!23"); }
    private Cookie login(String email, String password) throws Exception { return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(email, password)))).andReturn().getResponse().getCookie("ims_auth"); }
    private void completeProfile(Cookie cookie, String suffix) throws Exception { mvc.perform(post("/api/v1/student/profile").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile(suffix)))).andExpect(status().isCreated()); }
    private void secure(Cookie cookie, String source) throws Exception { mvc.perform(post("/api/v1/student/internship-onboarding/secure").cookie(cookie)).andExpect(status().isCreated()); mvc.perform(put("/api/v1/student/internship-onboarding/source").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"" + source + "\"}")).andExpect(status().isOk()); }
    private void completeDocuments(Cookie cookie) throws Exception { for (DocumentType type : DocumentType.values()) mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/{type}", type).file(pdf(type.name() + ".pdf")).cookie(cookie)).andExpect(status().isOk()); }
    private void confirmDiary(Cookie cookie) throws Exception { mvc.perform(post("/api/v1/student/internship-onboarding/diary/confirm").cookie(cookie)).andExpect(status().isOk()); }
    private void createDetails(Cookie cookie, String company, String companyType, String mode, int stipend, boolean fullTime) throws Exception { Map<String,Object> body = details(company); body.put("companyType", companyType); body.put("internshipMode", mode); body.put("stipendPerMonth", stipend); body.put("fullTimeEmploymentOffered", fullTime); if ("OTHER".equals(companyType)) body.put("otherCompanyType", "Research"); mvc.perform(post("/api/v1/student/internship").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isCreated()); }
    private MockMultipartFile pdf(String name) { return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4\n%%EOF".getBytes()); }
    private Map<String,Object> details(String company) { Map<String,Object> body = new LinkedHashMap<>(); body.put("companyName", company); body.put("companyType", "IT"); body.put("otherCompanyType", "ignored"); body.put("companyAddress", "100 Industry Road, Chandigarh"); body.put("companyPhone", "+91 172 555 0100"); body.put("industrySupervisorName", "Industry Supervisor"); body.put("industrySupervisorDesignation", "Engineering Manager"); body.put("industrySupervisorContact", "+91 98765 40100"); body.put("durationMonths", 6); body.put("startDate", "2027-01-01"); body.put("endDate", "2027-06-30"); body.put("totalWeeks", 26); body.put("internshipMode", "HYBRID"); body.put("stipendPerMonth", 15000); body.put("fullTimeEmploymentOffered", true); return body; }
    private Map<String,Object> profile(String suffix) { Map<String,Object> p = new LinkedHashMap<>(); p.put("studentName", "Student " + suffix); p.put("branch", "COMPUTER_ENGINEERING"); p.put("uid", "UID-" + suffix); p.put("rollNumber", "ROLL-" + suffix); p.put("permanentAddress", "Permanent address"); p.put("currentAddress", ""); p.put("personalEmail", suffix.toLowerCase() + "@example.com"); p.put("instituteEmail", suffix.toLowerCase() + "@college.edu"); p.put("phoneNumber", "+91 98765 43210"); p.put("guardianName", "Guardian"); p.put("guardianContact", "+91 98765 43211"); p.put("emergencyContact", "+91 98765 43212"); p.put("bloodGroup", "O+"); p.put("aadhaarNumber", "123456789012"); p.put("facultyMentorName", "Faculty Mentor"); p.put("facultyMentorContact", "+91 98765 43213"); p.put("internshipCoordinatorName", "Internship Coordinator"); p.put("internshipCoordinatorContact", "+91 98765 43214"); p.put("cdcCoordinatorName", "CDC Coordinator"); p.put("cdcCoordinatorContact", "+91 98765 43215"); p.put("academicYear", "2026-2027"); p.put("semester", 7); return p; }
}
