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
    @Autowired edu.institution.ims.mentor.MentorStudentAssignmentRepository assignments;
    User admin; User studentA; User studentB; Cookie adminCookie; Cookie studentACookie; Cookie studentBCookie;

    @BeforeEach void setUp() throws Exception {
        assignments.deleteAll();
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
        create(studentACookie, profile("A", "UID-A", "a@college.edu")); Map<String,Object> update = profile("A", "UID-A", "a@college.edu"); update.put("studentName", "Student A Updated"); update.put("aadhaarNumber", ""); update.put("currentCgpa", 8.75);
        mvc.perform(put("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentName").value("Student A Updated")).andExpect(jsonPath("$.currentCgpa").value(8.75)).andExpect(jsonPath("$.maskedAadhaar").value("XXXX XXXX 9012"));
        mvc.perform(get("/api/v1/student/profile").cookie(studentACookie)).andExpect(status().isOk()).andExpect(jsonPath("$.currentCgpa").value(8.75));
    }

    @Test void currentCgpaIsOptionalAndValidatedToTwoDecimals() throws Exception {
        create(studentACookie, profile("A", "UID-A", "a@college.edu"));
        Map<String,Object> update = profile("A", "UID-A", "a@college.edu"); update.put("aadhaarNumber", ""); update.put("currentCgpa", 10.001);
        mvc.perform(put("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.currentCgpa").exists());
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
        var saved = profiles.findByUserId(studentA.getId()).orElseThrow().getUpdatedAt();
        mvc.perform(get("/api/v1/admin/students").param("search", "UID-A").cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(studentA.getId())).andExpect(jsonPath("$.content[0].profileStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.content[0].profileLastStudentUpdatedAt").value(saved.toString()))
                .andExpect(jsonPath("$.content[0].onboardingLastStudentUpdatedAt").isEmpty());
        mvc.perform(get("/api/v1/admin/students").param("profileStatus", "INCOMPLETE").param("page", "0").param("size", "1").cookie(adminCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(studentB.getId()))
                .andExpect(jsonPath("$.content[0].profileLastStudentUpdatedAt").isEmpty())
                .andExpect(jsonPath("$.content[0].onboardingLastStudentUpdatedAt").isEmpty());
        assertThat(profiles.findByUserId(studentA.getId()).orElseThrow().getUpdatedAt()).isEqualTo(saved);
    }

    @Test void adminCanViewStudentDetails() throws Exception {
        create(studentACookie, profile("A", "UID-A", "a@college.edu"));
        mvc.perform(get("/api/v1/admin/students/" + studentA.getId()).cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.uid").value("UID-A")).andExpect(jsonPath("$.profile.maskedAadhaar").value("XXXX XXXX 9012"))
                .andExpect(jsonPath("$.aadhaarNumber").value("123456789012"));
    }

    @Test void importedInstitutionalIdentifiersAndBranchCannotBeChangedByStudent() throws Exception {
        User imported = users.save(new User("imported@college.edu", encoder.encode("StudentPassword!23"), Role.STUDENT, true));
        accounts.save(new StudentAccount(imported, "Imported Student", "23001107", 8));
        Cookie importedCookie = login(imported.getEmail(), "StudentPassword!23");
        mvc.perform(get("/api/v1/student/profile/prefill").cookie(importedCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("Imported Student")).andExpect(jsonPath("$.semester").value(8))
                .andExpect(jsonPath("$.uid").value("23001107")).andExpect(jsonPath("$.instituteEmail").value("imported@college.edu"))
                .andExpect(jsonPath("$.branch").value("COMPUTER_ENGINEERING"))
                .andExpect(jsonPath("$.readOnlyFields", org.hamcrest.Matchers.containsInAnyOrder("studentName", "uid", "instituteEmail", "branch", "semester")));

        Map<String,Object> submitted = profile("Imported", "changed-uid", "changed@college.edu"); submitted.put("branch", "COMPUTER_ENGINEERING"); submitted.put("semester", 8);
        mvc.perform(post("/api/v1/student/profile").cookie(importedCookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(submitted)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.uid").value("23001107"))
                .andExpect(jsonPath("$.instituteEmail").value("imported@college.edu")).andExpect(jsonPath("$.branch").value("COMPUTER_ENGINEERING"))
                .andExpect(jsonPath("$.studentName").value("Imported Student")).andExpect(jsonPath("$.semester").value(8));

        submitted.put("uid", "another-uid"); submitted.put("instituteEmail", "another@college.edu"); submitted.put("branch", "COMPUTER_ENGINEERING"); submitted.put("aadhaarNumber", "");
        submitted.put("studentName", "Manipulated Name"); submitted.put("semester", 8); submitted.put("personalEmail", "updated@example.com");
        submitted.put("phoneNumber", "+91 98765 43219"); submitted.put("currentAddress", "Updated internship address");
        mvc.perform(put("/api/v1/student/profile").cookie(importedCookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(submitted)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uid").value("23001107"))
                .andExpect(jsonPath("$.instituteEmail").value("imported@college.edu")).andExpect(jsonPath("$.branch").value("COMPUTER_ENGINEERING"))
                .andExpect(jsonPath("$.studentName").value("Imported Student")).andExpect(jsonPath("$.semester").value(8))
                .andExpect(jsonPath("$.personalEmail").value("updated@example.com")).andExpect(jsonPath("$.phoneNumber").value("+91 98765 43219"))
                .andExpect(jsonPath("$.currentAddress").value("Updated internship address"));

        String studentBody = mvc.perform(get("/api/v1/student/profile").cookie(importedCookie)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(studentBody).doesNotContain("123456789012");
        assertThat(profiles.findByUserId(imported.getId()).orElseThrow().getAadhaarEncrypted()).doesNotContain("123456789012");
        mvc.perform(get("/api/v1/admin/students/" + imported.getId()).cookie(adminCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.aadhaarNumber").value("123456789012"));
        User mentor = users.save(new User("mentor@example.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        mvc.perform(get("/api/v1/admin/students/" + imported.getId()).cookie(login(mentor.getEmail(), "MentorPassword!23"))).andExpect(status().isForbidden());
    }

    @Test void bootstrapAccountsKeepUserEditableFieldsWhilePrefilledEmailAndBranchAreProtected() throws Exception {
        mvc.perform(get("/api/v1/student/profile/prefill").cookie(studentACookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.instituteEmail").value(studentA.getEmail()))
                .andExpect(jsonPath("$.readOnlyFields", org.hamcrest.Matchers.containsInAnyOrder("instituteEmail", "branch", "semester")));
        Map<String,Object> submitted = profile("A", "LEGACY-UID", "manipulated@college.edu"); submitted.put("branch", "COMPUTER_ENGINEERING"); submitted.put("semester", 8);
        mvc.perform(post("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(submitted)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.uid").value("LEGACY-UID"))
                .andExpect(jsonPath("$.instituteEmail").value(studentA.getEmail())).andExpect(jsonPath("$.branch").value("COMPUTER_ENGINEERING"))
                .andExpect(jsonPath("$.studentName").value("Student A")).andExpect(jsonPath("$.semester").value(8));
        submitted.put("studentName", "Student A Updated"); submitted.put("semester", 8); submitted.put("uid", "CHANGED-UID"); submitted.put("aadhaarNumber", "");
        mvc.perform(put("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(submitted)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentName").value("Student A Updated")).andExpect(jsonPath("$.semester").value(8))
                .andExpect(jsonPath("$.uid").value("LEGACY-UID")).andExpect(jsonPath("$.instituteEmail").value(studentA.getEmail()));
    }

    @Test void existingInstitutionalProfileValuesArePreservedWhenAccountPrefillDiffers() throws Exception {
        User imported = users.save(new User("imported@college.edu", encoder.encode("StudentPassword!23"), Role.STUDENT, true));
        accounts.save(new StudentAccount(imported, "Institutional Name", "IMPORTED-UID", 8));
        Cookie cookie = login(imported.getEmail(), "StudentPassword!23"); create(cookie, profile("Imported", "submitted-uid", "submitted@college.edu"));
        StudentProfile saved = profiles.findByUserId(imported.getId()).orElseThrow();
        saved.apply("Existing Name", Branch.COMPUTER_ENGINEERING, "EXISTING-UID", saved.getRollNumber(), saved.getPermanentAddress(), saved.getCurrentAddress(),
                saved.getPersonalEmail(), "existing@college.edu", saved.getPhoneNumber(), saved.getGuardianName(), saved.getGuardianContact(), saved.getEmergencyContact(),
                saved.getBloodGroup(), saved.getFacultyMentorName(), saved.getFacultyMentorContact(), saved.getInternshipCoordinatorName(), saved.getInternshipCoordinatorContact(),
                saved.getCdcCoordinatorName(), saved.getCdcCoordinatorContact(), saved.getAcademicYear(), 8);
        profiles.saveAndFlush(saved);
        mvc.perform(get("/api/v1/student/profile/prefill").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("Existing Name")).andExpect(jsonPath("$.uid").value("EXISTING-UID"))
                .andExpect(jsonPath("$.instituteEmail").value("existing@college.edu")).andExpect(jsonPath("$.branch").value("COMPUTER_ENGINEERING"))
                .andExpect(jsonPath("$.semester").value(8));
        Map<String,Object> update = profile("Replacement", "replacement-uid", "replacement@college.edu"); update.put("phoneNumber", "+91 98765 43219");
        mvc.perform(put("/api/v1/student/profile").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentName").value("Existing Name")).andExpect(jsonPath("$.uid").value("EXISTING-UID"))
                .andExpect(jsonPath("$.instituteEmail").value("existing@college.edu")).andExpect(jsonPath("$.branch").value("COMPUTER_ENGINEERING"))
                .andExpect(jsonPath("$.semester").value(8)).andExpect(jsonPath("$.phoneNumber").value("+91 98765 43219"));
        assertThat(users.findById(imported.getId()).orElseThrow().getEmail()).isEqualTo("imported@college.edu");
        assertThat(accounts.findAll().stream().filter(account -> account.getUser().getId().equals(imported.getId())).findFirst().orElseThrow().getUid()).isEqualTo("IMPORTED-UID");
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


    @Test void invalidAcademicAssignmentsAreRejectedAndNoSectionIsInvented() throws Exception {
        mvc.perform(get("/api/v1/student/profile/prefill").cookie(studentACookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.semester").value(8)).andExpect(jsonPath("$.section").isEmpty());
        for (var field : List.of("branch", "semester", "section")) {
            Map<String,Object> body = profile("A", "UID-A", "a@college.edu");
            body.put(field, field.equals("branch") ? "MECHANICAL" : field.equals("semester") ? 7 : "C");
            mvc.perform(post("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        Map<String,Object> missing = profile("A", "UID-A", "a@college.edu"); missing.remove("section");
        mvc.perform(post("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(missing)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.section").exists());
        assertThat(profiles.count()).isZero();
    }

    @Test void studentsChooseTheirSectionAndDirectoriesFilterBeforePagination() throws Exception {
        create(studentACookie, profile("A", "UID-A", "a@college.edu"));
        Map<String,Object> second = profile("B", "UID-B", "b@college.edu"); second.put("section", "B"); second.put("rollNumber", "ROLL-A");
        create(studentBCookie, second);
        for (String section : List.of("A", "B")) {
            mvc.perform(get("/api/v1/admin/students").param("section", section).param("size", "1").cookie(adminCookie))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].section").value(section)).andExpect(jsonPath("$.content[0].semester").value(8))
                    .andExpect(jsonPath("$.content[0].branchLabel").value("Computer Engineering"));
        }
        Map<String,Object> update = profile("A", "UID-A", "a@college.edu"); update.put("aadhaarNumber", ""); update.put("section", "B");
        mvc.perform(put("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_ROLL_NUMBER"));
        update.put("rollNumber", "ROLL-A-NEW");
        mvc.perform(put("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.section").value("B")).andExpect(jsonPath("$.sectionNeedsReview").value(false));
    }

    @Test void studentAndReportOptionsExposeOnlyFixedAcademics() throws Exception {
        mvc.perform(get("/api/v1/student/profile/options").cookie(studentACookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.branches.length()").value(1)).andExpect(jsonPath("$.branches[0].value").value("COMPUTER_ENGINEERING"))
                .andExpect(jsonPath("$.minimumSemester").value(8)).andExpect(jsonPath("$.maximumSemester").value(8))
                .andExpect(jsonPath("$.sections", org.hamcrest.Matchers.contains("A", "B")));
        mvc.perform(get("/api/v1/admin/students").param("section", "C").cookie(adminCookie)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/students").param("branch", "MECHANICAL").cookie(adminCookie)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/students").param("semester", "7").cookie(adminCookie)).andExpect(status().isBadRequest());
    }

    @Test void academicReportFiltersAndExportsIncludeOnlyTheSelectedSection() throws Exception {
        create(studentACookie, profile("A", "UID-A", "a@college.edu"));
        Map<String,Object> second = profile("B", "UID-B", "b@college.edu"); second.put("section", "B"); create(studentBCookie, second);
        for (String reportType : List.of("ROSTER", "ATTENDANCE_SUMMARY")) {
            var body = Map.of("reportType", reportType, "fields", List.of("uid", "section", "semester", "branch"), "filters", Map.of("section", "A"), "page", 0, "format", "CSV");
            mvc.perform(post("/api/v1/admin/reports/table").cookie(adminCookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(1))
                    .andExpect(jsonPath("$.rows[0].uid").value("UID-A")).andExpect(jsonPath("$.rows[0].section").value("A"))
                    .andExpect(jsonPath("$.rows[0].semester").value(8)).andExpect(jsonPath("$.rows[0].branch").value("Computer Engineering"));
            String csv = mvc.perform(post("/api/v1/admin/reports/generate").cookie(adminCookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(csv).contains("UID-A", "Section", "Computer Engineering").doesNotContain("UID-B");
        }
        var monthly = Map.of("reportType", "MONTHLY_PROGRESS", "fields", List.of("uid", "section", "semester"), "filters", Map.of("section", "B"), "page", 0, "format", "XLSX");
        mvc.perform(post("/api/v1/admin/reports/table").cookie(adminCookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(monthly)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(0)).andExpect(jsonPath("$.fields[1].id").value("section"));
    }

    @Test void mentorSectionFiltersCannotExposeStudentsAssignedToOtherMentors() throws Exception {
        create(studentACookie, profile("A", "UID-A", "a@college.edu"));
        Map<String,Object> second = profile("B", "UID-B", "b@college.edu"); second.put("section", "B"); create(studentBCookie, second);
        User mentor = users.save(new User("assigned-mentor@college.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        User other = users.save(new User("other-mentor@college.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        assignments.saveAndFlush(new edu.institution.ims.mentor.MentorStudentAssignment(mentor, studentA, admin, java.time.Instant.now()));
        assignments.saveAndFlush(new edu.institution.ims.mentor.MentorStudentAssignment(other, studentB, admin, java.time.Instant.now()));
        Cookie mentorCookie = login(mentor.getEmail(), "MentorPassword!23");
        var hidden = Map.of("reportType", "ROSTER", "fields", List.of("uid", "section"), "filters", Map.of("section", "B"), "page", 0, "format", "CSV");
        mvc.perform(post("/api/v1/mentor/reports/table").cookie(mentorCookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(hidden)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(0));
        var allowed = Map.of("reportType", "ROSTER", "fields", List.of("uid", "section"), "filters", Map.of("section", "A"), "page", 0, "format", "CSV");
        mvc.perform(post("/api/v1/mentor/reports/table").cookie(mentorCookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(allowed)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].uid").value("UID-A"));
        var forged = Map.of("reportType", "ROSTER", "fields", List.of("uid", "section"), "studentIds", List.of(studentB.getId()), "page", 0, "format", "CSV");
        mvc.perform(post("/api/v1/mentor/reports/table").cookie(mentorCookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(forged)))
                .andExpect(status().isForbidden());
        // Removing assignments keeps the existing test cleanup independent of test order.
        assignments.deleteAll();
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
        p.put("academicYear", "2026-2027"); p.put("semester", 8); p.put("section", "A"); return p;
    }
}
