package edu.institution.ims.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentorAssignmentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository studentAccounts;
    @Autowired StudentProfileRepository studentProfiles;
    @Autowired MentorProfileRepository mentorProfiles;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired PasswordEncoder encoder;

    User admin;
    User mentorA;
    User mentorB;
    User studentA;
    User studentB;
    User unassignedStudent;

    @BeforeEach
    void setup() {
        clearAccounts();
        admin = user("admin@example.edu", "AdminPassword!23", Role.ADMIN);
        mentorA = mentor("mentor.a@example.edu", "MentorPassword!23", "Dr. Asha", "FAC100");
        mentorB = mentor("mentor.b@example.edu", "MentorPassword!24", "Dr. Bharat", "FAC200");
        studentA = student("student.a@example.edu", "StudentPassword!23", "Student A", "UID100", 6);
        studentB = student("student.b@example.edu", "StudentPassword!24", "Student B", "UID200", 6);
        unassignedStudent = student("student.c@example.edu", "StudentPassword!25", "Student C", "UID300", 5);
    }

    @AfterEach
    void cleanup() {
        clearAccounts();
    }

    @Test
    void adminAssignsMultipleStudentsAndMentorsSeeOnlyTheirOwnCurrentStudents() throws Exception {
        Cookie adminSession = loginCookie(admin.getEmail(), "AdminPassword!23");
        mvc.perform(post("/api/v1/admin/mentors/{mentorId}/students", mentorA.getId())
                        .cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AssignStudentsRequest(List.of(studentA.getId(), studentB.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedStudentsCount").value(2))
                .andExpect(jsonPath("$.unassignedStudentsCount").value(1))
                .andExpect(jsonPath("$.mentor.assignedStudentCount").value(2))
                .andExpect(jsonPath("$.assignedStudents.length()").value(2));

        assertThat(assignments.countByMentorIdAndActiveTrue(mentorA.getId())).isEqualTo(2);
        mvc.perform(get("/api/v1/mentor/students").cookie(loginCookie(mentorA.getEmail(), "MentorPassword!23")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].uid").value(org.hamcrest.Matchers.containsInAnyOrder("UID100", "UID200")))
                .andExpect(jsonPath("$[*].uid").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("UID300"))));
        mvc.perform(get("/api/v1/mentor/students").cookie(loginCookie(mentorB.getEmail(), "MentorPassword!24")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void reassignmentAndRemovalPreserveHistoryAndDatabaseRejectsTwoActiveMentors() throws Exception {
        Cookie adminSession = loginCookie(admin.getEmail(), "AdminPassword!23");
        assign(adminSession, mentorA, studentA).andExpect(status().isOk());
        assign(adminSession, mentorB, studentA).andExpect(status().isOk());

        List<MentorStudentAssignment> history = assignments.findAllByStudentIdOrderByAssignedAtAsc(studentA.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).isActive()).isFalse();
        assertThat(history.get(0).getEndedAt()).isNotNull();
        assertThat(history.get(1).isActive()).isTrue();
        assertThat(history.get(1).getMentor().getId()).isEqualTo(mentorB.getId());

        mvc.perform(get("/api/v1/mentor/students").cookie(loginCookie(mentorA.getEmail(), "MentorPassword!23")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/mentor/students").cookie(loginCookie(mentorB.getEmail(), "MentorPassword!24")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].uid").value("UID100"));

        assertThatThrownBy(() -> assignments.saveAndFlush(
                new MentorStudentAssignment(mentorA, studentA, admin, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);

        mvc.perform(delete("/api/v1/admin/mentors/{mentorId}/students/{studentId}", mentorB.getId(), studentA.getId())
                        .cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedStudentsCount").value(0))
                .andExpect(jsonPath("$.unassignedStudentsCount").value(3));
        assertThat(assignments.findAllByStudentIdOrderByAssignedAtAsc(studentA.getId()))
                .hasSize(2).allMatch(assignment -> !assignment.isActive() && assignment.getEndedAt() != null);
    }

    @Test
    void assignmentEndpointsRejectUnauthorizedRolesInvalidRolesAndDuplicateIds() throws Exception {
        Cookie studentSession = loginCookie(studentA.getEmail(), "StudentPassword!23");
        Cookie mentorSession = loginCookie(mentorA.getEmail(), "MentorPassword!23");
        Cookie adminSession = loginCookie(admin.getEmail(), "AdminPassword!23");
        String body = json.writeValueAsString(new AssignStudentsRequest(List.of(studentB.getId())));

        mvc.perform(post("/api/v1/admin/mentors/{mentorId}/students", mentorA.getId())
                        .cookie(studentSession).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/mentors/{mentorId}/students", mentorA.getId())
                        .cookie(mentorSession).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mentor/students").cookie(studentSession))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/mentors/{mentorId}/students", 999999L)
                        .cookie(adminSession).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MENTOR_NOT_FOUND"));
        mvc.perform(post("/api/v1/admin/mentors/{mentorId}/students", studentA.getId())
                        .cookie(adminSession).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MENTOR_NOT_FOUND"));
        mvc.perform(post("/api/v1/admin/mentors/{mentorId}/students", mentorA.getId())
                        .cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AssignStudentsRequest(List.of(mentorB.getId())))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STUDENT_ROLE"));
        mvc.perform(post("/api/v1/admin/mentors/{mentorId}/students", mentorA.getId())
                        .cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AssignStudentsRequest(List.of(studentA.getId(), studentA.getId())))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_STUDENT_IDS"));
    }

    @Test
    void mentorCanViewOnlyAnAssignedStudentsNonSensitiveProfileAndConfirmedSkills() throws Exception {
        Cookie adminSession = loginCookie(admin.getEmail(), "AdminPassword!23");
        Cookie studentSession = loginCookie(studentA.getEmail(), "StudentPassword!23");
        completeProfile(studentA, studentSession);
        mvc.perform(put("/api/v1/student/professional-profile").cookie(studentSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Backend Developer\",\"careerInterests\":\"Cloud engineering\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/student/professional-profile/skills").cookie(studentSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java\"}"))
                .andExpect(status().isOk());
        assign(adminSession, mentorA, studentA).andExpect(status().isOk());

        mvc.perform(get("/api/v1/mentor/students/{id}/profile", studentA.getId())
                        .cookie(loginCookie(mentorA.getEmail(), "MentorPassword!23")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uid").value("UID100"))
                .andExpect(jsonPath("$.personalEmail").value("personal@example.com"))
                .andExpect(jsonPath("$.professionalHeadline").value("Backend Developer"))
                .andExpect(jsonPath("$.confirmedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.aadhaarNumber").doesNotExist())
                .andExpect(jsonPath("$.aadhaarEncrypted").doesNotExist())
                .andExpect(jsonPath("$.maskedAadhaar").doesNotExist())
                .andExpect(jsonPath("$.permanentAddress").doesNotExist())
                .andExpect(jsonPath("$.guardianContact").doesNotExist());
        mvc.perform(get("/api/v1/mentor/students/{id}/profile", studentA.getId())
                        .cookie(loginCookie(mentorB.getEmail(), "MentorPassword!24")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ASSIGNED_STUDENT_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions assign(Cookie adminSession, User mentor, User student) throws Exception {
        return mvc.perform(post("/api/v1/admin/mentors/{mentorId}/students", mentor.getId())
                .cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AssignStudentsRequest(List.of(student.getId())))));
    }

    private User user(String email, String password, Role role) {
        return users.save(new User(email, encoder.encode(password), role, true));
    }

    private User mentor(String email, String password, String name, String employeeId) {
        User mentor = user(email, password, Role.MENTOR);
        mentorProfiles.save(new MentorProfile(mentor, name, employeeId, "Computer Engineering", "Professor", "+91 9876543210"));
        return mentor;
    }

    private User student(String email, String password, String name, String uid, int semester) {
        User student = user(email, password, Role.STUDENT);
        studentAccounts.save(new StudentAccount(student, name, uid, semester));
        return student;
    }

    private void completeProfile(User student, Cookie cookie) throws Exception {
        Map<String, Object> profile = new java.util.LinkedHashMap<>();
        profile.put("studentName", "Student A"); profile.put("branch", "COMPUTER_ENGINEERING");
        profile.put("uid", "UID100"); profile.put("rollNumber", "ROLL100");
        profile.put("permanentAddress", "Private address"); profile.put("personalEmail", "personal@example.com");
        profile.put("instituteEmail", student.getEmail()); profile.put("phoneNumber", "9876543210");
        profile.put("guardianName", "Guardian"); profile.put("guardianContact", "9876543211");
        profile.put("emergencyContact", "9876543212"); profile.put("bloodGroup", "O+"); profile.put("aadhaarNumber", "123456789012");
        profile.put("facultyMentorName", "Faculty"); profile.put("facultyMentorContact", "9876543213");
        profile.put("internshipCoordinatorName", "Coordinator"); profile.put("internshipCoordinatorContact", "9876543214");
        profile.put("cdcCoordinatorName", "CDC"); profile.put("cdcCoordinatorContact", "9876543215");
        profile.put("academicYear", "2025-2026"); profile.put("semester", 6);
        mvc.perform(post("/api/v1/student/profile").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(profile))).andExpect(status().isCreated());
    }

    private Cookie loginCookie(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }

    private void clearAccounts() {
        assignments.deleteAll();
        mentorProfiles.deleteAll();
        studentProfiles.deleteAll();
        studentAccounts.deleteAll();
        users.deleteAll();
    }
}
