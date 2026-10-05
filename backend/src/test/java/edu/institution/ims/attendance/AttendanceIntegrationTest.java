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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {edu.institution.ims.ImsApplication.class, AttendanceIntegrationTest.FixedClockConfiguration.class})
class AttendanceIntegrationTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final Instant NOW = Instant.parse("2026-09-18T04:12:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository studentAccounts;
    @Autowired StudentProfileRepository studentProfiles;
    @Autowired MentorProfileRepository mentorProfiles;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired AttendanceRepository attendance;
    @Autowired PasswordEncoder encoder;

    User admin;
    User mentorA;
    User mentorB;
    User studentA;
    User studentB;
    User studentC;
    User noInternshipStudent;
    User futureInternshipStudent;
    User incompleteInternshipStudent;
    InternshipDetails internshipA;
    InternshipDetails internshipC;

    @BeforeEach
    void setup() {
        clearData();
        admin = user("admin@example.edu", "AdminPassword!23", Role.ADMIN);
        mentorA = mentor("mentor.a@example.edu", "MentorPassword!23", "Dr. Asha", "FAC100");
        mentorB = mentor("mentor.b@example.edu", "MentorPassword!24", "Dr. Bharat", "FAC200");
        studentA = student("student.a@example.edu", "StudentPassword!23", "Student A", "UID100");
        studentB = student("student.b@example.edu", "StudentPassword!24", "Student B", "UID200");
        studentC = student("student.c@example.edu", "StudentPassword!25", "Student C", "UID300");
        noInternshipStudent = student("student.none@example.edu", "StudentPassword!26", "Student None", "UID400");
        futureInternshipStudent = student("student.future@example.edu", "StudentPassword!27", "Student Future", "UID500");
        incompleteInternshipStudent = student("student.incomplete@example.edu", "StudentPassword!28", "Student Incomplete", "UID600");
        internshipA = internship(studentA, TODAY.minusDays(10), TODAY.plusDays(10), true, "Alpha Ltd", InternshipMode.COLLEGE_REPORTING);
        internship(studentB, TODAY.minusDays(10), TODAY.plusDays(10), true, "Beta Ltd", InternshipMode.ONLINE);
        internshipC = internship(studentC, TODAY.minusDays(5), TODAY.plusDays(5), true, "Gamma Ltd", InternshipMode.HYBRID);
        internship(futureInternshipStudent, TODAY.plusDays(1), TODAY.plusDays(20), true, "Future Ltd", InternshipMode.COLLEGE_REPORTING);
        internship(incompleteInternshipStudent, TODAY.minusDays(1), TODAY.plusDays(10), false, "Incomplete Ltd", InternshipMode.ONLINE);
    }

    @AfterEach
    void cleanup() { clearData(); }

    @Test
    void studentSubmissionUsesPrincipalAndServerDateAndPreventsDuplicates() throws Exception {
        Cookie studentSession = loginCookie(studentA.getEmail(), "StudentPassword!23");
        mvc.perform(post("/api/v1/student/attendance/today")
                        .queryParam("attendanceDate", "1999-01-01")
                        .cookie(studentSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentUserId\":" + studentB.getId() + ",\"attendanceDate\":\"2099-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendanceDate").value("2026-09-18"))
                .andExpect(jsonPath("$.attendanceResult").value("PRESENT"))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.submissionType").value("BASIC"))
                .andExpect(jsonPath("$.serverSubmittedAt").value(NOW.toString()));

        AttendanceRecord stored = attendance.findByStudentIdAndAttendanceDate(studentA.getId(), TODAY).orElseThrow();
        assertThat(stored.getStudent().getId()).isEqualTo(studentA.getId());
        assertThat(attendance.findByStudentIdAndAttendanceDate(studentB.getId(), TODAY)).isEmpty();

        mvc.perform(post("/api/v1/student/attendance/today").cookie(studentSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_ALREADY_SUBMITTED"));

        assertThatThrownBy(() -> attendance.saveAndFlush(new AttendanceRecord(studentA, internshipA, TODAY,
                AttendanceResult.PRESENT, VerificationStatus.UNVERIFIED, AttendanceSubmissionType.BASIC, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);

        Cookie studentCSession = loginCookie(studentC.getEmail(), "StudentPassword!25");
        mvc.perform(post("/api/v1/student/attendance/today")
                        .queryParam("attendanceDate", TODAY.plusDays(20).toString()).cookie(studentCSession))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.attendanceDate").value(TODAY.toString()));

        mvc.perform(get("/api/v1/student/attendance/today").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attendance.attendanceDate").value(TODAY.toString()));
        mvc.perform(get("/api/v1/student/attendance").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].internshipId").value(internshipA.getId()));
    }

    @Test
    void submissionRequiresCompletedInternshipAndDateInsideRegisteredPeriod() throws Exception {
        mvc.perform(post("/api/v1/student/attendance/today")
                        .cookie(loginCookie(noInternshipStudent.getEmail(), "StudentPassword!26")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENDANCE_NOT_ELIGIBLE"));
        mvc.perform(post("/api/v1/student/attendance/today")
                        .cookie(loginCookie(incompleteInternshipStudent.getEmail(), "StudentPassword!28")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENDANCE_NOT_ELIGIBLE"));
        mvc.perform(post("/api/v1/student/attendance/today")
                        .cookie(loginCookie(futureInternshipStudent.getEmail(), "StudentPassword!27")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENDANCE_OUTSIDE_INTERNSHIP_PERIOD"));
    }

    @Test
    void mentorAutomaticallySeesOnlyAssignedStudentsAndTheirTodayState() throws Exception {
        assignments.save(new MentorStudentAssignment(mentorA, studentA, admin, NOW));
        assignments.save(new MentorStudentAssignment(mentorA, studentB, admin, NOW));
        assignments.save(new MentorStudentAssignment(mentorB, studentC, admin, NOW));
        attendance.save(new AttendanceRecord(studentA, internshipA, TODAY, AttendanceResult.PRESENT,
                VerificationStatus.UNVERIFIED, AttendanceSubmissionType.BASIC, NOW));
        attendance.save(new AttendanceRecord(studentC, internshipC, TODAY, AttendanceResult.PRESENT,
                VerificationStatus.UNVERIFIED, AttendanceSubmissionType.BASIC, NOW));

        mvc.perform(get("/api/v1/mentor/attendance/today")
                        .cookie(loginCookie(mentorA.getEmail(), "MentorPassword!23")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.uid == 'UID100')].todayAttendance.attendanceResult").value("PRESENT"))
                .andExpect(jsonPath("$[?(@.uid == 'UID100')].todayAttendance.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$[1].uid").value("UID200"))
                .andExpect(jsonPath("$[1].todayAttendance").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[*].uid").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("UID300"))));
        mvc.perform(get("/api/v1/mentor/attendance/today")
                        .cookie(loginCookie(mentorB.getEmail(), "MentorPassword!24")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uid").value("UID300"));
        mvc.perform(get("/api/v1/mentor/attendance/today")
                        .cookie(loginCookie(studentA.getEmail(), "StudentPassword!23")))
                .andExpect(status().isForbidden());
    }

    private User user(String email, String password, Role role) {
        return users.save(new User(email, encoder.encode(password), role, true));
    }

    private User mentor(String email, String password, String name, String employeeId) {
        User mentor = user(email, password, Role.MENTOR);
        mentorProfiles.save(new MentorProfile(mentor, name, employeeId, "Computer Engineering", "Professor", "+91 9876543210"));
        return mentor;
    }

    private User student(String email, String password, String name, String uid) {
        User student = user(email, password, Role.STUDENT);
        studentAccounts.save(new StudentAccount(student, name, uid, 6));
        return student;
    }

    private InternshipDetails internship(User student, LocalDate start, LocalDate end, boolean complete,
            String company, InternshipMode mode) {
        InternshipOnboarding onboarding = onboardings.saveAndFlush(new InternshipOnboarding(student));
        onboarding.markDocumentsComplete(); onboarding.confirmDiary();
        InternshipDetails details = new InternshipDetails(onboarding);
        details.apply(company, CompanyType.IT, null, "Company address", "+91 9876543210",
                "Supervisor", "Manager", "+91 9876543211", 2, start, end, 8, mode,
                BigDecimal.ZERO, false);
        internships.saveAndFlush(details);
        if (complete) onboarding.markInternshipDetailsComplete();
        onboardings.saveAndFlush(onboarding);
        return details;
    }

    private Cookie loginCookie(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }

    private void clearData() {
        attendance.deleteAll(); assignments.deleteAll(); internships.deleteAll(); onboardings.deleteAll();
        studentProfiles.deleteAll(); mentorProfiles.deleteAll(); studentAccounts.deleteAll(); users.deleteAll();
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedAttendanceClock() { return Clock.fixed(NOW, ZONE); }
    }
}
