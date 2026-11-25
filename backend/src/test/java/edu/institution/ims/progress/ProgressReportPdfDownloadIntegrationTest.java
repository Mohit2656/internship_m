package edu.institution.ims.progress;

import edu.institution.ims.monthlyprogress.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.weeklyprogress.*;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.user.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProgressReportPdfDownloadIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private StudentAccountRepository accounts;
    @Autowired private MentorProfileRepository mentorProfiles;
    @Autowired private MentorStudentAssignmentRepository assignments;
    @Autowired private InternshipOnboardingRepository onboardings;
    @Autowired private InternshipDetailsRepository internships;
    @Autowired private WeeklyProgressService weeklyService;
    @Autowired private MonthlyProgressService monthlyService;

    @Test
    void testWeeklyAndMonthlyReportPdfDownloadsForAuthorizedRoles() throws Exception {
        // Setup users
        User admin = users.saveAndFlush(new User("admin@institution.edu", "pass", Role.ADMIN, true));
        User mentor = users.saveAndFlush(new User("mentor@institution.edu", "pass", Role.MENTOR, true));
        User student = users.saveAndFlush(new User("student@institution.edu", "pass", Role.STUDENT, true));

        accounts.saveAndFlush(new StudentAccount(student, "John Doe", "STU12345", 7));
        mentorProfiles.saveAndFlush(new MentorProfile(mentor, "Dr. Faculty Mentor", "9876543210", "Computer Engineering", "Assistant Professor", "Dr. Faculty Mentor"));
        assignments.saveAndFlush(new MentorStudentAssignment(mentor, student, admin, Instant.now()));

        // Onboarding & Internship
        var onboarding = new InternshipOnboarding(student);
        onboarding.confirmDiary();
        onboarding.markInternshipDetailsComplete();
        onboardings.saveAndFlush(onboarding);

        var details = new InternshipDetails(onboarding);
        details.apply("Tech Innovations Inc.", CompanyType.IT, null, "Company address", "9876543210",
                "Supervisor", "Manager", "9876543211", 2,
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 12, 30), 999,
                InternshipMode.ONLINE, BigDecimal.ZERO, false);
        details = internships.saveAndFlush(details);

        weeklyService.monitoring(UserPrincipal.from(admin), true);
        weeklyService.synchronize(details);

        // Submit a weekly report with formatted paragraphs and Unicode characters
        String weeklyWork = "Week 1 Objectives:\n- Designed database schema.\n- Client's feedback was positive — all deliverables approved.\n\nNext steps: Integration testing.";
        WeeklyProgressService.Overview overview = weeklyService.ownOverview(UserPrincipal.from(student), details.getId());
        Long weekPeriodId = overview.weeks().get(0).id();

        weeklyService.submit(UserPrincipal.from(student), weekPeriodId, null, weeklyWork);

        // 1. Test Weekly Report PDF download by Student
        byte[] studentWeeklyPdf = mvc.perform(get("/api/v1/student/weekly-progress/{id}/pdf", weekPeriodId)
                .with(user(UserPrincipal.from(student))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(studentWeeklyPdf).startsWith("%PDF".getBytes());
        try (PDDocument doc = PDDocument.load(studentWeeklyPdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("ST. VINCENT PALLOTTI COLLEGE OF ENGINEERING & TECHNOLOGY, NAGPUR",
                    "Department of Computer Engineering", "WEEKLY PROGRESS REPORT", "Tech Innovations Inc.",
                    "Week 1 Objectives:", "Client's feedback was positive");
            // Only 1 internship -> Should NOT contain "Selected Internship"
            assertThat(text).doesNotContain("Selected Internship");
        }

        // 2. Test Weekly Report PDF download by Mentor
        byte[] mentorWeeklyPdf = mvc.perform(get("/api/v1/mentor/weekly-progress/{id}/pdf", weekPeriodId)
                .with(user(UserPrincipal.from(mentor))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(mentorWeeklyPdf).startsWith("%PDF".getBytes());

        // 3. Test Weekly Report PDF download by Admin (III Cell)
        byte[] adminWeeklyPdf = mvc.perform(get("/api/v1/admin/weekly-progress/{id}/pdf", weekPeriodId)
                .with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(adminWeeklyPdf).startsWith("%PDF".getBytes());

        // Now test Monthly Report with Photo
        BufferedImage testImg = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        var g = testImg.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 400, 300);
        g.dispose();
        ByteArrayOutputStream imgOut = new ByteArrayOutputStream();
        ImageIO.write(testImg, "jpg", imgOut);

        MockMultipartFile photoFile = new MockMultipartFile("photo", "internship-workplace.jpg", "image/jpeg", imgOut.toByteArray());
        String monthlyWork = "Monthly Summary:\n1. Completed module A.\n2. Coordinated with mentor.\n\nReview requested.";

        var submittedMonthly = monthlyService.submit(UserPrincipal.from(student), details.getId(), 1, monthlyWork, photoFile);
        Long monthlyReportId = submittedMonthly.id();

        // 4. Test Monthly Report PDF download by Student
        byte[] studentMonthlyPdf = mvc.perform(get("/api/v1/student/monthly-progress/{id}/pdf", monthlyReportId)
                .with(user(UserPrincipal.from(student))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(studentMonthlyPdf).startsWith("%PDF".getBytes());
        try (PDDocument doc = PDDocument.load(studentMonthlyPdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("ST. VINCENT PALLOTTI COLLEGE OF ENGINEERING & TECHNOLOGY, NAGPUR",
                    "Department of Computer Engineering", "MONTHLY PROGRESS REPORT", "Month 1",
                    "Monthly Summary:", "PHOTOGRAPH", "Page 1 of");
        }

        // 5. Test Monthly Report PDF download by Mentor
        byte[] mentorMonthlyPdf = mvc.perform(get("/api/v1/mentor/monthly-progress/{id}/pdf", monthlyReportId)
                .with(user(UserPrincipal.from(mentor))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(mentorMonthlyPdf).startsWith("%PDF".getBytes());

        // 6. Test Monthly Report PDF download by Admin
        byte[] adminMonthlyPdf = mvc.perform(get("/api/v1/admin/monthly-progress/{id}/pdf", monthlyReportId)
                .with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(adminMonthlyPdf).startsWith("%PDF".getBytes());
    }
}
