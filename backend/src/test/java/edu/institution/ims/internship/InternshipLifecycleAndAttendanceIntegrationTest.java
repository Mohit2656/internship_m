package edu.institution.ims.internship;

import edu.institution.ims.attendance.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {edu.institution.ims.ImsApplication.class,
        InternshipLifecycleAndAttendanceIntegrationTest.TestClockConfig.class})
class InternshipLifecycleAndAttendanceIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    // 2026-11-25 12:30 PM IST (after noon deadline)
    private static final Instant NOV_25_INSTANT = ZonedDateTime.of(2026, 11, 25, 12, 30, 0, 0, ZONE).toInstant();
    private static final LocalDate NOV_25_DATE = LocalDate.of(2026, 11, 25);

    @Autowired AttendanceAbsenceService absenceService;
    @Autowired AttendanceService attendanceService;
    @Autowired AttendancePolicyService policyService;
    @Autowired AttendanceRepository attendanceRepo;
    @Autowired InternshipDetailsRepository internshipsRepo;
    @Autowired InternshipOnboardingRepository onboardingsRepo;
    @Autowired UserRepository usersRepo;
    @Autowired InternshipCompletionService completionService;
    @Autowired StudentAttendanceDirectoryService directoryService;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    private final List<InternshipDetails> fixtures = new ArrayList<>();

    @BeforeEach
    void setupClock() {
        clock.setInstant(NOV_25_INSTANT);
    }

    @AfterEach
    void cleanup() {
        for (InternshipDetails i : fixtures) {
            jdbc.update("delete from attendance_records where internship_id = ?", i.getId());
            internshipsRepo.deleteById(i.getId());
            onboardingsRepo.deleteById(i.getOnboarding().getId());
            usersRepo.deleteById(i.getOnboarding().getStudent().getId());
        }
        fixtures.clear();
    }

    @Test
    void regressionCase_nov18EndDate_onNov25IsCompleted_noAttendanceRequired_noAbsenceAfterNov18() {
        // Start: 2026-09-19, End: 2026-11-18, Current Date: 2026-11-25
        LocalDate start = LocalDate.of(2026, 9, 19);
        LocalDate end = LocalDate.of(2026, 11, 18);
        InternshipDetails internship = createInternship(start, end, 1);

        // 1. Authoritative lifecycle resolution
        InternshipLifecycle lifecycle = InternshipLifecycle.resolve(internship, NOV_25_DATE);
        assertThat(lifecycle).isEqualTo(InternshipLifecycle.COMPLETED);

        // 2. Attendance policy check
        UserPrincipal studentPrincipal = UserPrincipal.from(internship.getOnboarding().getStudent());
        TodayAttendanceResponse todayResp = attendanceService.today(studentPrincipal, internship.getId());
        assertThat(todayResp.submissionOpen()).isFalse();

        AttendancePolicyResponse policyResp = policyService.today(studentPrincipal, internship.getId());
        assertThat(policyResp.attendanceTrackingActive()).isFalse();
        assertThat(policyResp.lifecycleStatus()).isEqualTo(AttendanceLifecycleStatus.ENDED);

        // 3. Submitting attendance on Nov 25 must fail with lifecycle exception
        assertThatThrownBy(() -> attendanceService.submitToday(studentPrincipal, internship.getId(), null, null, null, null))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("Attendance tracking ended on 2026-11-18");

        // 4. Record a valid attendance on 2026-09-19
        AttendanceRecord validPast = new AttendanceRecord(internship.getOnboarding().getStudent(), internship,
                start, AttendanceResult.PRESENT, VerificationStatus.VERIFIED, AttendanceSubmissionType.BASIC, NOV_25_INSTANT);
        attendanceRepo.saveAndFlush(validPast);

        // 5. Automatic absence generation must not create absences after Nov 18
        absenceService.processOverdueAttendance();

        List<AttendanceRecord> records = attendanceRepo.findAllByStudentIdAndInternshipIdOrderByAttendanceDateAscIdAsc(
                internship.getOnboarding().getStudent().getId(), internship.getId());
        for (AttendanceRecord r : records) {
            assertThat(r.getAttendanceDate()).isBeforeOrEqualTo(end);
            assertThat(r.getAttendanceDate()).isAfterOrEqualTo(start);
        }

        // Previous valid record preserved
        assertThat(attendanceRepo.findByStudentIdAndInternshipIdAndAttendanceDate(
                internship.getOnboarding().getStudent().getId(), internship.getId(), start)).isPresent();

        // 6. Completion service list includes it in completed, not in active/ongoing
        User adminUser = usersRepo.saveAndFlush(new User("admin-" + UUID.randomUUID() + "@example.edu", "pass", Role.ADMIN, true));
        fixtures.add(internship);
        UserPrincipal adminPrincipal = UserPrincipal.from(adminUser);
        org.springframework.security.core.context.SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            Long sessionId = internship.getOnboarding().getStudent().getAcademicSessionId();
            InternshipCompletionService.Page page = completionService.list(sessionId, null, null, null, 0);
            assertThat(page.stats().completedInternships()).isGreaterThanOrEqualTo(1);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            usersRepo.deleteById(adminUser.getId());
        }
    }

    @Test
    void lifecycleBoundaries_futureStart_startsToday_endsToday_endsYesterday() {
        // Future start: tomorrow
        InternshipDetails future = createInternship(NOV_25_DATE.plusDays(1), NOV_25_DATE.plusDays(30), 1);
        assertThat(InternshipLifecycle.resolve(future, NOV_25_DATE)).isEqualTo(InternshipLifecycle.NOT_STARTED);
        assertThat(policyService.lifecycle(future, NOV_25_DATE)).isEqualTo(AttendanceLifecycleStatus.NOT_STARTED);

        // Starts today
        InternshipDetails startToday = createInternship(NOV_25_DATE, NOV_25_DATE.plusDays(30), 1);
        assertThat(InternshipLifecycle.resolve(startToday, NOV_25_DATE)).isEqualTo(InternshipLifecycle.ONGOING);
        assertThat(policyService.lifecycle(startToday, NOV_25_DATE)).isEqualTo(AttendanceLifecycleStatus.ACTIVE);

        // Ends today
        InternshipDetails endToday = createInternship(NOV_25_DATE.minusDays(30), NOV_25_DATE, 1);
        assertThat(InternshipLifecycle.resolve(endToday, NOV_25_DATE)).isEqualTo(InternshipLifecycle.ONGOING);
        assertThat(policyService.lifecycle(endToday, NOV_25_DATE)).isEqualTo(AttendanceLifecycleStatus.ACTIVE);

        // Ends yesterday
        InternshipDetails endYesterday = createInternship(NOV_25_DATE.minusDays(30), NOV_25_DATE.minusDays(1), 1);
        assertThat(InternshipLifecycle.resolve(endYesterday, NOV_25_DATE)).isEqualTo(InternshipLifecycle.COMPLETED);
        assertThat(policyService.lifecycle(endYesterday, NOV_25_DATE)).isEqualTo(AttendanceLifecycleStatus.ENDED);
    }

    @Test
    void invalidAndUnapprovedInternships_notActive() {
        // Missing dates
        InternshipDetails missingDates = createInternship(null, null, 1);
        assertThat(InternshipLifecycle.resolve(missingDates, NOV_25_DATE)).isNull();

        // Unapproved workflow status (not INTERNSHIP_DETAILS_COMPLETE)
        missingDates.getOnboarding().selectSource(InternshipSource.SELF);
        onboardingsRepo.saveAndFlush(missingDates.getOnboarding());
        assertThat(InternshipLifecycle.resolve(missingDates, NOV_25_DATE)).isNull();
    }

    @Test
    void dualInternships_evaluatedIndependently() {
        User student = usersRepo.saveAndFlush(new User("dual-" + UUID.randomUUID() + "@example.edu",
                "test-pass", Role.STUDENT, true));

        // Internship 1 ended on Nov 18
        InternshipOnboarding o1 = new InternshipOnboarding(student, 1);
        o1.markDocumentsComplete(); o1.confirmDiary(); o1.markInternshipDetailsComplete();
        onboardingsRepo.saveAndFlush(o1);
        InternshipDetails i1 = new InternshipDetails(o1);
        i1.apply("Company 1", CompanyType.IT, null, "Address 1", "1234567890",
                "Sup 1", "Mgr", "1234567890", 2,
                LocalDate.of(2026, 9, 19), LocalDate.of(2026, 11, 18), 8,
                InternshipMode.COLLEGE_REPORTING, BigDecimal.ZERO, false);
        internshipsRepo.saveAndFlush(i1);
        fixtures.add(i1);

        // Internship 2 active: Nov 20 to Dec 20
        InternshipOnboarding o2 = new InternshipOnboarding(student, 2);
        o2.markDocumentsComplete(); o2.confirmDiary(); o2.markInternshipDetailsComplete();
        onboardingsRepo.saveAndFlush(o2);
        InternshipDetails i2 = new InternshipDetails(o2);
        i2.apply("Company 2", CompanyType.IT, null, "Address 2", "1234567890",
                "Sup 2", "Mgr", "1234567890", 1,
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 20), 4,
                InternshipMode.COLLEGE_REPORTING, BigDecimal.ZERO, false);
        internshipsRepo.saveAndFlush(i2);
        fixtures.add(i2);

        // On Nov 25: i1 is COMPLETED, i2 is ONGOING
        assertThat(InternshipLifecycle.resolve(i1, NOV_25_DATE)).isEqualTo(InternshipLifecycle.COMPLETED);
        assertThat(InternshipLifecycle.resolve(i2, NOV_25_DATE)).isEqualTo(InternshipLifecycle.ONGOING);

        assertThat(policyService.lifecycle(i1, NOV_25_DATE)).isEqualTo(AttendanceLifecycleStatus.ENDED);
        assertThat(policyService.lifecycle(i2, NOV_25_DATE)).isEqualTo(AttendanceLifecycleStatus.ACTIVE);
    }

    @Test
    void absenceGeneration_isIdempotentAndRespectsWeekends() {
        // Monday Nov 23 to Friday Nov 27
        LocalDate start = LocalDate.of(2026, 11, 23);
        LocalDate end = LocalDate.of(2026, 11, 27);
        InternshipDetails internship = createInternship(start, end, 1);

        // Run absence generation first time
        int firstRun = absenceService.processOverdueAttendance();

        // Run absence generation second time - must be idempotent (0 created for same days)
        int secondRun = absenceService.processOverdueAttendance();
        assertThat(secondRun).isEqualTo(0);

        // Verify no Saturday/Sunday records created
        List<AttendanceRecord> records = attendanceRepo.findAllByStudentIdAndInternshipIdOrderByAttendanceDateAscIdAsc(
                internship.getOnboarding().getStudent().getId(), internship.getId());
        for (AttendanceRecord r : records) {
            assertThat(r.getAttendanceDate().getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        }
    }

    @Test
    void actualStudentSubmissionVsAutomaticAbsence_distinguishesMetrics() {
        // Monday Nov 23 to Friday Nov 27
        LocalDate start = LocalDate.of(2026, 11, 23);
        LocalDate end = LocalDate.of(2026, 11, 27);
        InternshipDetails internship = createInternship(start, end, 1);
        User student = internship.getOnboarding().getStudent();

        // 1. Automatic absence on Nov 23 (Monday) using authoritative factory
        AttendanceRecord autoAbsent = AttendanceRecord.automaticAbsent(student, internship,
                LocalDate.of(2026, 11, 23), NOV_25_INSTANT);
        attendanceRepo.saveAndFlush(autoAbsent);

        // 2. Student submission on Nov 24 (Tuesday) - PRESENT
        AttendanceRecord studentPresent = new AttendanceRecord(student, internship, LocalDate.of(2026, 11, 24),
                AttendanceResult.PRESENT, VerificationStatus.VERIFIED, AttendanceSubmissionType.PHYSICAL_CAPTURE, NOV_25_INSTANT);
        attendanceRepo.saveAndFlush(studentPresent);

        // 3. Compute directory totals
        List<AttendanceRecord> list = List.of(autoAbsent, studentPresent);
        StudentAttendanceDirectoryService.Totals totals = directoryService.totals(list, internship);

        // Submitted days = 1 (only the student physical capture submission, NOT the automatic absence)
        assertThat(totals.submittedDays()).isEqualTo(1);
        // Present days = 1
        assertThat(totals.presentDays()).isEqualTo(1);
        // Absent days = 1 (from auto absent)
        assertThat(totals.absentDays()).isEqualTo(1);
        // Total days = 2
        assertThat(totals.totalDays()).isEqualTo(2);
        // Percentage = 50.0%
        assertThat(totals.percentage()).isEqualByComparingTo("50.0");
    }

    @Test
    void softDeletedRecords_excludedFromAttendanceMetrics() {
        LocalDate start = LocalDate.of(2026, 11, 23);
        LocalDate end = LocalDate.of(2026, 11, 27);
        InternshipDetails internship = createInternship(start, end, 1);
        User student = internship.getOnboarding().getStudent();

        AttendanceRecord activePresent = new AttendanceRecord(student, internship, LocalDate.of(2026, 11, 24),
                AttendanceResult.PRESENT, VerificationStatus.VERIFIED, AttendanceSubmissionType.PHYSICAL_CAPTURE, NOV_25_INSTANT);
        activePresent = attendanceRepo.saveAndFlush(activePresent);

        // Insert a soft-deleted record directly in the DB
        jdbc.update("insert into attendance_records(student_user_id, internship_id, attendance_date, attendance_result, verification_status, submission_type, server_submitted_at, created_at, updated_at, deleted_at, active_slot, verification_basis, submitted_late) " +
                "values (?, ?, ?, 'PRESENT', 'VERIFIED', 'PHYSICAL_CAPTURE', ?, ?, ?, ?, null, 'LIVENESS', false)",
                student.getId(), internship.getId(), java.sql.Date.valueOf(LocalDate.of(2026, 11, 23)),
                java.sql.Timestamp.from(NOV_25_INSTANT), java.sql.Timestamp.from(NOV_25_INSTANT), java.sql.Timestamp.from(NOV_25_INSTANT),
                java.sql.Timestamp.from(NOV_25_INSTANT));

        // When queried via repository with @SQLRestriction("deleted_at IS NULL"), only the active record is returned
        List<AttendanceRecord> loaded = attendanceRepo.findAllByStudentIdAndInternshipIdOrderByAttendanceDateAscIdAsc(student.getId(), internship.getId());
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getAttendanceDate()).isEqualTo(LocalDate.of(2026, 11, 24));

        StudentAttendanceDirectoryService.Totals totals = directoryService.totals(loaded, internship);
        assertThat(totals.submittedDays()).isEqualTo(1);
        assertThat(totals.presentDays()).isEqualTo(1);
        assertThat(totals.absentDays()).isEqualTo(0);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void lateAttendanceApprovedByMentor_countsAsPresent() {
        LocalDate start = LocalDate.of(2026, 11, 23);
        LocalDate end = LocalDate.of(2026, 11, 27);
        InternshipDetails internship = createInternship(start, end, 1);
        User student = internship.getOnboarding().getStudent();

        // Late submission starts as PENDING_MENTOR_REVIEW
        AttendanceRecord lateRecord = new AttendanceRecord(student, internship, LocalDate.of(2026, 11, 24),
                AttendanceResult.PENDING_MENTOR_REVIEW, VerificationStatus.VERIFIED, AttendanceSubmissionType.PHYSICAL_CAPTURE, NOV_25_INSTANT);
        lateRecord = attendanceRepo.saveAndFlush(lateRecord);

        // Before mentor approval: pendingDays=1, presentDays=0
        StudentAttendanceDirectoryService.Totals beforeApproval = directoryService.totals(List.of(lateRecord), internship);
        assertThat(beforeApproval.pendingDays()).isEqualTo(1);
        assertThat(beforeApproval.presentDays()).isEqualTo(0);
        assertThat(beforeApproval.submittedDays()).isEqualTo(1);

        // Mentor approves late record
        lateRecord.applyMentorDecision(AttendanceReviewStatus.APPROVED);
        lateRecord = attendanceRepo.saveAndFlush(lateRecord);

        // After mentor approval: status transitions to PRESENT
        StudentAttendanceDirectoryService.Totals afterApproval = directoryService.totals(List.of(lateRecord), internship);
        assertThat(afterApproval.pendingDays()).isEqualTo(0);
        assertThat(afterApproval.presentDays()).isEqualTo(1);
        assertThat(afterApproval.submittedDays()).isEqualTo(1);
    }

    @Test
    void distinctStudentVsInternshipCountsReconcile() {
        User student = usersRepo.saveAndFlush(new User("dual-recon-" + UUID.randomUUID() + "@example.edu", "pass", Role.STUDENT, true));

        InternshipOnboarding o1 = new InternshipOnboarding(student, 1);
        o1.markDocumentsComplete(); o1.confirmDiary(); o1.markInternshipDetailsComplete();
        onboardingsRepo.saveAndFlush(o1);
        InternshipDetails i1 = new InternshipDetails(o1);
        i1.apply("Company A", CompanyType.IT, null, "Address A", "1234567890",
                "Sup A", "Mgr", "1234567890", 2,
                LocalDate.of(2026, 9, 19), LocalDate.of(2026, 11, 18), 8,
                InternshipMode.COLLEGE_REPORTING, BigDecimal.ZERO, false);
        internshipsRepo.saveAndFlush(i1);
        fixtures.add(i1);

        InternshipOnboarding o2 = new InternshipOnboarding(student, 2);
        o2.markDocumentsComplete(); o2.confirmDiary(); o2.markInternshipDetailsComplete();
        onboardingsRepo.saveAndFlush(o2);
        InternshipDetails i2 = new InternshipDetails(o2);
        i2.apply("Company B", CompanyType.IT, null, "Address B", "1234567890",
                "Sup B", "Mgr", "1234567890", 1,
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 20), 4,
                InternshipMode.COLLEGE_REPORTING, BigDecimal.ZERO, false);
        internshipsRepo.saveAndFlush(i2);
        fixtures.add(i2);

        // Query distinct students vs internship records
        Long distinctStudents = jdbc.queryForObject(
                "select count(distinct student_user_id) from internship_onboarding where student_user_id = ?",
                Long.class, student.getId());
        Long totalInternships = jdbc.queryForObject(
                "select count(id) from internship_details where onboarding_id in (?, ?)",
                Long.class, o1.getId(), o2.getId());

        assertThat(distinctStudents).isEqualTo(1L);
        assertThat(totalInternships).isEqualTo(2L);
    }

    private InternshipDetails createInternship(LocalDate start, LocalDate end, int num) {
        String sessionName = "TEST-SESSION-" + UUID.randomUUID();
        jdbc.update("insert into academic_sessions(name) values(?)", sessionName);
        Long sessId = jdbc.queryForObject("select id from academic_sessions where name=?", Long.class, sessionName);
        User student = new User("test-" + UUID.randomUUID() + "@example.edu", "test-pass", Role.STUDENT, true);
        student.assignAcademicSession(sessId);
        student = usersRepo.saveAndFlush(student);
        InternshipOnboarding onboarding = new InternshipOnboarding(student, num);
        onboarding.markDocumentsComplete();
        onboarding.confirmDiary();
        onboarding.markInternshipDetailsComplete();
        onboardingsRepo.saveAndFlush(onboarding);

        InternshipDetails details = new InternshipDetails(onboarding);
        details.apply("Test Corp", CompanyType.IT, null, "Test St", "1234567890",
                "Supervisor", "Manager", "1234567890", 1, start, end, 4,
                InternshipMode.COLLEGE_REPORTING, BigDecimal.ZERO, false);
        internshipsRepo.saveAndFlush(details);
        fixtures.add(details);
        return details;
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean @Primary MutableClock testMutableClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private volatile Instant instant = NOV_25_INSTANT;
        void setInstant(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
