package edu.institution.ims.notification;

import edu.institution.ims.attendance.*;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:mentor_notification_tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.notifications.reminders.enabled=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {edu.institution.ims.ImsApplication.class, MentorNotificationIntegrationTest.ClockConfiguration.class})
@Transactional
class MentorNotificationIntegrationTest {
    static final Instant NOW = Instant.parse("2026-10-27T04:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository accounts;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired AttendanceRepository attendance;
    @Autowired AttendanceService basic;
    @Autowired PhysicalAttendanceService physical;
    @Autowired RemoteAttendanceService remote;
    @Autowired LateAttendancePermissionService permissions;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MutableClock clock;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean MentorNotificationService notifications;
    @MockitoSpyBean AttendanceLocationVerificationService locationVerification;
    User admin, mentor, otherMentor, student;
    InternshipDetails internship;
    MentorStudentAssignment assignment;

    @BeforeEach void setup() {
        clock.now = NOW;
        admin = account("admin", Role.ADMIN);
        mentor = account("mentor", Role.MENTOR);
        otherMentor = account("othermentor", Role.MENTOR);
        student = student("student", "Student A");
        internship = internship(student, InternshipMode.COLLEGE_REPORTING);
        assignment = assignments.saveAndFlush(new MentorStudentAssignment(mentor, student, admin, NOW));
    }

    @Test void basicSubmissionCreatesOneDurableOwnedNotificationAndDuplicateCreatesNone() throws Exception {
        mvc.perform(post("/api/v1/student/attendance/today").with(user(principal(student)))
                        .param("latitude", "18.5204").param("longitude", "73.8567"))
                .andExpect(status().isCreated());
        var inbox = notifications.list(principal(mentor), 0);
        assertThat(inbox.unreadCount()).isEqualTo(1);
        assertThat(inbox.totalElements()).isEqualTo(1);
        var notice = inbox.content().get(0);
        assertThat(notice.type()).isEqualTo("ATTENDANCE_SUBMITTED");
        assertThat(notice.message()).contains("Student A", TODAY.toString());
        assertThat(notice.actionUrl()).isEqualTo("/mentor/attendance");
        assertThat(notice.createdAt()).isEqualTo(NOW);
        assertThat(notice.readAt()).isNull();
        mvc.perform(post("/api/v1/student/attendance/today").with(user(principal(student)))
                        .param("latitude", "18.5204").param("longitude", "73.8567"))
                .andExpect(status().isConflict());
        notifications.attendanceSubmitted(attendance.findByStudentIdAndAttendanceDate(student.getId(), TODAY).orElseThrow());
        assertThat(new MentorNotificationService(jdbc, clock).list(principal(mentor), 0).content()).containsExactly(notice);
        assertThat(notifications.unread(principal(otherMentor)).unreadCount()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from student_notifications", Long.class)).isZero();
    }

    @Test void physicalAndRemoteFlowsNotifyTheirOwnAssignedMentorsOnly() throws Exception {
        User office = student("office", "Office Student");
        User online = student("online", "Online Student");
        internship(office, InternshipMode.OFFICE_REPORTING);
        internship(online, InternshipMode.ONLINE);
        assignments.saveAndFlush(new MentorStudentAssignment(mentor, office, admin, NOW));
        assignments.saveAndFlush(new MentorStudentAssignment(otherMentor, online, admin, NOW));
        physical.submitToday(principal(office), photo(), null, new BigDecimal("18.52"), new BigDecimal("73.85"), BigDecimal.TEN);
        remote.submitToday(principal(online), photo(), null, "Completed the assigned project work and tested the results.",
                new BigDecimal("19.07"), new BigDecimal("72.87"), BigDecimal.TEN);
        assertThat(notifications.list(principal(mentor), 0).content()).singleElement().satisfies(n -> assertThat(n.message()).contains("Office Student"));
        assertThat(notifications.list(principal(otherMentor), 0).content()).singleElement().satisfies(n -> assertThat(n.message()).contains("Online Student"));
    }

    @Test void missingInvalidOrFailedAttendanceDoesNotNotify() throws Exception {
        mvc.perform(post("/api/v1/student/attendance/today").with(user(principal(student))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/student/attendance/today").with(user(principal(student)))
                        .param("latitude", "91").param("longitude", "73"))
                .andExpect(status().isBadRequest());
        assertThat(notifications.unread(principal(mentor)).unreadCount()).isZero();
        assertThat(attendance.findAll()).isEmpty();
    }

    @Test void unassignedAndInactiveMentorsAreNotNotified() {
        assignment.end(NOW); assignments.flush();
        basic.submitToday(principal(student), new BigDecimal("18"), new BigDecimal("73"), BigDecimal.TEN);
        User second = student("inactive-recipient", "Second Student");
        internship(second, InternshipMode.COLLEGE_REPORTING);
        assignments.saveAndFlush(new MentorStudentAssignment(otherMentor, second, admin, NOW));
        jdbc.update("update users set active=false where id=?", otherMentor.getId());
        basic.submitToday(principal(second), new BigDecimal("18"), new BigDecimal("73"), BigDecimal.TEN);
        assertThat(jdbc.queryForObject("select count(*) from mentor_notifications", Long.class)).isZero();
    }

    @Test void automaticAbsenceDoesNotNotifyAndPermittedLateReplacementNotifiesOnce() {
        clock.now = Instant.parse("2026-10-27T14:00:00Z");
        var absent = attendance.saveAndFlush(AttendanceRecord.automaticAbsent(student, internship, TODAY, clock.instant()));
        notifications.attendanceSubmitted(absent);
        assertThat(notifications.unread(principal(mentor)).unreadCount()).isZero();
        permissions.setForMentor(student.getId(), true, principal(mentor));
        var submitted = basic.submitToday(principal(student), new BigDecimal("18"), new BigDecimal("73"), BigDecimal.TEN);
        assertThat(submitted.id()).isEqualTo(absent.getId());
        assertThat(submitted.submittedLate()).isTrue();
        assertThat(notifications.list(principal(mentor), 0).content()).hasSize(1);
        assertThatThrownBy(() -> basic.submitToday(principal(student), new BigDecimal("18"), new BigDecimal("73"), BigDecimal.TEN))
                .isInstanceOf(AttendanceException.class);
        assertThat(notifications.unread(principal(mentor)).unreadCount()).isEqualTo(1);
    }

    @Test void readRoutesAreOwnedIdempotentAndCannotChangeAnotherMentorsInbox() throws Exception {
        submitBasic();
        Long id = notifications.list(principal(mentor), 0).content().get(0).id();
        mvc.perform(get("/api/v1/mentor/notifications").with(user(principal(mentor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id)).andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(get("/api/v1/mentor/notifications/unread-count").with(user(principal(mentor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(patch("/api/v1/mentor/notifications/{id}/read", id).with(user(principal(otherMentor))))
                .andExpect(status().isNotFound());
        for (int i = 0; i < 2; i++) mvc.perform(patch("/api/v1/mentor/notifications/{id}/read", id).with(user(principal(mentor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
        assertThat(notifications.list(principal(mentor), 0).content().get(0).readAt()).isNotNull();
        mvc.perform(patch("/api/v1/mentor/notifications/read-all").with(user(principal(otherMentor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test void allRoutesRejectAnonymousWrongRolesAndInactiveAccounts() throws Exception {
        for (String endpoint : new String[]{"", "/unread-count", "/1/read", "/read-all"}) {
            boolean mutation = endpoint.endsWith("read") || endpoint.endsWith("read-all");
            String path = "/api/v1/mentor/notifications" + endpoint;
            mvc.perform(mutation ? patch(path) : get(path)).andExpect(status().isUnauthorized());
            for (User outsider : new User[]{student, admin})
                mvc.perform((mutation ? patch(path) : get(path)).with(user(principal(outsider)))).andExpect(status().isForbidden());
            jdbc.update("update users set active=false where id=?", mentor.getId());
            mvc.perform((mutation ? patch(path) : get(path)).with(user(principal(mentor)))).andExpect(status().isForbidden());
            jdbc.update("update users set active=true where id=?", mentor.getId());
        }
    }

    @Test void reassignmentAndDeletionRevokeListCountAndReadAccess() throws Exception {
        submitBasic();
        Long id = notifications.list(principal(mentor), 0).content().get(0).id();
        assignment.end(NOW); assignments.flush();
        assignments.saveAndFlush(new MentorStudentAssignment(otherMentor, student, admin, NOW));
        assertThat(notifications.list(principal(mentor), 0).totalElements()).isZero();
        assertThat(notifications.unread(principal(mentor)).unreadCount()).isZero();
        assertThat(notifications.list(principal(otherMentor), 0).content()).isEmpty();
        mvc.perform(patch("/api/v1/mentor/notifications/{id}/read", id).with(user(principal(mentor)))).andExpect(status().isNotFound());
        notifications.markAllRead(principal(mentor));
        assertThat(jdbc.queryForObject("select read_at from mentor_notifications where id=?", java.sql.Timestamp.class, id)).isNull();
        // Reinstating this mentor restores its own event, until that attendance is removed.
        var current = assignments.findByStudentIdAndActiveTrue(student.getId()).orElseThrow();
        current.end(NOW); assignments.flush();
        assignments.saveAndFlush(new MentorStudentAssignment(mentor, student, admin, NOW));
        jdbc.update("update attendance_records set deleted_at=?,active_slot=null where student_user_id=?", java.sql.Timestamp.from(NOW), student.getId());
        assertThat(notifications.list(principal(mentor), 0).content()).isEmpty();
        mvc.perform(patch("/api/v1/mentor/notifications/{id}/read", id).with(user(principal(mentor)))).andExpect(status().isNotFound());
    }

    @Test void inboxIsCappedPagedAndNewestFirstAndReadAllOnlyMarksVisibleOwnRows() {
        for (int i = 0; i < 31; i++) {
            var record = attendance.saveAndFlush(new AttendanceRecord(student, internship, TODAY.minusDays(i), AttendanceResult.PRESENT,
                    VerificationStatus.UNVERIFIED, AttendanceSubmissionType.BASIC, NOW.plusSeconds(i)));
            notifications.attendanceSubmitted(record);
        }
        var first = notifications.list(principal(mentor), -5);
        assertThat(first.page()).isZero();
        assertThat(first.content()).hasSize(30);
        assertThat(first.totalElements()).isEqualTo(31);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content().get(0).createdAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(notifications.list(principal(mentor), 1).content()).hasSize(1);
        assertThat(notifications.list(principal(mentor), Integer.MAX_VALUE).content()).isEmpty();
        assertThat(notifications.markAllRead(principal(otherMentor)).unreadCount()).isZero();
        assertThat(notifications.unread(principal(mentor)).unreadCount()).isEqualTo(31);
        assertThat(notifications.markAllRead(principal(mentor)).unreadCount()).isZero();
    }

    @Test void laterEvidenceFailureRollsBackNotificationAndAttendanceTogether() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(jdbc.queryForObject("select count(*) from mentor_notifications", Long.class)).isEqualTo(1);
            throw new IllegalStateException("Simulated evidence write failure");
        }).when(locationVerification).save(any(), any(), any(), any(), anyBoolean(), any());
        try { assertRolledBackSubmission("evidence-rollback"); }
        finally { doCallRealMethod().when(locationVerification).save(any(), any(), any(), any(), anyBoolean(), any()); }
    }

    @Test void notificationWriteFailureCannotCommitAttendanceWithoutItsEvent() {
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("Simulated notification write failure"); })
                .when(notifications).attendanceSubmitted(any());
        try { assertRolledBackSubmission("notification-rollback"); }
        finally { doCallRealMethod().when(notifications).attendanceSubmitted(any()); }
    }

    private void assertRolledBackSubmission(String suffix) {
        var isolated = new TransactionTemplate(transactionManager);
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> isolated.executeWithoutResult(tx -> {
            User ownAdmin = account(suffix + "admin", Role.ADMIN);
            User ownMentor = account(suffix + "mentor", Role.MENTOR);
            User ownStudent = student(suffix, "Rollback Student");
            internship(ownStudent, InternshipMode.COLLEGE_REPORTING);
            assignments.saveAndFlush(new MentorStudentAssignment(ownMentor, ownStudent, ownAdmin, NOW));
            basic.submitToday(principal(ownStudent), new BigDecimal("18"), new BigDecimal("73"), BigDecimal.TEN);
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from mentor_notifications", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from attendance_records", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from attendance_evidence", Long.class)).isZero();
        assertThat(users.findByEmailIgnoreCase(suffix + "@mentor-notification.example.edu")).isEmpty();
    }

    private void submitBasic() { basic.submitToday(principal(student), new BigDecimal("18"), new BigDecimal("73"), BigDecimal.TEN); }
    private UserPrincipal principal(User value) { return UserPrincipal.from(value); }
    private User account(String suffix, Role role) { return users.saveAndFlush(new User(suffix + "@mentor-notification.example.edu", encoder.encode("TestPassword!23"), role, true)); }
    private User student(String suffix, String name) {
        User value = account(suffix, Role.STUDENT);
        accounts.saveAndFlush(new StudentAccount(value, name, suffix, 6));
        return value;
    }
    private InternshipDetails internship(User owner, InternshipMode mode) {
        var onboarding = new InternshipOnboarding(owner);
        onboarding.markDocumentsComplete(); onboarding.confirmDiary(); onboarding.markInternshipDetailsComplete();
        onboardings.saveAndFlush(onboarding);
        var details = new InternshipDetails(onboarding);
        details.apply("Company", CompanyType.IT, null, "Address", "1234567890", "Supervisor", "Manager", "1234567890",
                2, TODAY.minusDays(40), TODAY.plusDays(10), 8, mode, BigDecimal.ZERO, false);
        return internships.saveAndFlush(details);
    }
    private MockMultipartFile photo() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB), "jpg", output);
        return new MockMultipartFile("photo", "capture.jpg", "image/jpeg", output.toByteArray());
    }
    @TestConfiguration static class ClockConfiguration {
        @Bean @Primary MutableClock mentorNotificationClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Kolkata"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
