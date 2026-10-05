package edu.institution.ims.studentprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @ExtendWith(OutputCaptureExtension.class)
class StudentProfileIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired StudentAccountRepository accounts;
    @Autowired StudentProfileRepository profiles; @Autowired PasswordEncoder encoder;
    User admin; User studentA; User studentB; Cookie adminCookie; Cookie studentACookie; Cookie studentBCookie;

    @BeforeEach void setUp() throws Exception {
        profiles.deleteAll(); accounts.deleteAll(); users.deleteAll();
        admin = users.save(new User("incharge@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        studentA = student("student.a@example.edu", "StudentAPassword!23"); studentB = student("student.b@example.edu", "StudentBPassword!23");
        adminCookie = login(admin.getEmail(), "AdminPassword!23"); studentACookie = login(studentA.getEmail(), "StudentAPassword!23"); studentBCookie = login(studentB.getEmail(), "StudentBPassword!23");
    }

    @Test void studentCreatesAndReloadsOwnProfile() throws Exception {
        mvc.perform(post("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile("A", "UID-A", "a@college.edu"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.studentName").value("Student A")).andExpect(jsonPath("$.maskedAadhaar").value("XXXX XXXX 9012"))
                .andExpect(jsonPath("$.aadhaarNumber").doesNotExist());
        mvc.perform(get("/api/v1/student/profile").cookie(studentACookie)).andExpect(status().isOk()).andExpect(jsonPath("$.uid").value("UID-A"));
        assertThat(profiles.findByUserId(studentA.getId()).orElseThrow().getAadhaarEncrypted()).doesNotContain("123456789012");
    }

    @Test void studentUpdatesOwnProfileWithoutResendingAadhaar() throws Exception {
        create(studentACookie, profile("A", "UID-A", "a@college.edu")); Map<String,Object> update = profile("A", "UID-A", "a@college.edu"); update.put("studentName", "Student A Updated"); update.put("aadhaarNumber", "");
        mvc.perform(put("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentName").value("Student A Updated")).andExpect(jsonPath("$.maskedAadhaar").value("XXXX XXXX 9012"));
    }

    @Test void repeatedCreateCannotCreateDuplicateProfile() throws Exception {
        create(studentACookie, profile("A", "UID-A", "a@college.edu"));
        mvc.perform(post("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile("A", "UID-NEW", "new@college.edu"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFILE_ALREADY_EXISTS"));
        assertThat(profiles.count()).isEqualTo(1);
    }

    @Test void studentIdUrlCannotRetrieveAnotherProfile() throws Exception {
        create(studentBCookie, profile("B", "UID-B", "b@college.edu"));
        mvc.perform(get("/api/v1/student/profile/" + studentB.getId()).cookie(studentACookie)).andExpect(status().isNotFound());
    }

    @Test void adminCanViewStudentListAndSearch() throws Exception {
        create(studentACookie, profile("A", "UID-A", "a@college.edu"));
        mvc.perform(get("/api/v1/admin/students").param("search", "UID-A").cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(studentA.getId())).andExpect(jsonPath("$.content[0].profileStatus").value("COMPLETE"));
    }

    @Test void adminCanViewStudentDetails() throws Exception {
        create(studentACookie, profile("A", "UID-A", "a@college.edu"));
        mvc.perform(get("/api/v1/admin/students/" + studentA.getId()).cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.uid").value("UID-A")).andExpect(jsonPath("$.profile.maskedAadhaar").value("XXXX XXXX 9012"))
                .andExpect(jsonPath("$.aadhaarNumber").value("123456789012"));
    }

    @Test void importedInstitutionalIdentifiersAndBranchCannotBeChangedByStudent() throws Exception {
        User imported = users.save(new User("imported@college.edu", encoder.encode("StudentPassword!23"), Role.STUDENT, true));
        accounts.save(new StudentAccount(imported, "Imported Student", "23001107", 7));
        Cookie importedCookie = login(imported.getEmail(), "StudentPassword!23");
        mvc.perform(get("/api/v1/student/profile/prefill").cookie(importedCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("23001107")).andExpect(jsonPath("$.instituteEmail").value("imported@college.edu"))
                .andExpect(jsonPath("$.branch").value("COMPUTER_ENGINEERING"));

        Map<String,Object> submitted = profile("Imported", "changed-uid", "changed@college.edu"); submitted.put("branch", "ELECTRICAL");
        mvc.perform(post("/api/v1/student/profile").cookie(importedCookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(submitted)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.uid").value("23001107"))
                .andExpect(jsonPath("$.instituteEmail").value("imported@college.edu")).andExpect(jsonPath("$.branch").value("COMPUTER_ENGINEERING"));

        submitted.put("uid", "another-uid"); submitted.put("instituteEmail", "another@college.edu"); submitted.put("branch", "MECHANICAL"); submitted.put("aadhaarNumber", "");
        mvc.perform(put("/api/v1/student/profile").cookie(importedCookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(submitted)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uid").value("23001107"))
                .andExpect(jsonPath("$.instituteEmail").value("imported@college.edu")).andExpect(jsonPath("$.branch").value("COMPUTER_ENGINEERING"));

        String studentBody = mvc.perform(get("/api/v1/student/profile").cookie(importedCookie)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(studentBody).doesNotContain("123456789012");
        assertThat(profiles.findByUserId(imported.getId()).orElseThrow().getAadhaarEncrypted()).doesNotContain("123456789012");
        mvc.perform(get("/api/v1/admin/students/" + imported.getId()).cookie(adminCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.aadhaarNumber").value("123456789012"));
        User mentor = users.save(new User("mentor@example.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        mvc.perform(get("/api/v1/admin/students/" + imported.getId()).cookie(login(mentor.getEmail(), "MentorPassword!23"))).andExpect(status().isForbidden());
    }

    @Test void studentCannotAccessAdminStudentEndpoint() throws Exception {
        mvc.perform(get("/api/v1/admin/students").cookie(studentACookie)).andExpect(status().isForbidden());
    }

    @Test void duplicateUidIsRejected() throws Exception {
        create(studentACookie, profile("A", "SAME-UID", "a@college.edu"));
        mvc.perform(post("/api/v1/student/profile").cookie(studentBCookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile("B", "SAME-UID", "b@college.edu"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_UID"));
    }

    @Test void invalidEmailIsRejected() throws Exception {
        Map<String,Object> data = profile("A", "UID-A", "invalid-email");
        mvc.perform(post("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.instituteEmail").value("Institute email format is invalid"));
    }

    @Test void invalidPhoneIsRejected() throws Exception {
        Map<String,Object> data = profile("A", "UID-A", "a@college.edu"); data.put("phoneNumber", "123");
        mvc.perform(post("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.phoneNumber").value("Phone number format is invalid"));
    }

    @Test void summaryDoesNotExposeSensitiveFields() throws Exception {
        create(studentACookie, profile("A", "UID-A", "a@college.edu"));
        String body = mvc.perform(get("/api/v1/admin/students").cookie(adminCookie)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("aadhaar").doesNotContainIgnoringCase("bloodGroup").doesNotContainIgnoringCase("permanentAddress").doesNotContainIgnoringCase("guardian");
    }

    @Test void aadhaarIsNotLogged(CapturedOutput output) throws Exception {
        create(studentACookie, profile("A", "UID-A", "a@college.edu")); assertThat(output.getAll()).doesNotContain("123456789012");
    }

    private User student(String email, String password) { User user = users.save(new User(email, encoder.encode(password), Role.STUDENT, true)); accounts.save(new StudentAccount(user)); return user; }
    private Cookie login(String email, String password) throws Exception { return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(email, password)))).andReturn().getResponse().getCookie("ims_auth"); }
    private void create(Cookie cookie, Map<String,Object> data) throws Exception { mvc.perform(post("/api/v1/student/profile").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andExpect(status().isCreated()); }
    private Map<String,Object> profile(String suffix, String uid, String instituteEmail) {
        Map<String,Object> p = new LinkedHashMap<>(); p.put("studentName", "Student " + suffix); p.put("branch", "COMPUTER_ENGINEERING"); p.put("uid", uid); p.put("rollNumber", "ROLL-" + suffix);
        p.put("permanentAddress", "Permanent address " + suffix); p.put("currentAddress", ""); p.put("personalEmail", "personal" + suffix.toLowerCase() + "@example.com"); p.put("instituteEmail", instituteEmail);
        p.put("phoneNumber", "+91 98765 43210"); p.put("guardianName", "Guardian " + suffix); p.put("guardianContact", "+91 98765 43211"); p.put("emergencyContact", "+91 98765 43212");
        p.put("bloodGroup", "O+"); p.put("aadhaarNumber", "123456789012"); p.put("facultyMentorName", "Faculty Mentor"); p.put("facultyMentorContact", "+91 98765 43213");
        p.put("internshipCoordinatorName", "Internship Coordinator"); p.put("internshipCoordinatorContact", "+91 98765 43214"); p.put("cdcCoordinatorName", "CDC Coordinator"); p.put("cdcCoordinatorContact", "+91 98765 43215");
        p.put("academicYear", "2026-2027"); p.put("semester", 7); return p;
    }
}
