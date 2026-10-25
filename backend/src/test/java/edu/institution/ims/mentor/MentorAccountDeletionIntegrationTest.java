package edu.institution.ims.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.attendance.*;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.*;
import edu.institution.ims.monthlyprogress.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MentorAccountDeletionIntegrationTest {
    private static final String PASSWORD = "AccountPassword!23";
    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired MentorProfileRepository mentors;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired StudentAccountRepository studentAccounts;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired AttendanceRepository attendance;
    @Autowired MonthlyProgressReportRepository reports;
    @Autowired MonthlyProgressReviewEventRepository reviewEvents;
    @Autowired MentorAccountService service;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    User admin;
    User mentor;
    User student;

    @BeforeEach void setup() {
        admin = user("admin", Role.ADMIN);
        mentor = user("mentor", Role.MENTOR);
        mentors.saveAndFlush(new MentorProfile(mentor, "Dr. Asha Mehta", "FAC" + mentor.getId(), "Computer Engineering", "Professor", "9876543210"));
        student = user("student", Role.STUDENT);
        studentAccounts.saveAndFlush(new StudentAccount(student));
    }

    @Test void onlyAuthenticatedAdminMayDeleteAndNonMentorTargetsAreProtected() throws Exception {
        mvc.perform(delete(endpoint(mentor))).andExpect(status().isUnauthorized());
        for (User denied : List.of(student, mentor, user("hod", Role.HOD))) {
            mvc.perform(delete(endpoint(mentor)).cookie(session(denied))).andExpect(status().isForbidden());
        }
        Cookie adminSession = session(admin);
        mvc.perform(delete(endpoint(admin)).cookie(adminSession)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROTECTED_ACCOUNT"));
        for (User protectedUser : List.of(student, user("other-admin", Role.ADMIN))) {
            mvc.perform(delete(endpoint(protectedUser)).cookie(adminSession)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_MENTOR_ACCOUNT"));
            assertThat(users.findById(protectedUser.getId()).orElseThrow().isActive()).isTrue();
        }
        assertThat(users.findById(mentor.getId()).orElseThrow().isActive()).isTrue();
        assertThat(users.findById(admin.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test void missingAccountsAndProfilesAreDistinctFromAnUnmappedDeletionEndpoint() throws Exception {
        Cookie adminSession = session(admin);
        User withoutProfile = user("unprofiled-mentor", Role.MENTOR);
        Map<String, List<Map<String, Object>>> before = retainedRecords();
        mvc.perform(delete("/api/v1/admin/mentor-accounts/9223372036854775807").cookie(adminSession))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MENTOR_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Mentor account was not found"));
        mvc.perform(delete(endpoint(withoutProfile)).cookie(adminSession))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MENTOR_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Mentor profile was not found"));
        mvc.perform(delete("/api/v1/admin/mentor-accounts/unmapped/17").cookie(adminSession))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource was not found"));
        assertThat(users.findById(withoutProfile.getId()).orElseThrow().isActive()).isTrue();
        assertThat(users.findById(mentor.getId()).orElseThrow().isActive()).isTrue();
        assertThat(retainedRecords()).isEqualTo(before);
    }

    @Test void serviceBoundaryRejectsMissingForgedAndInactiveCoordinators() {
        assertThatThrownBy(() -> service.deactivate(mentor.getId(), null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deactivate(mentor.getId(), UserPrincipal.from(student))).isInstanceOf(AccessDeniedException.class);
        var forged = new UserPrincipal(student.getId(), student.getEmail(), "unused", true, "ADMIN", false, 0);
        assertThatThrownBy(() -> service.deactivate(mentor.getId(), forged)).isInstanceOf(AccessDeniedException.class);
        UserPrincipal staleAdmin = UserPrincipal.from(admin);
        admin.deactivate();
        users.saveAndFlush(admin);
        assertThatThrownBy(() -> service.deactivate(mentor.getId(), staleAdmin)).isInstanceOf(AccessDeniedException.class);
        assertThat(users.findById(mentor.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test void deletingUnusedMentorDeactivatesIdempotentlyAndRevokesLoginAndExistingSession() throws Exception {
        MentorProfile profile = mentors.findByUserId(mentor.getId()).orElseThrow();
        assertThat(profile.getId()).isNotEqualTo(mentor.getId());
        User otherMentor = user("other-mentor", Role.MENTOR);
        MentorProfile otherProfile = mentors.saveAndFlush(new MentorProfile(otherMentor, "Dr. Zed", "FAC" + otherMentor.getId(),
                "Computer Engineering", "Professor", "9876543210"));
        Cookie mentorSession = session(mentor);
        Cookie adminSession = session(admin);
        mvc.perform(get("/api/v1/admin/mentor-accounts").cookie(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(profile.getId())).andExpect(jsonPath("$[0].userId").value(mentor.getId()));
        mvc.perform(delete(endpoint(mentor)).cookie(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profile.getId()))
                .andExpect(jsonPath("$.userId").value(mentor.getId())).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.assignedStudentCount").value(0));
        mvc.perform(delete(endpoint(mentor)).cookie(adminSession)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertThat(users.findById(mentor.getId()).orElseThrow().getTokenVersion()).isEqualTo(1);
        assertThat(mentors.findByUserId(mentor.getId()).orElseThrow().getId()).isEqualTo(profile.getId());
        assertThat(users.findById(otherMentor.getId()).orElseThrow().isActive()).isTrue();
        assertThat(mentors.findByUserId(otherMentor.getId()).orElseThrow().getId()).isEqualTo(otherProfile.getId());
        assertThat(users.findById(student.getId()).orElseThrow().isActive()).isTrue();
        assertThat(users.findById(admin.getId()).orElseThrow().isActive()).isTrue();
        mvc.perform(get("/api/v1/admin/mentor-accounts").cookie(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(otherMentor.getId())).andExpect(jsonPath("$[0].active").value(true));
        mvc.perform(get("/api/v1/mentor/profile").cookie(mentorSession)).andExpect(status().isUnauthorized());
        login(mentor).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test void deletingAndRecreatingAssignedMentorPreservesStudentsAttendanceApprovedReportsAndAuditAttribution() throws Exception {
        assignments.saveAndFlush(new MentorStudentAssignment(mentor, student, admin, NOW));
        InternshipOnboarding onboarding = onboardings.saveAndFlush(new InternshipOnboarding(student));
        InternshipDetails internship = new InternshipDetails(onboarding);
        internship.apply("History Ltd", CompanyType.IT, null, "Pune", "9876543210", "Supervisor", "Manager", "9876543210", 3,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), 13, InternshipMode.ONLINE, BigDecimal.ZERO, false);
        internships.saveAndFlush(internship);
        AttendanceRecord record = attendance.saveAndFlush(new AttendanceRecord(student, internship, LocalDate.of(2026, 10, 20),
                AttendanceResult.PRESENT, VerificationStatus.UNVERIFIED, AttendanceSubmissionType.BASIC, NOW));
        MonthlyProgressReport report = new MonthlyProgressReport(internship, 1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                "Retained approved work", "historical-photo.png", "photo.png", "image/png", 100, null, null, null, NOW);
        report.decide(mentor, true, null, NOW);
        reports.saveAndFlush(report);
        reviewEvents.saveAndFlush(new MonthlyProgressReviewEvent(report, mentor, "APPROVED", null, NOW));
        jdbc.update("update attendance_records set late_authorized_by=?,late_authorized_at=? where id=?", mentor.getId(), Timestamp.from(NOW), record.getId());
        jdbc.update("insert into mentor_record_deletion_audit(record_type,record_id,deleting_user_id,deleted_at,reason) values ('ATTENDANCE',?,?,?,?)",
                record.getId(), mentor.getId(), Timestamp.from(NOW), "Retained audit attribution");
        entityManager.flush();
        Map<String, List<Map<String, Object>>> before = retainedRecords();
        Cookie adminSession = session(admin);
        mvc.perform(delete(endpoint(mentor)).cookie(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.assignedStudentCount").value(1));
        assertThat(retainedRecords()).isEqualTo(before);
        assertThat(users.findById(student.getId()).orElseThrow().isActive()).isTrue();
        assertThat(jdbc.queryForObject("select reviewer_id from monthly_progress_reports where id=?", Long.class, report.getId())).isEqualTo(mentor.getId());
        assertThat(jdbc.queryForObject("select actor_id from monthly_progress_review_events where report_id=?", Long.class, report.getId())).isEqualTo(mentor.getId());
        mvc.perform(get("/api/v1/admin/mentors/{id}/students", mentor.getId()).cookie(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mentor.active").value(false)).andExpect(jsonPath("$.assignedStudents[0].userId").value(student.getId()));
        mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request(mentor.getEmail(), "FAC" + mentor.getId(), "Dr. Asha Mehta"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(mentor.getId()))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.assignedStudentCount").value(1));
        assertThat(retainedRecords()).isEqualTo(before);
    }

    @Test void deletedMentorRequiresCompleteCreationFormAndReceivesFreshTemporaryCredentials() throws Exception {
        MentorProfile profile = mentors.findByUserId(mentor.getId()).orElseThrow();
        long userCount = users.count();
        long profileCount = mentors.count();
        Cookie oldSession = session(mentor);
        Cookie adminSession = session(admin);
        mvc.perform(delete(endpoint(mentor)).cookie(adminSession)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/mentor-accounts").cookie(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("instituteEmail", mentor.getEmail(), "employeeId", profile.getEmployeeId()))))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(mentor.getId()).orElseThrow().isActive()).isFalse();

        MentorAccountRequest request = new MentorAccountRequest(profile.getName(), " fac" + mentor.getId() + " ",
                " " + mentor.getEmail().toUpperCase() + " ", "Electronics", "Associate Professor", "9123456780");
        mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(profile.getId()))
                .andExpect(jsonPath("$.userId").value(mentor.getId())).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.mustChangePassword").value(true)).andExpect(jsonPath("$.department").value("Electronics"))
                .andExpect(jsonPath("$.designation").value("Associate Professor")).andExpect(jsonPath("$.phone").value("9123456780"));
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(mentors.count()).isEqualTo(profileCount);
        User recreated = users.findById(mentor.getId()).orElseThrow();
        assertThat(recreated.getTokenVersion()).isEqualTo(2);
        assertThat(encoder.matches(profile.getEmployeeId(), recreated.getPasswordHash())).isTrue();
        mvc.perform(get("/api/v1/auth/me").cookie(oldSession)).andExpect(status().isUnauthorized());
        login(mentor).andExpect(status().isUnauthorized());
        Cookie temporarySession = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(mentor.getEmail(), profile.getEmployeeId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn().getResponse().getCookie("ims_auth");
        mvc.perform(get("/api/v1/mentor/profile").cookie(temporarySession)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/v1/admin/mentor-accounts").cookie(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].userId").value(mentor.getId()));
    }

    @Test void recreationRejectsMismatchedIdentityAndProtectedOrUnprofiledAccounts() throws Exception {
        Cookie adminSession = session(admin);
        mvc.perform(delete(endpoint(mentor)).cookie(adminSession)).andExpect(status().isOk());
        User unprofiled = user("unprofiled", Role.MENTOR);
        unprofiled.deactivate();
        users.saveAndFlush(unprofiled);
        User inactiveAdmin = user("inactive-admin", Role.ADMIN);
        inactiveAdmin.deactivate();
        users.saveAndFlush(inactiveAdmin);
        student.deactivate();
        users.saveAndFlush(student);
        Map<String, List<Map<String, Object>>> before = retainedRecords();
        for (MentorAccountRequest mismatch : List.of(
                request(mentor.getEmail(), "OTHER" + mentor.getId(), "Dr. Asha Mehta"),
                request(mentor.getEmail(), "FAC" + mentor.getId(), "Different Person"),
                request(unprofiled.getEmail(), "NEW123", "Dr. Asha Mehta"))) {
            mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(mismatch))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MENTOR_IDENTITY_CONFLICT"));
        }
        mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("different@example.edu", "FAC" + mentor.getId(), "Dr. Asha Mehta"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMPLOYEE_ID"));
        for (User protectedUser : List.of(student, inactiveAdmin, admin)) {
            mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request(protectedUser.getEmail(), "OTHER123", "Dr. Asha Mehta"))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        }
        assertThat(users.findById(mentor.getId()).orElseThrow().isActive()).isFalse();
        assertThat(users.findById(unprofiled.getId()).orElseThrow().isActive()).isFalse();
        assertThat(users.findById(inactiveAdmin.getId()).orElseThrow().isActive()).isFalse();
        assertThat(retainedRecords()).isEqualTo(before);
    }

    @Test void deactivatedMentorCannotReceiveAssignmentsAndAdminCanExplicitlyRemoveOrReassignStudents() throws Exception {
        assignments.saveAndFlush(new MentorStudentAssignment(mentor, student, admin, NOW));
        Cookie adminSession = session(admin);
        mvc.perform(delete(endpoint(mentor)).cookie(adminSession)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/mentors/{id}/students", mentor.getId()).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("studentUserIds", List.of(student.getId())))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MENTOR_ACCOUNT_INACTIVE"));
        mvc.perform(delete("/api/v1/admin/mentors/{id}/students/{studentId}", mentor.getId(), student.getId()).cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedStudents.length()").value(0));
        assertThat(assignments.findByStudentIdAndActiveTrue(student.getId())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from mentor_student_assignments where mentor_user_id=?", Integer.class, mentor.getId())).isEqualTo(1);
        User replacement = user("replacement", Role.MENTOR);
        mentors.saveAndFlush(new MentorProfile(replacement, "Dr. Replacement", "FAC" + replacement.getId(), "Computer Engineering", "Professor", "9876543210"));
        mvc.perform(post("/api/v1/admin/mentors/{id}/students", replacement.getId()).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("studentUserIds", List.of(student.getId())))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedStudents[0].userId").value(student.getId()));
        assertThat(assignments.findByStudentIdAndActiveTrue(student.getId()).orElseThrow().getMentor().getId()).isEqualTo(replacement.getId());
    }

    private Map<String, List<Map<String, Object>>> retainedRecords() {
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        for (String table : List.of("student_accounts", "student_profiles", "internship_onboarding", "internship_details",
                "mentor_profiles", "mentor_student_assignments", "attendance_records", "monthly_progress_reports",
                "monthly_progress_review_events", "mentor_record_deletion_audit")) {
            rows.put(table, jdbc.queryForList("select * from " + table + " order by id"));
        }
        rows.put("student_users", jdbc.queryForList("select * from users where role='STUDENT' order by id"));
        return rows;
    }
    private MentorAccountRequest request(String email, String employeeId, String name) {
        return new MentorAccountRequest(name, employeeId, email, "Computer Engineering", "Professor", "9876543210");
    }
    private User user(String name, Role role) {
        return users.saveAndFlush(new User(name + "." + UUID.randomUUID() + "@example.edu", encoder.encode(PASSWORD), role, true));
    }
    private String endpoint(User user) { return "/api/v1/admin/mentor-accounts/" + user.getId(); }
    private ResultActions login(User user) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(user.getEmail(), PASSWORD))));
    }
    private Cookie session(User user) throws Exception {
        return login(user).andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }
}
