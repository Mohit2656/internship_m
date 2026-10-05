package edu.institution.ims.monthlyprogress;

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
import org.springframework.test.web.servlet.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {edu.institution.ims.ImsApplication.class, MonthlyProgressIntegrationTest.FixedClockConfiguration.class})
class MonthlyProgressIntegrationTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final Instant NOW = Instant.parse("2026-11-10T06:30:00Z");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository studentAccounts;
    @Autowired StudentProfileRepository studentProfiles;
    @Autowired MentorProfileRepository mentorProfiles;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired MonthlyProgressReportRepository reports;
    @Autowired PasswordEncoder encoder;

    User admin;
    User mentorA;
    User mentorB;
    User studentA;
    User studentB;

    @BeforeEach void setup() {
        clearData();
        admin = user("admin.progress@example.edu", "AdminPassword!23", Role.ADMIN);
        mentorA = mentor("mentor.progress.a@example.edu", "MentorPassword!23", "Mentor A", "MP100");
        mentorB = mentor("mentor.progress.b@example.edu", "MentorPassword!24", "Mentor B", "MP200");
        studentA = student("student.progress.a@example.edu", "StudentPassword!23", "Student A", "UID-P100");
        studentB = student("student.progress.b@example.edu", "StudentPassword!24", "Student B", "UID-P200");
        internship(studentA, "Alpha Ltd");
        internship(studentB, "Beta Ltd");
        assignments.save(new MentorStudentAssignment(mentorA, studentA, admin, NOW));
        assignments.save(new MentorStudentAssignment(mentorB, studentB, admin, NOW));
    }
    @AfterEach void cleanup() { clearData(); }

    @Test void studentPeriodsSubmissionDuplicatePrivateFilesAndReadOnlyPolicy() throws Exception {
        Cookie student = loginCookie(studentA.getEmail(), "StudentPassword!23");
        mvc.perform(get("/api/v1/student/monthly-progress").cookie(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.periods.length()").value(4))
                .andExpect(jsonPath("$.periods[0].periodStart").value("2026-09-17"))
                .andExpect(jsonPath("$.periods[0].periodEnd").value("2026-10-16"))
                .andExpect(jsonPath("$.periods[1].periodStart").value("2026-10-17"))
                .andExpect(jsonPath("$.periods[1].periodEnd").value("2026-11-16"))
                .andExpect(jsonPath("$.periods[2].status").value("UPCOMING"))
                .andExpect(jsonPath("$.periods[3].periodEnd").value("2027-01-16"));

        MvcResult created = submit(student, 2, png("progress.png"), "  Completed API integration and testing.  ")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reportMonthNumber").value(2))
                .andExpect(jsonPath("$.periodStart").value("2026-10-17"))
                .andExpect(jsonPath("$.periodEnd").value("2026-11-16"))
                .andExpect(jsonPath("$.workDescription").value("Completed API integration and testing."))
                .andExpect(jsonPath("$.exifLatitude").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        submit(student, 2, png("duplicate.png"), "Duplicate")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTHLY_REPORT_ALREADY_SUBMITTED"));
        submit(student, 3, png("future.png"), "Future")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_MONTH_UPCOMING"));
        mvc.perform(multipart("/api/v1/student/monthly-progress/1").file(new MockMultipartFile("photo", "bad.txt", "text/plain", "bad".getBytes()))
                        .file(new MockMultipartFile("workDescription", "", "text/plain", "Work".getBytes(StandardCharsets.UTF_8))).cookie(student))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PHOTO"));

        mvc.perform(get("/api/v1/student/monthly-progress/{id}/photo", id).cookie(student))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"));
        MvcResult pdf = mvc.perform(get("/api/v1/student/monthly-progress/{id}/pdf", id).cookie(student))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf")).andReturn();
        assertThat(pdf.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        mvc.perform(put("/api/v1/student/monthly-progress/{id}", id).cookie(student).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/v1/student/monthly-progress/{id}", id).cookie(loginCookie(studentB.getEmail(), "StudentPassword!24")))
                .andExpect(status().isNotFound());
    }

    @Test void gpsExifIsExtractedAndMentorAdminAuthorizationIsEnforced() throws Exception {
        Cookie student = loginCookie(studentA.getEmail(), "StudentPassword!23");
        MvcResult created = submit(student, 1, exifJpeg("geo.jpg", 21, 7, 30, 79, 5, 15), "On-site development work")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exifLatitude").value(org.hamcrest.Matchers.closeTo(21.125, 0.0001)))
                .andExpect(jsonPath("$.exifLongitude").value(org.hamcrest.Matchers.closeTo(79.0875, 0.0001)))
                .andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        Cookie assignedMentor = loginCookie(mentorA.getEmail(), "MentorPassword!23");
        Cookie unassignedMentor = loginCookie(mentorB.getEmail(), "MentorPassword!24");
        Cookie adminSession = loginCookie(admin.getEmail(), "AdminPassword!23");

        mvc.perform(get("/api/v1/mentor/monthly-progress").cookie(assignedMentor)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/v1/mentor/monthly-progress/{id}/photo", id).cookie(assignedMentor)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mentor/monthly-progress/{id}/pdf", id).cookie(assignedMentor)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mentor/monthly-progress/{id}", id).cookie(unassignedMentor)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/monthly-progress").cookie(adminSession)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/v1/admin/monthly-progress/{id}/photo", id).cookie(adminSession)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/monthly-progress/{id}/pdf", id).cookie(adminSession)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/monthly-progress")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/mentor/monthly-progress").cookie(student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/monthly-progress").cookie(assignedMentor)).andExpect(status().isForbidden());
    }

    private ResultActions submit(Cookie session, int month, MockMultipartFile photo, String work) throws Exception {
        return mvc.perform(multipart("/api/v1/student/monthly-progress/{month}", month).file(photo)
                .file(new MockMultipartFile("workDescription", "", "text/plain", work.getBytes(StandardCharsets.UTF_8))).cookie(session));
    }
    private MockMultipartFile png(String name) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output);
        return new MockMultipartFile("photo", name, "image/png", output.toByteArray());
    }
    private MockMultipartFile exifJpeg(String name, int latDeg, int latMin, int latSec, int lonDeg, int lonMin, int lonSec) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream(); ImageIO.write(image, "jpeg", jpeg);
        byte[] source = jpeg.toByteArray(); byte[] exif = gpsExif(latDeg, latMin, latSec, lonDeg, lonMin, lonSec);
        ByteArrayOutputStream result = new ByteArrayOutputStream(); result.write(source, 0, 2); result.write(0xff); result.write(0xe1);
        int length = exif.length + 2; result.write((length >>> 8) & 0xff); result.write(length & 0xff); result.write(exif); result.write(source, 2, source.length - 2);
        return new MockMultipartFile("photo", name, "image/jpeg", result.toByteArray());
    }
    private byte[] gpsExif(int latDeg, int latMin, int latSec, int lonDeg, int lonMin, int lonSec) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        out.write('I'); out.write('I'); le16(out, 42); le32(out, 8); le16(out, 1);
        le16(out, 0x8825); le16(out, 4); le32(out, 1); le32(out, 26); le32(out, 0);
        le16(out, 4);
        le16(out, 1); le16(out, 2); le32(out, 2); out.write('N'); out.write(0); out.write(0); out.write(0);
        le16(out, 2); le16(out, 5); le32(out, 3); le32(out, 80);
        le16(out, 3); le16(out, 2); le32(out, 2); out.write('E'); out.write(0); out.write(0); out.write(0);
        le16(out, 4); le16(out, 5); le32(out, 3); le32(out, 104); le32(out, 0);
        rational(out, latDeg); rational(out, latMin); rational(out, latSec);
        rational(out, lonDeg); rational(out, lonMin); rational(out, lonSec);
        return out.toByteArray();
    }
    private static void rational(ByteArrayOutputStream out, int value) { le32(out, value); le32(out, 1); }
    private static void le16(ByteArrayOutputStream out, int value) { out.write(value & 0xff); out.write((value >>> 8) & 0xff); }
    private static void le32(ByteArrayOutputStream out, int value) { le16(out, value); le16(out, value >>> 16); }
    private User user(String email, String password, Role role) { return users.save(new User(email, encoder.encode(password), role, true)); }
    private User mentor(String email, String password, String name, String employeeId) {
        User mentor = user(email, password, Role.MENTOR); mentorProfiles.save(new MentorProfile(mentor, name, employeeId, "Computer Engineering", "Professor", "+91 9876543210")); return mentor;
    }
    private User student(String email, String password, String name, String uid) {
        User student = user(email, password, Role.STUDENT); studentAccounts.save(new StudentAccount(student, name, uid, 6)); return student;
    }
    private void internship(User student, String company) {
        InternshipOnboarding onboarding = onboardings.saveAndFlush(new InternshipOnboarding(student)); onboarding.markDocumentsComplete(); onboarding.confirmDiary();
        InternshipDetails details = new InternshipDetails(onboarding); details.apply(company, CompanyType.IT, null, "Company address", "+91 9876543210", "Supervisor", "Manager", "+91 9876543211", 4, LocalDate.of(2026, 9, 17), LocalDate.of(2027, 1, 16), 17, InternshipMode.OFFICE_REPORTING, BigDecimal.ZERO, false);
        internships.saveAndFlush(details); onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
    }
    private Cookie loginCookie(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }
    private void clearData() {
        reports.deleteAll(); assignments.deleteAll(); internships.deleteAll(); onboardings.deleteAll(); studentProfiles.deleteAll(); mentorProfiles.deleteAll(); studentAccounts.deleteAll(); users.deleteAll();
    }
    @TestConfiguration static class FixedClockConfiguration { @Bean @Primary Clock fixedMonthlyProgressClock() { return Clock.fixed(NOW, ZONE); } }
}
