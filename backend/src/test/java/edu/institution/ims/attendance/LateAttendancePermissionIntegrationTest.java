package edu.institution.ims.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ims_late_permission;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {edu.institution.ims.ImsApplication.class,
        LateAttendancePermissionIntegrationTest.ClockConfiguration.class})
class LateAttendancePermissionIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Instant AFTER_NOON = Instant.parse("2026-09-18T07:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);
    private static final String PASSWORD = "TestPassword!23456";
    private static final String SUMMARY = "Completed implementation and checked the daily internship task.";
    private static final String PERMISSIONS = "/api/v1/mentor/attendance/late-permissions";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository studentAccounts;
    @Autowired MentorProfileRepository mentorProfiles;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired AttendanceRepository attendance;
    @Autowired AttendanceEvidenceRepository evidence;
    @Autowired AttendanceVerificationSessionRepository verificationSessions;
    @Autowired AttendanceAbsenceWriter absenceWriter;
    @Autowired LateAttendancePermissionService permissions;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    User admin;
    User mentor;
    User otherMentor;
    User student;
    User secondStudent;
    User unassignedStudent;
    InternshipDetails internship;
    Cookie mentorSession;
    Cookie studentSession;
    String encodedPassword;

    @BeforeEach
    void setup() throws Exception {
        clock.setInstant(AFTER_NOON);
        if (encodedPassword == null) encodedPassword = encoder.encode(PASSWORD);
        admin = user("late.admin@example.edu", Role.ADMIN);
        mentor = mentor("late.mentor@example.edu", "LATE100");
        otherMentor = mentor("late.other.mentor@example.edu", "LATE200");
        student = student("late.student@example.edu", "Late Student", "LATE101");
        secondStudent = student("late.second@example.edu", "Second Student", "LATE102");
        unassignedStudent = student("late.unassigned@example.edu", "Other Student", "LATE103");
        internship = internship(student);
        internship(secondStudent);
        assignments.saveAndFlush(new MentorStudentAssignment(mentor, student, admin, AFTER_NOON));
        assignments.saveAndFlush(new MentorStudentAssignment(mentor, secondStudent, admin, AFTER_NOON));
        assignments.saveAndFlush(new MentorStudentAssignment(otherMentor, unassignedStudent, admin, AFTER_NOON));
        mentorSession = login(mentor);
        studentSession = login(student);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from attendance_submission_audit");
        jdbc.update("delete from late_attendance_permission_audit");
        jdbc.update("delete from late_attendance_permissions");
        evidence.deleteAll();
        attendance.deleteAll();
        verificationSessions.deleteAll();
        assignments.deleteAll();
        internships.deleteAll();
        onboardings.deleteAll();
        mentorProfiles.deleteAll();
        studentAccounts.deleteAll();
        users.deleteAll();
        clock.setInstant(AFTER_NOON);
    }

    @Test
    void onlyAssignedFacultyMentorCanReadAndChangePermissions() throws Exception {
        mvc.perform(get(PERMISSIONS).cookie(mentorSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].studentUserId").value(org.hamcrest.Matchers.containsInAnyOrder(
                        student.getId().intValue(), secondStudent.getId().intValue())))
                .andExpect(jsonPath("$[*].enabled").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));
        mvc.perform(get(PERMISSIONS).cookie(studentSession)).andExpect(status().isForbidden());
        change(studentSession, student, true).andExpect(status().isForbidden());
        change(login(admin), student, true).andExpect(status().isForbidden());
        change(login(otherMentor), student, true).andExpect(status().isForbidden());
        change(mentorSession, unassignedStudent, true).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from late_attendance_permissions", Long.class)).isZero();
    }

    @Test
    void permissionAppliesToServerDayOnlyAndRepeatedGrantAndRevokeAreSafe() throws Exception {
        mvc.perform(post("/api/v1/student/attendance/verification-session").cookie(studentSession)
                        .queryParam("latePermissionEnabled", "true"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENDANCE_WINDOW_CLOSED"));

        mvc.perform(put(PERMISSIONS + "/{id}", student.getId()).cookie(mentorSession)
                        .queryParam("attendanceDate", TODAY.minusDays(1).toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"attendanceDate\":\"1999-01-01\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attendanceDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.authorizedBy").value(mentor.getId()))
                .andExpect(jsonPath("$.authorizedAt").value(AFTER_NOON.toString()));
        clock.setInstant(AFTER_NOON.plusSeconds(60));
        change(mentorSession, student, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizedAt").value(AFTER_NOON.toString()));
        assertThat(jdbc.queryForObject("select count(*) from late_attendance_permissions", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from late_attendance_permission_audit", Long.class)).isEqualTo(1);
        assertThat(permissions.isEnabled(student.getId(), TODAY)).isTrue();
        assertThat(permissions.isEnabled(secondStudent.getId(), TODAY)).isFalse();
        mvc.perform(post("/api/v1/student/attendance/verification-session").cookie(login(secondStudent)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENDANCE_WINDOW_CLOSED"));
        mvc.perform(get("/api/v1/student/attendance/today").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.submissionOpen").value(true))
                .andExpect(jsonPath("$.latePermissionEnabled").value(true));

        change(mentorSession, student, false).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        change(mentorSession, student, false).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        assertThat(jdbc.queryForObject("select count(*) from late_attendance_permission_audit", Long.class)).isEqualTo(2);
        assertThat(permissions.isEnabled(student.getId(), TODAY)).isFalse();
        mvc.perform(post("/api/v1/student/attendance/verification-session").cookie(studentSession))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENDANCE_WINDOW_CLOSED"));

        change(mentorSession, student, true).andExpect(status().isOk());
        clock.setInstant(AFTER_NOON.plus(Duration.ofDays(1)));
        assertThat(permissions.isEnabled(student.getId(), TODAY.plusDays(1))).isFalse();
        mvc.perform(get(PERMISSIONS).cookie(mentorSession).queryParam("attendanceDate", TODAY.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].attendanceDate").value(TODAY.plusDays(1).toString()))
                .andExpect(jsonPath("$[*].enabled").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));
        mvc.perform(get("/api/v1/student/attendance/today").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.submissionOpen").value(false))
                .andExpect(jsonPath("$.latePermissionEnabled").value(false));
        assertThat(jdbc.queryForObject("select count(*) from late_attendance_permissions", Long.class)).isEqualTo(1);
    }

    @Test
    void togglingPermissionPreservesAutomaticAbsenceAndHistoricalRecords() throws Exception {
        assertThat(absenceWriter.createIfMissing(internship.getId(), TODAY)).isTrue();
        assertThat(absenceWriter.createIfMissing(internship.getId(), TODAY.minusDays(1))).isTrue();
        Long currentId = attendance.findByStudentIdAndAttendanceDate(student.getId(), TODAY).orElseThrow().getId();
        Long historyId = attendance.findByStudentIdAndAttendanceDate(student.getId(), TODAY.minusDays(1)).orElseThrow().getId();

        change(mentorSession, student, true).andExpect(status().isOk());
        change(mentorSession, student, false).andExpect(status().isOk());

        assertThat(attendance.findById(currentId).orElseThrow().getSubmissionType()).isEqualTo(AttendanceSubmissionType.AUTO_ABSENT);
        assertThat(attendance.findById(historyId).orElseThrow().getAttendanceResult()).isEqualTo(AttendanceResult.ABSENT);
        assertThat(attendance.countIncludingDeleted(internship.getId(), TODAY)).isEqualTo(1);
        assertThat(evidence.findAll()).isEmpty();
    }

    @Test
    void validLateSubmissionUpdatesSameAbsenceAndRetainsAuditAndAuthorizer() throws Exception {
        assertThat(absenceWriter.createIfMissing(internship.getId(), TODAY)).isTrue();
        AttendanceRecord original = attendance.findByStudentIdAndAttendanceDate(student.getId(), TODAY).orElseThrow();
        clock.setInstant(AFTER_NOON.plusSeconds(600));
        change(mentorSession, student, true).andExpect(status().isOk());
        MockMultipartFile ai = aiEvidence(studentSession, true);

        mvc.perform(remote(studentSession).file(ai).queryParam("attendanceDate", TODAY.minusDays(1).toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(original.getId()))
                .andExpect(jsonPath("$.attendanceDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.attendanceResult").value("PRESENT"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.livenessStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.submittedLate").value(true))
                .andExpect(jsonPath("$.serverSubmittedAt").value(clock.instant().toString()));

        AttendanceRecord updated = attendance.findById(original.getId()).orElseThrow();
        assertThat(updated.getAutomaticAbsentAt()).isEqualTo(AFTER_NOON);
        assertThat(updated.getCreatedAt()).isEqualTo(AFTER_NOON);
        assertThat(updated.isSubmittedLate()).isTrue();
        assertThat(updated.getLateAuthorizedBy().getId()).isEqualTo(mentor.getId());
        assertThat(updated.getLateAuthorizedAt()).isEqualTo(AFTER_NOON.plusSeconds(600));
        assertThat(attendance.countIncludingDeleted(internship.getId(), TODAY)).isEqualTo(1);
        assertThat(evidence.findByAttendanceId(original.getId())).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from attendance_submission_audit where attendance_id = ?",
                Long.class, original.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select attendance_result from attendance_submission_audit "
                        + "where attendance_id = ? and event_type = 'AUTO_ABSENT'", String.class, original.getId()))
                .isEqualTo("ABSENT");
        assertThat(jdbc.queryForObject("select event_at from attendance_submission_audit "
                        + "where attendance_id = ? and event_type = 'AUTO_ABSENT'", java.sql.Timestamp.class, original.getId())
                .toInstant()).isEqualTo(AFTER_NOON);
        assertThat(jdbc.queryForObject("select verification_status from attendance_submission_audit "
                        + "where attendance_id = ? and event_type = 'LATE_SUBMISSION'", String.class, original.getId()))
                .isEqualTo("VERIFIED");
        assertThat(jdbc.queryForObject("select authorized_by from attendance_submission_audit "
                        + "where attendance_id = ? and event_type = 'LATE_SUBMISSION'", Long.class, original.getId()))
                .isEqualTo(mentor.getId());

        change(mentorSession, student, false).andExpect(status().isOk());
        assertThat(attendance.findById(original.getId()).orElseThrow().isSubmittedLate()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from attendance_submission_audit where attendance_id = ?",
                Long.class, original.getId())).isEqualTo(2);
        change(mentorSession, student, true).andExpect(status().isOk());
        mvc.perform(remote(studentSession)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_ALREADY_SUBMITTED"));
    }

    @Test
    void permissionDoesNotBypassInvalidOrForeignVerificationSessions() throws Exception {
        assertThat(absenceWriter.createIfMissing(internship.getId(), TODAY)).isTrue();
        Long recordId = attendance.findByStudentIdAndAttendanceDate(student.getId(), TODAY).orElseThrow().getId();
        change(mentorSession, student, true).andExpect(status().isOk());
        change(mentorSession, secondStudent, true).andExpect(status().isOk());
        MockMultipartFile foreign = aiEvidence(login(secondStudent), true);
        mvc.perform(remote(studentSession).file(foreign)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_SESSION_STUDENT_MISMATCH"));
        String invalidPayload = json.writeValueAsString(Map.of("sessionId", "00000000-0000-0000-0000-000000000000",
                "challenge", "BLINK", "faceCount", 1, "qualityPassed", true, "livenessPassed", true,
                "modelVersion", "mediapipe-face-landmarker@1.0.1/test"));
        mvc.perform(remote(studentSession).file(new MockMultipartFile("aiEvidence", "ai-evidence.json",
                        "application/json", invalidPayload.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AI_SESSION"));
        assertThat(attendance.findById(recordId).orElseThrow().getSubmissionType()).isEqualTo(AttendanceSubmissionType.AUTO_ABSENT);
        assertThat(evidence.findAll()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from attendance_submission_audit", Long.class)).isZero();
    }

    @Test
    void failedLivenessKeepsExistingUnverifiedSubmissionRules() throws Exception {
        assertThat(absenceWriter.createIfMissing(internship.getId(), TODAY)).isTrue();
        Long recordId = attendance.findByStudentIdAndAttendanceDate(student.getId(), TODAY).orElseThrow().getId();
        change(mentorSession, student, true).andExpect(status().isOk());
        mvc.perform(remote(studentSession).file(aiEvidence(studentSession, false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(recordId))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.livenessStatus").value("FAILED"))
                .andExpect(jsonPath("$.submittedLate").value(true));
        assertThat(attendance.countIncludingDeleted(internship.getId(), TODAY)).isEqualTo(1);
        assertThat(evidence.findByAttendanceId(recordId).orElseThrow().getAiFailureReason())
                .isEqualTo(VerificationReason.EDGE_AI_LIVENESS_FAILED);
    }

    private ResultActions change(Cookie session, User target, boolean enabled) throws Exception {
        return mvc.perform(put(PERMISSIONS + "/{id}", target.getId()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("enabled", enabled))));
    }

    private MockMultipartHttpServletRequestBuilder remote(Cookie session) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/student/attendance/today/remote")
                .file(new MockMultipartFile("photo", "capture.png", "image/png", bytes.toByteArray()));
        request.param("dailyWorkSummary", SUMMARY).param("latitude", "18.5204").param("longitude", "73.8567")
                .cookie(session);
        return request;
    }

    private MockMultipartFile aiEvidence(Cookie session, boolean livenessPassed) throws Exception {
        String response = mvc.perform(post("/api/v1/student/attendance/verification-session").cookie(session))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var issued = json.readTree(response);
        String payload = json.writeValueAsString(Map.of("sessionId", issued.get("sessionId").asText(),
                "challenge", issued.get("challenge").asText(), "faceCount", 1, "qualityPassed", true,
                "livenessPassed", livenessPassed, "modelVersion", "mediapipe-face-landmarker@1.0.1/test"));
        return new MockMultipartFile("aiEvidence", "ai-evidence.json", "application/json", payload.getBytes(StandardCharsets.UTF_8));
    }

    private Cookie login(User user) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest(user.getEmail(), PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }

    private User user(String email, Role role) {
        return users.saveAndFlush(new User(email, encodedPassword, role, true));
    }

    private User mentor(String email, String employeeId) {
        User user = user(email, Role.MENTOR);
        mentorProfiles.saveAndFlush(new MentorProfile(user, "Faculty " + employeeId, employeeId,
                "Computer Engineering", "Professor", "+91 9876543210"));
        return user;
    }

    private User student(String email, String name, String uid) {
        User user = user(email, Role.STUDENT);
        studentAccounts.saveAndFlush(new StudentAccount(user, name, uid, 6));
        return user;
    }

    private InternshipDetails internship(User student) {
        InternshipOnboarding onboarding = onboardings.saveAndFlush(new InternshipOnboarding(student));
        onboarding.markDocumentsComplete();
        onboarding.confirmDiary();
        InternshipDetails details = new InternshipDetails(onboarding);
        details.apply("Late Test Ltd", CompanyType.IT, null, "Company address", "+91 9876543210",
                "Supervisor", "Manager", "+91 9876543211", 2, TODAY.minusDays(5), TODAY.plusDays(5),
                8, InternshipMode.ONLINE, BigDecimal.ZERO, false);
        internships.saveAndFlush(details);
        onboarding.markInternshipDetailsComplete();
        onboardings.saveAndFlush(onboarding);
        return details;
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary MutableClock latePermissionTestClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private volatile Instant instant = AFTER_NOON;
        void setInstant(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
