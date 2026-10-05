package edu.institution.ims.internship;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class InternshipDetailsIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired StudentAccountRepository accounts;
    @Autowired StudentProfileRepository profiles; @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDocumentRepository documents; @Autowired InternshipDetailsRepository details; @Autowired PasswordEncoder encoder;
    User admin; User studentA; User studentB; Cookie adminCookie; Cookie studentACookie; Cookie studentBCookie;

    @BeforeEach void setUp() throws Exception {
        details.deleteAll(); documents.deleteAll(); onboardings.deleteAll(); profiles.deleteAll(); accounts.deleteAll(); users.deleteAll();
        admin = users.save(new User("incharge@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        studentA = student("student.a@example.edu", "StudentAPassword!23"); studentB = student("student.b@example.edu", "StudentBPassword!23");
        adminCookie = login(admin.getEmail(), "AdminPassword!23"); studentACookie = login(studentA.getEmail(), "StudentAPassword!23"); studentBCookie = login(studentB.getEmail(), "StudentBPassword!23");
    }

    @Test void studentWithIssuedDiaryCreatesInternshipDetailsAndAdvancesWorkflow() throws Exception {
        ready(studentACookie, "A", "UID-A", "a@college.edu");
        mvc.perform(post("/api/v1/student/internship").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(internship("Acme Systems"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.companyName").value("Acme Systems"))
                .andExpect(jsonPath("$.internshipModeLabel").value("Hybrid")).andExpect(jsonPath("$.stipendPerMonth").value(15000.00));
        mvc.perform(get("/api/v1/student/internship-onboarding").cookie(studentACookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("INTERNSHIP_DETAILS_COMPLETE"));
    }

    @Test void studentBeforeDiaryIssuedCannotCreateDetails() throws Exception {
        completeProfile(studentACookie, "A", "UID-A", "a@college.edu");
        mvc.perform(post("/api/v1/student/internship").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(internship("Acme Systems"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ONBOARDING_NOT_STARTED"));
    }

    @Test void studentRetrievesTheirInternship() throws Exception {
        readyAndCreate(studentACookie, "A", "UID-A", "a@college.edu", "Acme Systems");
        mvc.perform(get("/api/v1/student/internship").cookie(studentACookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Acme Systems")).andExpect(jsonPath("$.fullTimeEmploymentOffered").value(true));
    }

    @Test void studentUpdatesTheirInternship() throws Exception {
        readyAndCreate(studentACookie, "A", "UID-A", "a@college.edu", "Acme Systems"); Map<String,Object> update = internship("Acme Systems Updated"); update.put("stipendPerMonth", 0);
        mvc.perform(put("/api/v1/student/internship").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Acme Systems Updated")).andExpect(jsonPath("$.stipendPerMonth").value(0));
    }

    @Test void duplicateInternshipDetailsCannotBeCreated() throws Exception {
        readyAndCreate(studentACookie, "A", "UID-A", "a@college.edu", "Acme Systems");
        mvc.perform(post("/api/v1/student/internship").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(internship("Duplicate"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INTERNSHIP_DETAILS_ALREADY_EXIST"));
        assertThat(details.count()).isEqualTo(1);
    }

    @Test void otherCompanyTypeRequiresDescription() throws Exception {
        ready(studentACookie, "A", "UID-A", "a@college.edu"); Map<String,Object> data = internship("Acme Systems"); data.put("companyType", "OTHER"); data.put("otherCompanyType", "  ");
        mvc.perform(post("/api/v1/student/internship").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTHER_COMPANY_TYPE_REQUIRED"));
    }

    @Test void endDateMustBeAfterStartDate() throws Exception {
        ready(studentACookie, "A", "UID-A", "a@college.edu"); Map<String,Object> data = internship("Acme Systems"); data.put("endDate", "2027-01-01");
        mvc.perform(post("/api/v1/student/internship").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @ParameterizedTest @ValueSource(ints = {0, -1})
    void zeroOrNegativeDurationIsRejected(int duration) throws Exception {
        ready(studentACookie, "A", "UID-A", "a@college.edu"); Map<String,Object> data = internship("Acme Systems"); data.put("durationMonths", duration); data.put("totalWeeks", duration);
        mvc.perform(post("/api/v1/student/internship").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test void negativeStipendIsRejected() throws Exception {
        ready(studentACookie, "A", "UID-A", "a@college.edu"); Map<String,Object> data = internship("Acme Systems"); data.put("stipendPerMonth", -1);
        mvc.perform(post("/api/v1/student/internship").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.stipendPerMonth").value("Stipend cannot be negative"));
    }

    @Test void invalidInternshipModeIsRejected() throws Exception {
        ready(studentACookie, "A", "UID-A", "a@college.edu"); Map<String,Object> data = internship("Acme Systems"); data.put("internshipMode", "FIELD");
        mvc.perform(post("/api/v1/student/internship").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void suppliedStudentIdCannotChangeSelfScopedInternship() throws Exception {
        readyAndCreate(studentACookie, "A", "UID-A", "a@college.edu", "Student A Company");
        readyAndCreate(studentBCookie, "B", "UID-B", "b@college.edu", "Student B Company");
        mvc.perform(get("/api/v1/student/internship").param("studentId", studentB.getId().toString()).cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Student A Company"));
    }

    @Test void adminCanViewStudentInternshipAndStudentCannotUseAdminEndpoint() throws Exception {
        readyAndCreate(studentACookie, "A", "UID-A", "a@college.edu", "Acme Systems");
        mvc.perform(get("/api/v1/admin/students/{id}/internship", studentA.getId()).cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Acme Systems"));
        mvc.perform(get("/api/v1/admin/students/{id}/internship", studentA.getId()).cookie(studentACookie)).andExpect(status().isForbidden());
    }

    @Test void adminStudentDetailIncludesFullLifecycle() throws Exception {
        readyAndCreate(studentACookie, "A", "UID-A", "a@college.edu", "Acme Systems");
        mvc.perform(get("/api/v1/admin/students/{id}", studentA.getId()).cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.uid").value("UID-A")).andExpect(jsonPath("$.internshipOnboarding.diaryIssued").value(true))
                .andExpect(jsonPath("$.internshipOnboarding.workflowStatus").value("INTERNSHIP_DETAILS_COMPLETE"))
                .andExpect(jsonPath("$.internshipDetails.companyName").value("Acme Systems"));
    }

    @Test void laterDiaryConfirmationAndDocumentReplacementDoNotMoveWorkflowBackwards() throws Exception {
        readyAndCreate(studentACookie, "A", "UID-A", "a@college.edu", "Acme Systems");
        mvc.perform(post("/api/v1/student/internship-onboarding/diary/confirm").cookie(studentACookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("INTERNSHIP_DETAILS_COMPLETE"));
        mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/OFFER_LETTER").file(pdf("replacement.pdf")).cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workflowStatus").value("INTERNSHIP_DETAILS_COMPLETE"));
    }

    @Test void unauthenticatedUserCannotAccessInternshipData() throws Exception {
        mvc.perform(get("/api/v1/student/internship")).andExpect(status().isUnauthorized());
    }

    private User student(String email, String password) { User user = users.save(new User(email, encoder.encode(password), Role.STUDENT, true)); accounts.save(new StudentAccount(user)); return user; }
    private Cookie login(String email, String password) throws Exception { return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(email, password)))).andReturn().getResponse().getCookie("ims_auth"); }
    private void readyAndCreate(Cookie cookie, String suffix, String uid, String email, String company) throws Exception { ready(cookie, suffix, uid, email); mvc.perform(post("/api/v1/student/internship").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(internship(company)))).andExpect(status().isCreated()); }
    private void ready(Cookie cookie, String suffix, String uid, String email) throws Exception {
        completeProfile(cookie, suffix, uid, email); mvc.perform(post("/api/v1/student/internship-onboarding/secure").cookie(cookie)).andExpect(status().isCreated());
        mvc.perform(put("/api/v1/student/internship-onboarding/source").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"SELF\"}")).andExpect(status().isOk());
        for (DocumentType type : DocumentType.values()) mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/{type}", type).file(pdf(type.name() + ".pdf")).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/student/internship-onboarding/diary/confirm").cookie(cookie)).andExpect(status().isOk());
    }
    private MockMultipartFile pdf(String name) { return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4\n%%EOF".getBytes()); }
    private void completeProfile(Cookie cookie, String suffix, String uid, String instituteEmail) throws Exception { mvc.perform(post("/api/v1/student/profile").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile(suffix, uid, instituteEmail)))).andExpect(status().isCreated()); }
    private Map<String,Object> internship(String company) { Map<String,Object> data = new LinkedHashMap<>(); data.put("companyName", company); data.put("companyType", "IT"); data.put("otherCompanyType", "ignored"); data.put("companyAddress", "100 Industry Road, Chandigarh"); data.put("companyPhone", "+91 172 555 0100"); data.put("industrySupervisorName", "Industry Supervisor"); data.put("industrySupervisorDesignation", "Engineering Manager"); data.put("industrySupervisorContact", "+91 98765 40100"); data.put("durationMonths", 6); data.put("startDate", "2027-01-01"); data.put("endDate", "2027-06-30"); data.put("totalWeeks", 26); data.put("internshipMode", "HYBRID"); data.put("stipendPerMonth", 15000.00); data.put("fullTimeEmploymentOffered", true); return data; }
    private Map<String,Object> profile(String suffix, String uid, String instituteEmail) {
        Map<String,Object> p = new LinkedHashMap<>(); p.put("studentName", "Student " + suffix); p.put("branch", "COMPUTER_ENGINEERING"); p.put("uid", uid); p.put("rollNumber", "ROLL-" + suffix); p.put("permanentAddress", "Permanent address " + suffix); p.put("currentAddress", ""); p.put("personalEmail", "personal" + suffix.toLowerCase() + "@example.com"); p.put("instituteEmail", instituteEmail); p.put("phoneNumber", "+91 98765 43210"); p.put("guardianName", "Guardian " + suffix); p.put("guardianContact", "+91 98765 43211"); p.put("emergencyContact", "+91 98765 43212"); p.put("bloodGroup", "O+"); p.put("aadhaarNumber", "123456789012"); p.put("facultyMentorName", "Faculty Mentor"); p.put("facultyMentorContact", "+91 98765 43213"); p.put("internshipCoordinatorName", "Internship Coordinator"); p.put("internshipCoordinatorContact", "+91 98765 43214"); p.put("cdcCoordinatorName", "CDC Coordinator"); p.put("cdcCoordinatorContact", "+91 98765 43215"); p.put("academicYear", "2026-2027"); p.put("semester", 7); return p;
    }
}
