package edu.institution.ims.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.studentprofile.StudentProfileRepository;
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
        OnlineHybridAttendanceIntegrationTest.FixedClockConfiguration.class})
class OnlineHybridAttendanceIntegrationTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final Instant NOW = Instant.parse("2026-09-18T04:12:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 9, 18); // Friday
    static final String SUMMARY = "Implemented the attendance policy endpoint and verified its integration.";

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
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired HybridWorkScheduleRepository schedules;
    @Autowired AttendanceRepository attendance;
    @Autowired AttendanceEvidenceRepository evidence;
    @Autowired AttendanceVerificationSessionRepository verificationSessions;
    @Autowired PasswordEncoder encoder;

    User admin;
    User mentorA;
    User mentorB;
    User online;
    User onlineInvalid;
    User hybridRemote;
    User hybridOffice;
    User hybridMissing;
    Cookie adminSession;

    @BeforeEach
    void setup() throws Exception {
        clearData();
        admin = user("admin.remote@example.edu", "AdminPassword!23", Role.ADMIN);
        mentorA = mentor("mentor.remote.a@example.edu", "MentorPassword!23", "Dr. Asha", "REM100");
        mentorB = mentor("mentor.remote.b@example.edu", "MentorPassword!24", "Dr. Bharat", "REM200");
        online = student("online.valid@example.edu", "StudentPassword!21", "Online Valid", "R100");
        onlineInvalid = student("online.invalid@example.edu", "StudentPassword!22", "Online Invalid", "R200");
        hybridRemote = student("hybrid.remote@example.edu", "StudentPassword!23", "Hybrid Remote", "R300");
        hybridOffice = student("hybrid.office@example.edu", "StudentPassword!24", "Hybrid Office", "R400");
        hybridMissing = student("hybrid.missing@example.edu", "StudentPassword!25", "Hybrid Missing", "R500");
        internship(online, InternshipMode.ONLINE, "Online Ltd");
        internship(onlineInvalid, InternshipMode.ONLINE, "Online Invalid Ltd");
        internship(hybridRemote, InternshipMode.HYBRID, "Hybrid Remote Ltd");
        internship(hybridOffice, InternshipMode.HYBRID, "Hybrid Office Ltd");
        internship(hybridMissing, InternshipMode.HYBRID, "Hybrid Missing Ltd");
        adminSession = loginCookie(admin.getEmail(), "AdminPassword!23");
    }

    @AfterEach void cleanup() { clearData(); }

    @Test void remoteSubmissionStoresRequiredLocationWithoutWorkplaceOrLivenessDependency() throws Exception {
        Cookie session = loginCookie(online.getEmail(), "StudentPassword!21");
        mvc.perform(multipart("/api/v1/student/attendance/today/remote").file(photo())
                .file(aiEvidence(session,1,true,true)).param("dailyWorkSummary",SUMMARY)
                .param("latitude","18.5204").param("longitude","73.8567").param("accuracy","500").cookie(session))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.evidence.latitude").value(18.5204))
            .andExpect(jsonPath("$.evidence.longitude").value(73.8567)).andExpect(jsonPath("$.evidence.gpsAccuracyMeters").value(500))
            .andExpect(jsonPath("$.evidence.expectedLatitude").isEmpty())
            .andExpect(jsonPath("$.evidence.locationVerificationStatus").value("NOT_APPLICABLE"))
            .andExpect(jsonPath("$.livenessStatus").value("VERIFIED"));
        mvc.perform(get("/api/v1/student/attendance/today").cookie(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.attendance.evidence.latitude").value(18.5204));
        assertThat(locations.findAll()).isEmpty();
    }

    @Test
    void onlinePolicyNeedsNoWorkplaceAndValidLocatedCaptureIsVerified() throws Exception {
        Cookie session = loginCookie(online.getEmail(), "StudentPassword!21");
        mvc.perform(get("/api/v1/student/attendance/today/policy").cookie(session)
                        .queryParam("browserDate", "1999-01-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.internshipMode").value("ONLINE"))
                .andExpect(jsonPath("$.todayWorkMode").value("REMOTE"))
                .andExpect(jsonPath("$.verificationMethod").value("REMOTE"))
                .andExpect(jsonPath("$.serverDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.configurationReady").value(true));

        mvc.perform(multipart("/api/v1/student/attendance/today/remote").file(photo())
                        .file(aiEvidence(session, 1, true, true))
                        .param("dailyWorkSummary", SUMMARY).param("latitude","18.5204").param("longitude","73.8567").param("attendanceDate", "2099-01-01")
                        .param("verificationStatus", "VERIFIED").cookie(session))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.attendanceDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.serverSubmittedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.livenessStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.submissionType").value("REMOTE_CAPTURE"));

        AttendanceRecord record = attendance.findByStudentIdAndAttendanceDate(online.getId(), TODAY).orElseThrow();
        AttendanceEvidence stored = evidence.findByAttendanceId(record.getId()).orElseThrow();
        assertThat(stored.getDailyWorkSummary()).isEqualTo(SUMMARY);
        assertThat(stored.getExpectedWorkMode()).isEqualTo(HybridWorkMode.REMOTE);
        assertThat(stored.getLatitude()).isEqualByComparingTo("18.5204");
        assertThat(stored.getGpsAccuracyMeters()).isNull();
        assertThat(stored.getVerificationReason()).isEqualTo(VerificationReason.EDGE_AI_VERIFIED);
        assertThat(locations.findAll()).isEmpty();

        mvc.perform(multipart("/api/v1/student/attendance/today/remote").file(photo())
                        .param("dailyWorkSummary", SUMMARY).param("latitude","18.5204").param("longitude","73.8567").cookie(session))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENDANCE_ALREADY_SUBMITTED"));
        mvc.perform(get("/api/v1/student/attendance").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$[0].livenessStatus").value("VERIFIED"));
    }

    @Test
    void onlineMissingCameraOrMeaningfulSummaryCannotVerifyOrCreateAttendance() throws Exception {
        Cookie session = loginCookie(onlineInvalid.getEmail(), "StudentPassword!22");
        mvc.perform(multipart("/api/v1/student/attendance/today/remote")
                        .param("dailyWorkSummary", SUMMARY).param("latitude","18.5204").param("longitude","73.8567").cookie(session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMERA_IMAGE_REQUIRED"));
        mvc.perform(multipart("/api/v1/student/attendance/today/remote").file(photo())
                        .param("dailyWorkSummary", "   ").cookie(session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DAILY_WORK_SUMMARY"));
        mvc.perform(multipart("/api/v1/student/attendance/today/remote").file(photo())
                        .param("dailyWorkSummary", "Too short").cookie(session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DAILY_WORK_SUMMARY"));
        assertThat(attendance.findByStudentIdAndAttendanceDate(onlineInvalid.getId(), TODAY)).isEmpty();
    }

    @Test
    void adminScheduleControlsHybridOfficeAndRemotePoliciesAndMentorView() throws Exception {
        saveSchedule(hybridRemote, "REMOTE");
        saveSchedule(hybridOffice, "OFFICE");
        assignments.saveAndFlush(new MentorStudentAssignment(mentorA, hybridRemote, admin, NOW));
        assignments.saveAndFlush(new MentorStudentAssignment(mentorA, hybridOffice, admin, NOW));

        Cookie remoteSession = loginCookie(hybridRemote.getEmail(), "StudentPassword!23");
        Cookie officeSession = loginCookie(hybridOffice.getEmail(), "StudentPassword!24");
        Cookie mentorSession = loginCookie(mentorA.getEmail(), "MentorPassword!23");
        mvc.perform(put("/api/v1/admin/students/{id}/internship/hybrid-schedule", hybridRemote.getId())
                        .cookie(remoteSession).contentType(MediaType.APPLICATION_JSON).content(scheduleJson("OFFICE")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/students/{id}/internship/hybrid-schedule", hybridRemote.getId())
                        .cookie(mentorSession).contentType(MediaType.APPLICATION_JSON).content(scheduleJson("OFFICE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/internship/hybrid-schedule").cookie(remoteSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].workMode").value("REMOTE"));
        mvc.perform(get("/api/v1/mentor/students/{id}/hybrid-schedule", hybridRemote.getId()).cookie(mentorSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].workMode").value("REMOTE"));
        mvc.perform(get("/api/v1/mentor/students/{id}/hybrid-schedule", hybridRemote.getId())
                        .cookie(loginCookie(mentorB.getEmail(), "MentorPassword!24")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/student/attendance/today/policy").cookie(remoteSession)
                        .queryParam("todayWorkMode", "OFFICE").queryParam("browserDate", "2026-09-21"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.todayWorkMode").value("REMOTE"))
                .andExpect(jsonPath("$.verificationMethod").value("REMOTE"));
        mvc.perform(get("/api/v1/student/attendance/today/policy").cookie(officeSession)
                        .queryParam("todayWorkMode", "REMOTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.todayWorkMode").value("OFFICE"))
                .andExpect(jsonPath("$.verificationMethod").value("PHYSICAL"));

        mvc.perform(multipart("/api/v1/student/attendance/today/physical").file(photo())
                        .param("latitude", "18.5204").param("longitude", "73.8567").param("accuracy", "10")
                        .cookie(remoteSession)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMOTE_ATTENDANCE_REQUIRED"));
        mvc.perform(multipart("/api/v1/student/attendance/today/remote").file(photo())
                        .param("dailyWorkSummary", SUMMARY).param("latitude","18.5204").param("longitude","73.8567").param("todayWorkMode", "REMOTE").cookie(officeSession))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHYSICAL_ATTENDANCE_REQUIRED"));

        mvc.perform(multipart("/api/v1/student/attendance/today/remote").file(photo())
                        .file(aiEvidence(remoteSession, 1, true, true))
                        .param("dailyWorkSummary", SUMMARY).param("latitude","18.5204").param("longitude","73.8567").cookie(remoteSession))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        mvc.perform(multipart("/api/v1/student/attendance/today/physical").file(photo())
                        .file(aiEvidence(officeSession, 1, true, true)).param("latitude","18.5204").param("longitude","73.8567")
                        .cookie(officeSession)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.livenessStatus").value("VERIFIED"));
        assertThat(locations.findAll()).isEmpty();

        mvc.perform(get("/api/v1/mentor/attendance/today").cookie(mentorSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.uid == 'R300')].todayWorkMode").value("REMOTE"))
                .andExpect(jsonPath("$[?(@.uid == 'R300')].todayAttendance.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$[?(@.uid == 'R300')].todayAttendance.livenessStatus").value("VERIFIED"))
                .andExpect(jsonPath("$[?(@.uid == 'R400')].todayWorkMode").value("OFFICE"))
                .andExpect(jsonPath("$[?(@.uid == 'R400')].todayAttendance.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$[?(@.uid == 'R400')].todayAttendance.livenessStatus").value("VERIFIED"));
        mvc.perform(get("/api/v1/mentor/attendance/today")
                        .cookie(loginCookie(mentorB.getEmail(), "MentorPassword!24")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void hybridMissingScheduleCannotFalselyVerifyAndCanSubmitBasicUnverified() throws Exception {
        Cookie session = loginCookie(hybridMissing.getEmail(), "StudentPassword!25");
        mvc.perform(get("/api/v1/student/attendance/today/policy").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configurationReady").value(false))
                .andExpect(jsonPath("$.verificationMethod").value("UNAVAILABLE"));
        mvc.perform(multipart("/api/v1/student/attendance/today/remote").file(photo())
                        .param("dailyWorkSummary", SUMMARY).param("latitude","18.5204").param("longitude","73.8567").cookie(session))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HYBRID_SCHEDULE_NOT_CONFIGURED"));
        mvc.perform(post("/api/v1/student/attendance/today").param("latitude","18.5204").param("longitude","73.8567").cookie(session)
                        .queryParam("attendanceDate", "1999-01-01").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationStatus\":\"VERIFIED\",\"todayWorkMode\":\"REMOTE\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.attendanceDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.livenessStatus").value("NOT_PERFORMED"));
    }

    @Test void remoteMissingOrPartialCoordinatesCannotCreateAttendance() throws Exception {
        Cookie session = loginCookie(online.getEmail(), "StudentPassword!21");
        for (String latitude : new String[]{"", "18.5", "91"}) {
            var request = multipart("/api/v1/student/attendance/today/remote").file(photo())
                .param("dailyWorkSummary", SUMMARY).cookie(session);
            if (!latitude.isEmpty()) request.param("latitude", latitude);
            mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SUBMISSION_LOCATION_REQUIRED"));
        }
        assertThat(attendance.findAll()).isEmpty(); assertThat(evidence.findAll()).isEmpty();
    }

    private void saveSchedule(User student, String mode) throws Exception {
        mvc.perform(put("/api/v1/admin/students/{id}/internship/hybrid-schedule", student.getId())
                        .cookie(adminSession).contentType(MediaType.APPLICATION_JSON).content(scheduleJson(mode)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].dayOfWeek").value("FRIDAY"))
                .andExpect(jsonPath("$.entries[0].workMode").value(mode));
    }

    private String scheduleJson(String mode) throws Exception {
        return json.writeValueAsString(Map.of("entries", List.of(Map.of("dayOfWeek", "FRIDAY", "workMode", mode))));
    }

    private MockMultipartFile photo() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("photo", "capture.png", "image/png", bytes.toByteArray());
    }

    private MockMultipartFile aiEvidence(Cookie session, int faceCount,
            boolean qualityPassed, boolean livenessPassed) throws Exception {
        String body = mvc.perform(post("/api/v1/student/attendance/verification-session").cookie(session))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var issued = json.readTree(body);
        String payload = json.writeValueAsString(Map.of(
                "sessionId", issued.get("sessionId").asText(),
                "challenge", issued.get("challenge").asText(),
                "faceCount", faceCount,
                "qualityPassed", qualityPassed,
                "livenessPassed", livenessPassed,
                "modelVersion", "mediapipe-face-landmarker@1.0.1/test"));
        return new MockMultipartFile("aiEvidence", "ai-evidence.json", "application/json", payload.getBytes());
    }

    private User user(String email, String password, Role role) {
        return users.saveAndFlush(new User(email, encoder.encode(password), role, true));
    }

    private User mentor(String email, String password, String name, String employeeId) {
        User mentor = user(email, password, Role.MENTOR);
        mentorProfiles.saveAndFlush(new MentorProfile(mentor, name, employeeId, "Computer Engineering", "Professor", "+91 9876543210"));
        return mentor;
    }

    private User student(String email, String password, String name, String uid) {
        User student = user(email, password, Role.STUDENT);
        studentAccounts.saveAndFlush(new StudentAccount(student, name, uid, 6));
        return student;
    }

    private InternshipDetails internship(User student, InternshipMode mode, String company) {
        InternshipOnboarding onboarding = onboardings.saveAndFlush(new InternshipOnboarding(student));
        onboarding.markDocumentsComplete(); onboarding.confirmDiary();
        InternshipDetails details = new InternshipDetails(onboarding);
        details.apply(company, CompanyType.IT, null, "Company address", "+91 9876543210", "Supervisor", "Manager",
                "+91 9876543211", 2, TODAY.minusDays(10), TODAY.plusDays(10), 8, mode, BigDecimal.ZERO, false);
        internships.saveAndFlush(details); onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
        return details;
    }

    private Cookie loginCookie(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }

    private void clearData() {
        jdbc.update("delete from workplace_review_audit");
        evidence.deleteAll(); attendance.deleteAll(); verificationSessions.deleteAll(); locations.deleteAll(); schedules.deleteAll(); assignments.deleteAll();
        internships.deleteAll(); onboardings.deleteAll(); studentProfiles.deleteAll(); mentorProfiles.deleteAll();
        studentAccounts.deleteAll(); users.deleteAll();
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedAttendanceClock() { return Clock.fixed(NOW, ZONE); }
    }
}
