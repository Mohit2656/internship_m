package edu.institution.ims.attendance;

import com.fasterxml.jackson.databind.*;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {edu.institution.ims.ImsApplication.class,
        EdgeAiAttendanceIntegrationTest.FixedClockConfiguration.class})
class EdgeAiAttendanceIntegrationTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final Instant NOW = Instant.parse("2026-09-18T04:12:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 9, 18);
    static final String PASSWORD = "StudentPassword!21";
    static final String SUMMARY = "Completed the assigned development tasks and documented today's progress.";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository studentAccounts;
    @Autowired StudentProfileRepository studentProfiles;
    @Autowired MentorProfileRepository mentorProfiles;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired InternshipLocationRepository locations;
    @Autowired InternshipLocationService locationService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired HybridWorkScheduleRepository schedules;
    @Autowired AttendanceRepository attendance;
    @Autowired AttendanceEvidenceRepository evidence;
    @Autowired AttendanceVerificationSessionRepository verificationSessions;
    @Autowired PasswordEncoder encoder;

    User admin;
    Cookie adminSession;

    @BeforeEach
    void setup() throws Exception {
        clearData();
        admin = users.saveAndFlush(new User("admin.edge@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        adminSession = login(admin.getEmail(), "AdminPassword!23");
    }

    @AfterEach void cleanup() { clearData(); }

    @Test
    void serverIssuesBoundExpiringAllowedChallengeAndRejectsCrossStudentAndReplay() throws Exception {
        User first = studentWithInternship("edge.first@example.edu", "E100", InternshipMode.ONLINE);
        User second = studentWithInternship("edge.second@example.edu", "E200", InternshipMode.ONLINE);
        Cookie firstCookie = login(first.getEmail(), PASSWORD);
        Cookie secondCookie = login(second.getEmail(), PASSWORD);

        mvc.perform(post("/api/v1/student/attendance/verification-session"))
                .andExpect(status().isUnauthorized());
        JsonNode issued = issue(firstCookie, second.getId());
        AttendanceVerificationSession stored = verificationSessions.findById(issued.get("sessionId").asText()).orElseThrow();
        assertThat(stored.getStudent().getId()).isEqualTo(first.getId());
        assertThat(stored.getAttendanceDate()).isEqualTo(TODAY);
        assertThat(stored.getChallengeType()).isIn(AiChallengeType.values());
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plusSeconds(180));

        mvc.perform(remote(secondCookie, aiEvidence(issued, 1, true, true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_SESSION_STUDENT_MISMATCH"));

        MockMultipartFile accepted = aiEvidence(issued, 1, true, true);
        mvc.perform(remote(firstCookie, accepted).param("verificationStatus", "VERIFIED")
                        .param("attendanceDate", "1999-01-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendanceDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        mvc.perform(remote(firstCookie, aiEvidence(issued, 1, true, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_SESSION_ALREADY_CONSUMED"));
    }

    @Test
    void faceQualityAndLivenessFailuresRemainPresentUnverified() throws Exception {
        assertRemoteFailure("missing", null, VerificationReason.EDGE_AI_EVIDENCE_MISSING);
        assertRemoteFailure("noface", new AiFlags(0, true, true), VerificationReason.EDGE_AI_FACE_NOT_DETECTED);
        assertRemoteFailure("multiple", new AiFlags(2, true, true), VerificationReason.EDGE_AI_MULTIPLE_FACES);
        assertRemoteFailure("quality", new AiFlags(1, false, true), VerificationReason.EDGE_AI_QUALITY_FAILED);
        assertRemoteFailure("liveness", new AiFlags(1, true, false), VerificationReason.EDGE_AI_LIVENESS_FAILED);
        assertThat(attendance.findAll()).allSatisfy(record -> {
            assertThat(record.getAttendanceResult()).isEqualTo(AttendanceResult.PRESENT);
            assertThat(record.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        });
    }

    @Test
    void expiredChallengeCannotVerifyButCreatesPresentUnverified() throws Exception {
        User student = studentWithInternship("edge.expired@example.edu", "E300", InternshipMode.ONLINE);
        InternshipDetails internship = internships.findByOnboardingStudentId(student.getId()).orElseThrow();
        AttendanceVerificationSession expired = verificationSessions.saveAndFlush(new AttendanceVerificationSession(
                UUID.randomUUID().toString(), student, internship, TODAY, AiChallengeType.BLINK,
                NOW.minusSeconds(240), NOW.minusSeconds(60)));
        JsonNode session = json.valueToTree(Map.of("sessionId", expired.getId(), "challenge", "BLINK"));
        Cookie cookie = login(student.getEmail(), PASSWORD);

        mvc.perform(remote(cookie, aiEvidence(session, 1, true, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendanceResult").value("PRESENT"))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));
        AttendanceEvidence stored = storedEvidence(student);
        assertThat(stored.getAiFailureReason()).isEqualTo(VerificationReason.EDGE_AI_SESSION_EXPIRED);
        assertThat(verificationSessions.findById(expired.getId()).orElseThrow().getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    void physicalVerificationRequiresBothEdgeAiAndPhysicalEvidence() throws Exception {
        User bothPass = studentWithInternship("edge.office.pass@example.edu", "E400", InternshipMode.OFFICE_REPORTING);
        User aiFail = studentWithInternship("edge.office.ai-fail@example.edu", "E500", InternshipMode.OFFICE_REPORTING);
        User locationFail = studentWithInternship("edge.office.location-fail@example.edu", "E600", InternshipMode.OFFICE_REPORTING);
        confirmLocation(bothPass); confirmLocation(aiFail); confirmLocation(locationFail);

        submitPhysical(bothPass, new AiFlags(1, true, true), "18.5204", "VERIFIED");
        submitPhysical(aiFail, new AiFlags(1, true, false), "18.5204", "UNVERIFIED");
        submitPhysical(locationFail, new AiFlags(1, true, true), "18.5404", "UNVERIFIED");
        assertThat(storedEvidence(aiFail).getAiFailureReason()).isEqualTo(VerificationReason.EDGE_AI_LIVENESS_FAILED);
        assertThat(storedEvidence(locationFail).getVerificationReason()).isEqualTo(VerificationReason.OUTSIDE_GEOFENCE);
    }

    private void assertRemoteFailure(String suffix, AiFlags flags, VerificationReason reason) throws Exception {
        User student = studentWithInternship("edge." + suffix + "@example.edu", "R" + suffix, InternshipMode.ONLINE);
        Cookie cookie = login(student.getEmail(), PASSWORD);
        MockMultipartFile ai = flags == null ? null : aiEvidence(issue(cookie, null),
                flags.faceCount(), flags.quality(), flags.liveness());
        var request = multipart("/api/v1/student/attendance/today/remote");
        request.file(photo());
        request.param("dailyWorkSummary", SUMMARY);
        if (ai != null) request.file(ai);
        request.cookie(cookie);
        mvc.perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendanceResult").value("PRESENT"))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));
        assertThat(storedEvidence(student).getAiFailureReason()).isEqualTo(reason);
    }

    private void submitPhysical(User student, AiFlags flags, String latitude, String expected) throws Exception {
        Cookie cookie = login(student.getEmail(), PASSWORD);
        mvc.perform(multipart("/api/v1/student/attendance/today/physical").file(photo())
                        .file(aiEvidence(issue(cookie, null), flags.faceCount(), flags.quality(), flags.liveness()))
                        .param("latitude", latitude).param("longitude", "73.8567").param("accuracy", "10")
                        .cookie(cookie))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.verificationStatus").value(expected));
    }

    private JsonNode issue(Cookie cookie, Long ignoredStudentId) throws Exception {
        var request = post("/api/v1/student/attendance/verification-session")
                .queryParam("studentId", ignoredStudentId == null ? "999999" : ignoredStudentId.toString())
                .queryParam("browserDate", "1999-01-01").cookie(cookie);
        String body = mvc.perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.challenge").value(org.hamcrest.Matchers.isIn(List.of(
                        "BLINK", "TURN_HEAD_LEFT", "TURN_HEAD_RIGHT"))))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private MockMultipartFile aiEvidence(JsonNode session, int faceCount,
            boolean qualityPassed, boolean livenessPassed) throws Exception {
        String payload = json.writeValueAsString(Map.of(
                "sessionId", session.get("sessionId").asText(),
                "challenge", session.get("challenge").asText(),
                "faceCount", faceCount,
                "qualityPassed", qualityPassed,
                "livenessPassed", livenessPassed,
                "modelVersion", "mediapipe-face-landmarker@1.0.1/test"));
        return new MockMultipartFile("aiEvidence", "ai-evidence.json", "application/json", payload.getBytes());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder remote(
            Cookie cookie, MockMultipartFile ai) throws Exception {
        return multipart("/api/v1/student/attendance/today/remote").file(photo()).file(ai)
                .param("dailyWorkSummary", SUMMARY).cookie(cookie);
    }

    private AttendanceEvidence storedEvidence(User student) {
        AttendanceRecord record = attendance.findByStudentIdAndAttendanceDate(student.getId(), TODAY).orElseThrow();
        return evidence.findByAttendanceId(record.getId()).orElseThrow();
    }

    private void confirmLocation(User student) throws Exception {
        var mentor=users.saveAndFlush(new User("edge.mentor."+student.getId()+"@example.edu",encoder.encode("MentorPassword!23"),Role.MENTOR,true));
        if(assignments.findByStudentIdAndActiveTrue(student.getId()).isEmpty()) assignments.saveAndFlush(new MentorStudentAssignment(mentor,student,admin,NOW));
        locationService.registerSelectedPlace(internships.findByOnboardingStudentId(student.getId()).orElseThrow(),new edu.institution.ims.internship.dto.RegisteredWorkplaceRequest("test-branch-"+student.getId(),"Actual test workplace address",new BigDecimal("18.520400"),new BigDecimal("73.856700")));
        var current=locationService.getForAdmin(student.getId());
        locationService.review(student.getId(),edu.institution.ims.security.UserPrincipal.from(mentor),new WorkplaceReview(current.revision(),WorkplaceReview.Decision.APPROVE,null));
        locationService.review(student.getId(),edu.institution.ims.security.UserPrincipal.from(admin),new WorkplaceReview(current.revision(),WorkplaceReview.Decision.APPROVE,null));
    }

    private User studentWithInternship(String email, String uid, InternshipMode mode) {
        User student = users.saveAndFlush(new User(email, encoder.encode(PASSWORD), Role.STUDENT, true));
        studentAccounts.saveAndFlush(new StudentAccount(student, "Edge Student", uid, 6));
        InternshipOnboarding onboarding = onboardings.saveAndFlush(new InternshipOnboarding(student));
        onboarding.markDocumentsComplete(); onboarding.confirmDiary();
        InternshipDetails details = new InternshipDetails(onboarding);
        details.apply("Edge Ltd", CompanyType.IT, null, "Company address", "+91 9876543210", "Supervisor",
                "Manager", "+91 9876543211", 2, TODAY.minusDays(10), TODAY.plusDays(10), 8, mode,
                BigDecimal.ZERO, false);
        internships.saveAndFlush(details); onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
        return student;
    }

    private Cookie login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }

    private MockMultipartFile photo() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("photo", "capture.png", "image/png", bytes.toByteArray());
    }

    private void clearData() {
        jdbc.update("delete from workplace_review_audit");
        evidence.deleteAll(); attendance.deleteAll(); verificationSessions.deleteAll(); locations.deleteAll();
        schedules.deleteAll(); assignments.deleteAll(); internships.deleteAll(); onboardings.deleteAll();
        studentProfiles.deleteAll(); mentorProfiles.deleteAll(); studentAccounts.deleteAll(); users.deleteAll();
    }

    private record AiFlags(int faceCount, boolean quality, boolean liveness) {}

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedAttendanceClock() { return Clock.fixed(NOW, ZONE); }
    }
}
