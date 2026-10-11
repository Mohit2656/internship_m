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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {edu.institution.ims.ImsApplication.class, PhysicalAttendanceIntegrationTest.FixedClockConfiguration.class})
class PhysicalAttendanceIntegrationTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final Instant NOW = Instant.parse("2026-09-18T04:12:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 9, 18);
    static final BigDecimal COMPANY_LATITUDE = new BigDecimal("18.520400");
    static final BigDecimal COMPANY_LONGITUDE = new BigDecimal("73.856700");

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
    @Autowired AttendanceRepository attendance;
    @Autowired AttendanceEvidenceRepository evidence;
    @Autowired AttendanceVerificationSessionRepository verificationSessions;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    User admin;
    User mentorA;
    User mentorB;
    User verifiedStudent;
    User outsideStudent;
    User inaccurateStudent;
    User noGpsStudent;
    User pendingStudent;
    User onlineStudent;
    Cookie adminSession;

    @BeforeEach
    void setup() throws Exception {
        clearData();
        admin = user("admin.physical@example.edu", "AdminPassword!23", Role.ADMIN);
        mentorA = mentor("mentor.physical.a@example.edu", "MentorPassword!23", "Dr. Asha", "PHY100");
        mentorB = mentor("mentor.physical.b@example.edu", "MentorPassword!24", "Dr. Bharat", "PHY200");
        verifiedStudent = student("physical.verified@example.edu", "StudentPassword!21", "Verified Student", "P100");
        outsideStudent = student("physical.outside@example.edu", "StudentPassword!22", "Outside Student", "P200");
        inaccurateStudent = student("physical.inaccurate@example.edu", "StudentPassword!23", "Inaccurate Student", "P300");
        noGpsStudent = student("physical.nogps@example.edu", "StudentPassword!24", "No GPS Student", "P400");
        pendingStudent = student("physical.pending@example.edu", "StudentPassword!25", "Pending Student", "P500");
        onlineStudent = student("physical.online@example.edu", "StudentPassword!26", "Online Student", "P600");
        internship(verifiedStudent, InternshipMode.OFFICE_REPORTING, "Verified Ltd");
        internship(outsideStudent, InternshipMode.OFFICE_REPORTING, "Outside Ltd");
        internship(inaccurateStudent, InternshipMode.OFFICE_REPORTING, "Accuracy Ltd");
        internship(noGpsStudent, InternshipMode.OFFICE_REPORTING, "No GPS Ltd");
        internship(pendingStudent, InternshipMode.OFFICE_REPORTING, "Pending Ltd");
        internship(onlineStudent, InternshipMode.ONLINE, "Online Ltd");
        adminSession = loginCookie(admin.getEmail(), "AdminPassword!23");
    }

    @AfterEach
    void cleanup() { clearData(); }

    @Test
    void officeLivenessWithoutWorkplaceOrGpsProducesVerifiedAttendanceAndMentorView() throws Exception {
        assignments.saveAndFlush(new MentorStudentAssignment(mentorA, verifiedStudent, admin, NOW));

        Cookie studentSession = loginCookie(verifiedStudent.getEmail(), "StudentPassword!21");
        mvc.perform(multipart("/api/v1/student/attendance/today/physical")
                        .file(photo()).file(aiEvidence(studentSession, 1, true, true))
                        .param("attendanceDate", "1999-01-01").cookie(studentSession))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendanceDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.serverSubmittedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.attendanceResult").value("PRESENT"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.livenessStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.verificationBasis").value("LIVENESS"))
                .andExpect(jsonPath("$.evidence.locationCheckPassed").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.evidence.latitude").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.evidence.longitude").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.submissionType").value("PHYSICAL_CAPTURE"));

        AttendanceRecord record = attendance.findByStudentIdAndAttendanceDate(verifiedStudent.getId(), TODAY).orElseThrow();
        AttendanceEvidence stored = evidence.findByAttendanceId(record.getId()).orElseThrow();
        assertThat(stored.getVerificationReason()).isEqualTo(VerificationReason.EDGE_AI_VERIFIED);
        assertThat(stored.getDistanceMeters()).isNull();
        assertThat(stored.getLocationCheckPassed()).isNull();
        assertThat(stored.getExpectedLatitude()).isNull();
        assertThat(locations.findAll()).isEmpty();
        assertThat(stored.isCameraCaptureUsed()).isTrue();
        assertThat(stored.getImageSizeBytes()).isPositive();
        assertThat(stored.isAiFaceDetected()).isTrue();
        assertThat(stored.isAiSingleFace()).isTrue();
        assertThat(stored.isAiQualityPassed()).isTrue();
        assertThat(stored.isAiLivenessPassed()).isTrue();

        mvc.perform(get("/api/v1/mentor/attendance/today")
                        .cookie(loginCookie(mentorA.getEmail(), "MentorPassword!23")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].uid").value("P100"))
                .andExpect(jsonPath("$[0].todayAttendance.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$[0].todayAttendance.livenessStatus").value("VERIFIED"));
        mvc.perform(get("/api/v1/mentor/attendance/today")
                        .cookie(loginCookie(mentorB.getEmail(), "MentorPassword!24")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/student/attendance").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$[0].livenessStatus").value("VERIFIED"));

        mvc.perform(multipart("/api/v1/student/attendance/today/physical").file(photo())
                        .param("latitude", "18.520450").param("longitude", "73.856750")
                        .param("accuracy", "10").cookie(studentSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_ALREADY_SUBMITTED"));
        assertThat(attendance.findAll()).hasSize(1);
        assertThat(evidence.findAll()).hasSize(1);
    }

    @Test
    void poorGpsAccuracyIsEvidenceOnlyAndCannotOverrideSuccessfulLiveness() throws Exception {
        saveLocation(verifiedStudent, true);
        Cookie session = loginCookie(verifiedStudent.getEmail(), "StudentPassword!21");
        mvc.perform(multipart("/api/v1/student/attendance/today/physical").file(photo())
                        .file(aiEvidence(session, 1, true, true))
                        .param("latitude", "18.520400").param("longitude", "73.856700")
                        .param("accuracy", "106").cookie(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendanceResult").value("PRESENT"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.livenessStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.evidence.verificationReason").value("EDGE_AI_VERIFIED"))
                .andExpect(jsonPath("$.evidence.gpsAccuracyMeters").value(106))
                .andExpect(jsonPath("$.evidence.locationCheckPassed").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.evidence.distanceMeters").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.evidence.latitude").value(18.5204))
                .andExpect(jsonPath("$.evidence.longitude").value(73.8567));
        mvc.perform(get("/api/v1/mentor/attendance/today")
                        .cookie(loginCookie(mentorA.getEmail(), "MentorPassword!23")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].todayAttendance.attendanceResult").value("PRESENT"))
                .andExpect(jsonPath("$[0].todayAttendance.livenessStatus").value("VERIFIED"))
                .andExpect(jsonPath("$[0].todayAttendance.evidence.gpsAccuracyMeters").value(106))
                .andExpect(jsonPath("$[0].todayAttendance.evidence.verificationReason").value("EDGE_AI_VERIFIED"));
        assertThat(evidence.findAll()).hasSize(1);
        assertThat(evidence.findAll().get(0).isCameraCaptureUsed()).isTrue();
    }

    @Test
    void failedLivenessCannotBeVerifiedByGpsOrApprovedWorkplace() throws Exception {
        saveLocation(verifiedStudent, true);
        Cookie session = loginCookie(verifiedStudent.getEmail(), "StudentPassword!21");
        mvc.perform(multipart("/api/v1/student/attendance/today/physical").file(photo())
                        .file(aiEvidence(session, 1, true, false))
                        .param("latitude", "18.520400").param("longitude", "73.856700")
                        .param("accuracy", "10").cookie(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendanceResult").value("PRESENT"))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.livenessStatus").value("FAILED"))
                .andExpect(jsonPath("$.evidence.locationCheckPassed").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.evidence.verificationReason").value("EDGE_AI_LIVENESS_FAILED"));
    }

    @Test
    void locationsAndApprovalDoNotAffectSuccessfulLivenessOrAttendanceResult() throws Exception {
        saveLocation(outsideStudent, true);
        saveLocation(inaccurateStudent, true);
        saveLocation(noGpsStudent, true);
        saveLocation(pendingStudent, false);

        submitPhysical(outsideStudent, "StudentPassword!22", "18.540400", "73.856700", "10");
        submitPhysical(inaccurateStudent, "StudentPassword!23", "18.520400", "73.856700", "500");
        submitWithoutGps(noGpsStudent, "StudentPassword!24");
        submitPhysical(pendingStudent, "StudentPassword!25", "18.520400", "73.856700", "10");
        mvc.perform(multipart("/api/v1/student/attendance/today/physical").file(photo())
                        .param("latitude", "18.520400").param("longitude", "73.856700").param("accuracy", "10")
                        .cookie(loginCookie(onlineStudent.getEmail(), "StudentPassword!26")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REMOTE_ATTENDANCE_REQUIRED"));

        assertThat(attendance.findAll()).allSatisfy(record -> {
            assertThat(record.getAttendanceResult()).isEqualTo(AttendanceResult.PRESENT);
            assertThat(record.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        });
        mvc.perform(get("/api/v1/mentor/attendance/today")
                        .cookie(loginCookie(mentorA.getEmail(), "MentorPassword!23")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].todayAttendance.verificationStatus")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("VERIFIED"))))
                .andExpect(jsonPath("$[*].todayAttendance.livenessStatus")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("VERIFIED"))))
                .andExpect(jsonPath("$[*].uid").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("P500"))));
    }

    @Test
    void onlyAdminCanConfirmAndLocationValidationIsEnforced() throws Exception {
        Cookie studentSession = loginCookie(pendingStudent.getEmail(), "StudentPassword!25");
        Cookie mentorSession = loginCookie(mentorA.getEmail(), "MentorPassword!23");
        mvc.perform(put("/api/v1/student/internship/location").cookie(studentSession).contentType(MediaType.APPLICATION_JSON).content(locationJson("18.5204","73.8567","200"))).andExpect(status().isConflict());
        saveLocation(pendingStudent,false);
        mvc.perform(post("/api/v1/admin/students/{id}/internship/location/confirm",pendingStudent.getId()).cookie(studentSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/students/{id}/internship/location/confirm",pendingStudent.getId()).cookie(adminSession)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/students/{id}/internship/location/review",pendingStudent.getId()).cookie(adminSession).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1,\"decision\":\"APPROVE\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(put("/api/v1/admin/students/{id}/internship/location",outsideStudent.getId()).cookie(adminSession).contentType(MediaType.APPLICATION_JSON).content(locationJson("91","73","200"))).andExpect(status().isBadRequest());
    }

    @Test
    void legacyInvalidCoordinateInputsAreIgnoredForNewLivenessAttendance() throws Exception {
        submitPhysical(verifiedStudent,"StudentPassword!21","91","73","10");
        assertThat(attendance.findAll()).hasSize(1);assertThat(attendance.findAll().get(0).getAttendanceResult()).isEqualTo(AttendanceResult.PRESENT);
        assertThat(evidence.findAll().get(0).getLatitude()).isNull();
    }

    @Test
    void missingLivenessCannotBeVerifiedByClientStatusGpsOrApprovedWorkplace() throws Exception {
        saveLocation(verifiedStudent, true);
        Cookie session = loginCookie(verifiedStudent.getEmail(), "StudentPassword!21");
        mvc.perform(multipart("/api/v1/student/attendance/today/physical").file(photo())
                        .param("latitude", "18.520400").param("longitude", "73.856700")
                        .param("accuracy", "1").param("verificationStatus", "VERIFIED")
                        .param("livenessStatus", "VERIFIED").cookie(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendanceResult").value("PRESENT"))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.livenessStatus").value("NOT_PERFORMED"))
                .andExpect(jsonPath("$.evidence.verificationReason").value("EDGE_AI_EVIDENCE_MISSING"));
        mvc.perform(get("/api/v1/mentor/attendance/today")
                        .cookie(loginCookie(mentorA.getEmail(), "MentorPassword!23")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].todayAttendance.livenessStatus").value("NOT_PERFORMED"));
        assertThat(evidence.findAll().get(0).isAiLivenessPassed()).isFalse();
    }

    @Test
    void historicalGpsStatusesAndEvidenceArePreservedWithoutClaimingLivenessVerification() throws Exception {
        saveLocation(verifiedStudent, true);
        saveLocation(outsideStudent, true);
        var internship = internships.findByOnboardingStudentId(verifiedStudent.getId()).orElseThrow();
        var location = locations.findByInternshipId(internship.getId()).orElseThrow();
        AttendanceRecord historical = attendance.saveAndFlush(new AttendanceRecord(verifiedStudent,
                internship, TODAY, AttendanceResult.PRESENT, VerificationStatus.VERIFIED,
                AttendanceSubmissionType.PHYSICAL_CAPTURE, NOW));
        AttendanceEvidence historicalEvidence = evidence.saveAndFlush(new AttendanceEvidence(historical,
                new PhysicalEvidenceInput(COMPANY_LATITUDE, COMPANY_LONGITUDE, new BigDecimal("10"),
                        new ValidatedCameraImage("image/jpeg", 123, NOW, COMPANY_LATITUDE, COMPANY_LONGITUDE)),
                location, new VerificationDecision(VerificationStatus.VERIFIED,
                        VerificationReason.VERIFIED_GEOFENCE, new BigDecimal("0.95"), true, true),
                AiVerificationDecision.missing(), NOW));
        AttendanceRecord previousFailure = attendance.saveAndFlush(new AttendanceRecord(outsideStudent,
                internships.findByOnboardingStudentId(outsideStudent.getId()).orElseThrow(), TODAY,
                AttendanceResult.PRESENT, VerificationStatus.UNVERIFIED,
                AttendanceSubmissionType.PHYSICAL_CAPTURE, NOW));

        Cookie student = loginCookie(verifiedStudent.getEmail(), "StudentPassword!21");
        mvc.perform(get("/api/v1/student/attendance").cookie(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$[0].livenessStatus").value("HISTORICAL"))
                .andExpect(jsonPath("$[0].verificationBasis").value("LEGACY"))
                .andExpect(jsonPath("$[0].evidence.verificationReason").value("VERIFIED_GEOFENCE"))
                .andExpect(jsonPath("$[0].evidence.distanceMeters").value(0.95));
        mvc.perform(get("/api/v1/student/attendance")
                        .cookie(loginCookie(outsideStudent.getEmail(), "StudentPassword!22")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$[0].livenessStatus").value("HISTORICAL"));
        mvc.perform(get("/api/v1/mentor/attendance/today")
                        .cookie(loginCookie(mentorA.getEmail(), "MentorPassword!23")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].todayAttendance.livenessStatus")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("HISTORICAL"))));
        assertThat(attendance.findById(historical.getId()).orElseThrow().getVerificationStatus())
                .isEqualTo(VerificationStatus.VERIFIED);
        assertThat(attendance.findById(previousFailure.getId()).orElseThrow().getVerificationStatus())
                .isEqualTo(VerificationStatus.UNVERIFIED);
        AttendanceEvidence retained = evidence.findById(historicalEvidence.getId()).orElseThrow();
        assertThat(retained.getLatitude()).isEqualByComparingTo(COMPANY_LATITUDE);
        assertThat(retained.getDistanceMeters()).isEqualByComparingTo("0.95");
        assertThat(retained.getCreatedAt()).isEqualTo(NOW);
        assertThat(retained.getVerificationReason()).isEqualTo(VerificationReason.VERIFIED_GEOFENCE);
    }

    @Test void storedSubmissionLocationFlowsToOwnerAssignedMentorAndAuthorizedHistory() throws Exception {
        assignments.saveAndFlush(new MentorStudentAssignment(mentorA,verifiedStudent,admin,NOW));
        submitPhysical(verifiedStudent,"StudentPassword!21","0","0","10");
        Cookie student=loginCookie(verifiedStudent.getEmail(),"StudentPassword!21"),mentor=loginCookie(mentorA.getEmail(),"MentorPassword!23");
        mvc.perform(get("/api/v1/student/attendance/today").cookie(student)).andExpect(status().isOk())
            .andExpect(jsonPath("$.attendance.evidence.latitude").value(0)).andExpect(jsonPath("$.attendance.evidence.longitude").value(0)).andExpect(jsonPath("$.attendance.livenessStatus").value("VERIFIED"));
        mvc.perform(get("/api/v1/mentor/attendance/today").cookie(mentor)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].todayAttendance.evidence.latitude").value(0)).andExpect(jsonPath("$[0].todayAttendance.evidence.gpsAccuracyMeters").value(10));
        mvc.perform(get("/api/v1/mentor/attendance/today").cookie(loginCookie(mentorB.getEmail(),"MentorPassword!24"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/student/attendance").cookie(loginCookie(outsideStudent.getEmail(),"StudentPassword!22"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        String request=json.writeValueAsString(Map.of("reportType","ATTENDANCE","fields",java.util.List.of("attendanceLatitude","attendanceLongitude","gpsAccuracyMeters","verification"),"filters",Map.of(),"studentIds",java.util.List.of(verifiedStudent.getId()),"page",0,"format","XLSX"));
        mvc.perform(post("/api/v1/mentor/reports/table").cookie(mentor).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].attendanceLatitude").value(0)).andExpect(jsonPath("$.rows[0].attendanceLongitude").value(0)).andExpect(jsonPath("$.rows[0].verification").value("VERIFIED"));
        mvc.perform(post("/api/v1/mentor/reports/table").cookie(loginCookie(mentorB.getEmail(),"MentorPassword!24")).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isForbidden());
        assertThat(locations.findAll()).isEmpty();
    }

    private void submitPhysical(User student, String password, String latitude, String longitude,
            String accuracy) throws Exception {
        Cookie session = loginCookie(student.getEmail(), password);
        mvc.perform(multipart("/api/v1/student/attendance/today/physical").file(photo())
                        .file(aiEvidence(session, 1, true, true))
                        .param("latitude", latitude).param("longitude", longitude).param("accuracy", accuracy)
                        .param("insideGeofence", "true").cookie(session))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.attendanceResult").value("PRESENT"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.livenessStatus").value("VERIFIED"));
        AttendanceRecord record = attendance.findByStudentIdAndAttendanceDate(student.getId(), TODAY).orElseThrow();
        AttendanceEvidence stored = evidence.findByAttendanceId(record.getId()).orElseThrow();
        assertThat(stored.getVerificationReason()).isEqualTo(VerificationReason.EDGE_AI_VERIFIED);
        assertThat(stored.getLatitude()).isEqualTo(SubmissionLocation.from(new BigDecimal(latitude),new BigDecimal(longitude),new BigDecimal(accuracy)).latitude());
        assertThat(stored.getExpectedLatitude()).isNull();
        assertThat(stored.getDistanceMeters()).isNull();
    }

    private void submitWithoutGps(User student, String password) throws Exception {
        Cookie session = loginCookie(student.getEmail(), password);
        mvc.perform(multipart("/api/v1/student/attendance/today/physical").file(photo())
                        .file(aiEvidence(session, 1, true, true)).cookie(session))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.livenessStatus").value("VERIFIED"));
        AttendanceRecord record = attendance.findByStudentIdAndAttendanceDate(student.getId(), TODAY).orElseThrow();
        AttendanceEvidence stored = evidence.findByAttendanceId(record.getId()).orElseThrow();
        assertThat(stored.getVerificationReason()).isEqualTo(VerificationReason.EDGE_AI_VERIFIED);
        assertThat(stored.getLatitude()).isNull();
    }

    private void saveLocation(User student, boolean confirm) throws Exception {
        var internship=internships.findByOnboardingStudentId(student.getId()).orElseThrow();
        var location=new InternshipLocation(internship,COMPANY_LATITUDE,COMPANY_LONGITUDE,new BigDecimal("200"),NOW);
        location.selectedPlace("test-place-"+student.getId(),"Actual test workplace address");
        if(confirm) { location.review(mentorA,"APPROVE",null,NOW); location.review(admin,"APPROVE",null,NOW); }
        locations.saveAndFlush(location);
        if(confirm && assignments.findByStudentIdAndActiveTrue(student.getId()).isEmpty()) assignments.saveAndFlush(new MentorStudentAssignment(mentorA,student,admin,NOW));
    }

    private String locationJson(String latitude, String longitude, String radius) throws Exception {
        return json.writeValueAsString(Map.of("latitude", latitude, "longitude", longitude,
                "geofenceRadiusMeters", radius));
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
        details.apply(company, CompanyType.IT, null, "Company address", "+91 9876543210",
                "Supervisor", "Manager", "+91 9876543211", 2, TODAY.minusDays(10), TODAY.plusDays(10),
                8, mode, BigDecimal.ZERO, false);
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
        evidence.deleteAll(); attendance.deleteAll(); verificationSessions.deleteAll(); locations.deleteAll(); assignments.deleteAll();
        internships.deleteAll(); onboardings.deleteAll(); studentProfiles.deleteAll(); mentorProfiles.deleteAll();
        studentAccounts.deleteAll(); users.deleteAll();
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedAttendanceClock() { return Clock.fixed(NOW, ZONE); }
    }
}
