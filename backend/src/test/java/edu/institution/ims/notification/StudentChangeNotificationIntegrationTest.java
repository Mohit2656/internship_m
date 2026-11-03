package edu.institution.ims.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.attendance.*;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.MentorStudentAssignment;
import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.studentprofile.StudentProfile;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StudentChangeNotificationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository accounts;
    @Autowired StudentProfileRepository profiles;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDocumentRepository documents;
    @Autowired InternshipDetailsRepository details;
    @Autowired PasswordEncoder encoder;
    @Autowired InternshipLocationRepository locations;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    User admin;
    User mentor;
    User studentA;
    User studentB;
    Cookie adminCookie;
    Cookie mentorCookie;
    Cookie studentACookie;
    Cookie studentBCookie;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("delete from student_change_audit");
        jdbc.update("delete from mentor_notifications");
        jdbc.update("delete from student_edit_permission_audit");
        jdbc.update("delete from student_edit_permissions");
        jdbc.update("delete from second_internship_requests");
        locations.deleteAll();
        details.deleteAll();
        documents.deleteAll();
        onboardings.deleteAll();
        profiles.deleteAll();
        assignments.deleteAll();
        accounts.deleteAll();
        users.deleteAll();

        admin = users.save(new User("incharge@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        mentor = users.save(new User("mentor@example.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        studentA = student("student.a@example.edu", "StudentAPassword!23");
        studentB = student("student.b@example.edu", "StudentBPassword!23");

        assignments.saveAndFlush(new MentorStudentAssignment(mentor, studentA, admin, Instant.now()));

        adminCookie = login(admin.getEmail(), "AdminPassword!23");
        mentorCookie = login(mentor.getEmail(), "MentorPassword!23");
        studentACookie = login(studentA.getEmail(), "StudentAPassword!23");
        studentBCookie = login(studentB.getEmail(), "StudentBPassword!23");
    }

    @Test
    void studentCanEditProfileWithoutMentorPermissionAndMentorIsAccuratelyNotified() throws Exception {
        ready(studentACookie, "A", "23001022", "a@college.edu");

        // Clear initial notifications from setup
        jdbc.update("delete from mentor_notifications");
        jdbc.update("delete from student_change_audit");

        // Student updates profile phone number and guardian contact
        Map<String, Object> update = profile("A", "23001022", "a@college.edu");
        update.put("phoneNumber", "+91 99999 88888");

        mvc.perform(put("/api/v1/student/profile")
                .cookie(studentACookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+91 99999 88888"));

        // Mentor received accurate notification
        mvc.perform(get("/api/v1/mentor/notifications").cookie(mentorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("STUDENT_PROFILE_UPDATED"))
                .andExpect(jsonPath("$.content[0].title").value("Profile updated (23001022)"))
                .andExpect(jsonPath("$.content[0].message").value(org.hamcrest.Matchers.containsString("Student 23001022 updated Phone Number from +91 98765 43210 to +91 99999 88888")));

        // Audit trail recorded
        Integer auditCount = jdbc.queryForObject(
                "select count(*) from student_change_audit where student_user_id=? and change_type='PROFILE_UPDATE'",
                Integer.class, studentA.getId());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void studentCanEditInternshipAfterStartWithoutMentorPermissionAndMentorIsAccuratelyNotified() throws Exception {
        readyAndCreate(studentACookie, "A", "23001022", "a@college.edu", "ABC Corp");
        Long internshipId = details.findByOnboardingStudentId(studentA.getId()).orElseThrow().getId();

        // Mark internship as already started
        jdbc.update("update internship_details set start_date=?, end_date=? where id=?",
                LocalDate.now().minusDays(5), LocalDate.now().plusMonths(3), internshipId);

        jdbc.update("delete from mentor_notifications");
        jdbc.update("delete from student_change_audit");

        // Student updates company name from ABC Corp to XYZ Corp
        Map<String, Object> update = internship("XYZ Corp");
        update.put("startDate", LocalDate.now().minusDays(5).toString());
        update.put("endDate", LocalDate.now().plusMonths(3).toString());

        mvc.perform(put("/api/v1/student/internship")
                .cookie(studentACookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("XYZ Corp"));

        // Mentor receives accurate notification formatted with UID, fields, old/new values and date
        mvc.perform(get("/api/v1/mentor/notifications").cookie(mentorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("INTERNSHIP_DETAILS_UPDATED"))
                .andExpect(jsonPath("$.content[0].title").value("Internship details updated (23001022)"))
                .andExpect(jsonPath("$.content[0].message").value(org.hamcrest.Matchers.containsString("Student 23001022 updated Company Name from ABC Corp to XYZ Corp")));

        // Audit trail verified
        Integer auditCount = jdbc.queryForObject(
                "select count(*) from student_change_audit where student_user_id=? and change_type='INTERNSHIP_UPDATE' and internship_id=?",
                Integer.class, studentA.getId(), internshipId);
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void unchangedSubmissionGeneratesNoNotificationsOrAudits() throws Exception {
        readyAndCreate(studentACookie, "A", "23001022", "a@college.edu", "ABC Corp");
        Long internshipId = details.findByOnboardingStudentId(studentA.getId()).orElseThrow().getId();

        jdbc.update("delete from mentor_notifications");
        jdbc.update("delete from student_change_audit");

        // Submit exact same profile data
        mvc.perform(put("/api/v1/student/profile")
                .cookie(studentACookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(profile("A", "23001022", "a@college.edu"))))
                .andExpect(status().isOk());

        // Submit exact same internship data
        mvc.perform(put("/api/v1/student/internship")
                .cookie(studentACookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(internship("ABC Corp"))))
                .andExpect(status().isOk());

        // Verify no notifications generated
        mvc.perform(get("/api/v1/mentor/notifications").cookie(mentorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        // Verify no audit rows generated
        Integer auditCount = jdbc.queryForObject(
                "select count(*) from student_change_audit where student_user_id=?",
                Integer.class, studentA.getId());
        assertThat(auditCount).isZero();
    }

    @Test
    void workplaceCoordinateChangeInvalidatesApprovalAndRequiresFreshMentorVerification() throws Exception {
        readyAndCreate(studentACookie, "A", "23001022", "a@college.edu", "Acme Systems");
        var internship = details.findByOnboardingStudentId(studentA.getId()).orElseThrow();

        // Simulate approved workplace
        var location = new InternshipLocation(internship,
                new BigDecimal("30.733315"), new BigDecimal("76.779419"),
                new BigDecimal("200"), Instant.now());
        location.selectedPlace("office-place-1", "Office Address 1");
        location.review(mentor, "APPROVE", "Approved", Instant.now());
        locations.saveAndFlush(location);

        assertThat(location.getStatus()).isEqualTo(InternshipLocationStatus.APPROVED);

        // Student updates workplace coordinates
        Map<String, Object> update = internship("Acme Systems");
        update.put("workplace", Map.of(
                "placeId", "office-place-2",
                "address", "New Office Address 2",
                "latitude", 30.750000,
                "longitude", 76.780000
        ));

        mvc.perform(put("/api/v1/student/internship")
                .cookie(studentACookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(update)))
                .andExpect(status().isOk());

        // Verify workplace approval was invalidated and status set to PENDING_MENTOR_APPROVAL
        var updatedLocation = locations.findById(location.getId()).orElseThrow();
        assertThat(updatedLocation.getStatus()).isEqualTo(InternshipLocationStatus.PENDING_MENTOR_APPROVAL);
        assertThat(updatedLocation.getConfirmedAt()).isNull();
        assertThat(updatedLocation.getConfirmedBy()).isNull();
        assertThat(updatedLocation.getLatitude()).isEqualByComparingTo("30.750000");
        assertThat(updatedLocation.getLongitude()).isEqualByComparingTo("76.780000");

        // Verify mentor is notified about coordinate change
        mvc.perform(get("/api/v1/mentor/notifications").cookie(mentorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].message").value(org.hamcrest.Matchers.containsString("Workplace Coordinates")));
    }

    @Test
    void studentCannotEditAnotherStudentsRecords() throws Exception {
        readyAndCreate(studentACookie, "A", "23001022", "a@college.edu", "Company A");
        readyAndCreate(studentBCookie, "B", "23001023", "b@college.edu", "Company B");

        Long internshipAId = details.findByOnboardingStudentId(studentA.getId()).orElseThrow().getId();
        Long internshipBId = details.findByOnboardingStudentId(studentB.getId()).orElseThrow().getId();

        // Student A tries to update Student B's internship ID via query parameter
        Map<String, Object> maliciousUpdate = internship("Hacked Company");
        mvc.perform(put("/api/v1/student/internship?internshipId=" + internshipBId)
                .cookie(studentACookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(maliciousUpdate)))
                .andExpect(status().isNotFound());

        // Student B's record remains untouched
        assertThat(details.findById(internshipBId).orElseThrow().getCompanyName()).isEqualTo("Company B");
    }

    @Test
    void dualInternshipDataRemainsIsolatedAndCannotBypassAuthorization() throws Exception {
        readyAndCreate(studentACookie, "A", "23001022", "a@college.edu", "Internship 1 Company");

        // Attempting to update second internship before approval fails with 404 (not found / not authorized)
        Map<String, Object> update2 = internship("Unauthorized Internship 2");
        mvc.perform(put("/api/v1/student/internship/second")
                .cookie(studentACookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(update2)))
                .andExpect(status().isNotFound());

        // Student requests second internship
        mvc.perform(post("/api/v1/student/second-internship-requests").cookie(studentACookie))
                .andExpect(status().isCreated());
        Long requestId = jdbc.queryForObject("select id from second_internship_requests where student_user_id=?", Long.class, studentA.getId());

        // IIIC Cell approves second internship
        mvc.perform(put("/api/v1/admin/second-internship-requests/{id}", requestId)
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true}"))
                .andExpect(status().isOk());

        // Student completes initial onboarding and diary for Internship 2
        mvc.perform(put("/api/v1/student/internship-onboarding/source?internshipNumber=2").cookie(studentACookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"SELF\"}")).andExpect(status().isOk());
        for (DocumentType type : DocumentType.values()) {
            mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/{type}?internshipNumber=2", type)
                    .file(pdf(type.name() + "_2.pdf")).cookie(studentACookie)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/student/internship-onboarding/diary/confirm?internshipNumber=2").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diaryIssued").value(true));

        // Student creates second internship
        Map<String, Object> createSecond = internship("Internship 2 Company");
        createSecond.put("startDate", "2027-07-01");
        createSecond.put("endDate", "2027-12-31");
        mvc.perform(post("/api/v1/student/internship/second")
                .cookie(studentACookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(createSecond)))
                .andExpect(status().isCreated());

        jdbc.update("delete from mentor_notifications");
        jdbc.update("delete from student_change_audit");

        // Student updates second internship
        createSecond.put("companyName", "Internship 2 Company Renamed");
        mvc.perform(put("/api/v1/student/internship/second")
                .cookie(studentACookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(createSecond)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Internship 2 Company Renamed"));

        // First internship remains untouched
        var firstDetails = details.findByOnboardingStudentId(studentA.getId()).orElseThrow();
        assertThat(firstDetails.getCompanyName()).isEqualTo("Internship 1 Company");

        // Mentor receives separate notification distinguishing Internship 2
        mvc.perform(get("/api/v1/mentor/notifications").cookie(mentorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Internship 2 details updated (23001022)"))
                .andExpect(jsonPath("$.content[0].message").value(org.hamcrest.Matchers.containsString("Internship 2 Company Name from Internship 2 Company to Internship 2 Company Renamed")));
    }

    private User student(String email, String password) {
        User user = users.save(new User(email, encoder.encode(password), Role.STUDENT, true));
        accounts.save(new StudentAccount(user));
        return user;
    }

    private Cookie login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password))))
                .andReturn().getResponse().getCookie("ims_auth");
    }

    private void readyAndCreate(Cookie cookie, String suffix, String uid, String email, String company) throws Exception {
        ready(cookie, suffix, uid, email);
        mvc.perform(post("/api/v1/student/internship")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(internship(company))))
                .andExpect(status().isCreated());
    }

    private void ready(Cookie cookie, String suffix, String uid, String email) throws Exception {
        completeProfile(cookie, suffix, uid, email);
        mvc.perform(post("/api/v1/student/internship-onboarding/secure").cookie(cookie)).andExpect(status().isCreated());
        mvc.perform(put("/api/v1/student/internship-onboarding/source").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"SELF\"}")).andExpect(status().isOk());
        for (DocumentType type : DocumentType.values()) {
            mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/{type}", type)
                    .file(pdf(type.name() + ".pdf")).cookie(cookie)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/student/internship-onboarding/diary/confirm").cookie(cookie)).andExpect(status().isOk());
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4\n%%EOF".getBytes());
    }

    private void completeProfile(Cookie cookie, String suffix, String uid, String instituteEmail) throws Exception {
        mvc.perform(post("/api/v1/student/profile").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(profile(suffix, uid, instituteEmail))))
                .andExpect(status().isCreated());
    }

    private Map<String, Object> internship(String company) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("companyName", company);
        data.put("companyType", "IT");
        data.put("otherCompanyType", "ignored");
        data.put("companyAddress", "100 Industry Road, Chandigarh");
        data.put("companyPhone", "+91 172 555 0100");
        data.put("industrySupervisorName", "Industry Supervisor");
        data.put("industrySupervisorDesignation", "Engineering Manager");
        data.put("industrySupervisorContact", "+91 98765 40100");
        data.put("durationMonths", 6);
        data.put("startDate", "2027-01-01");
        data.put("endDate", "2027-06-30");
        data.put("totalWeeks", 26);
        data.put("internshipMode", "HYBRID");
        data.put("stipendPerMonth", 15000.00);
        data.put("fullTimeEmploymentOffered", true);
        return data;
    }

    private Map<String, Object> profile(String suffix, String uid, String instituteEmail) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("studentName", "Student " + suffix);
        p.put("branch", "COMPUTER_ENGINEERING");
        p.put("uid", uid);
        p.put("rollNumber", "ROLL-" + suffix);
        p.put("permanentAddress", "Permanent address " + suffix);
        p.put("currentAddress", "");
        p.put("personalEmail", "personal" + suffix.toLowerCase() + "@example.com");
        p.put("instituteEmail", instituteEmail);
        p.put("phoneNumber", "+91 98765 43210");
        p.put("guardianName", "Guardian " + suffix);
        p.put("guardianContact", "+91 98765 43211");
        p.put("emergencyContact", "+91 98765 43212");
        p.put("bloodGroup", "O+");
        p.put("aadhaarNumber", "123456789012");
        p.put("facultyMentorName", "Faculty Mentor");
        p.put("facultyMentorContact", "+91 98765 43213");
        p.put("internshipCoordinatorName", "Internship Coordinator");
        p.put("internshipCoordinatorContact", "+91 98765 43214");
        p.put("cdcCoordinatorName", "CDC Coordinator");
        p.put("cdcCoordinatorContact", "+91 98765 43215");
        p.put("academicYear", "2026-2027");
        p.put("semester", 7);
        return p;
    }
}
