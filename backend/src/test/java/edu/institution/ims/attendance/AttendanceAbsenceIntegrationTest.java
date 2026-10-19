package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {edu.institution.ims.ImsApplication.class,
        AttendanceAbsenceIntegrationTest.ClockConfiguration.class})
class AttendanceAbsenceIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = Instant.parse("2026-09-18T07:00:00Z"); // 12:30 noon
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    @Autowired AttendanceAbsenceService service;
    @Autowired AttendanceAbsenceWriter writer;
    @Autowired AttendanceRepository attendance;
    @Autowired AttendanceEvidenceRepository evidence;
    @Autowired AttendanceCalendarProperties calendar;
    @Autowired InternshipDetailsRepository internships;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    private final List<InternshipDetails> fixtures = new ArrayList<>();

    @BeforeEach
    void resetClockAndCalendar() {
        clock.setInstant(NOW);
        calendar.getHolidays().clear();
        calendar.getApprovedLeave().clear();
    }

    @AfterEach
    void cleanupFixtures() {
        // Native cleanup also removes the deliberately soft-deleted test history.
        for (InternshipDetails internship : fixtures) {
            jdbc.update("delete from attendance_evidence where attendance_id in "
                    + "(select id from attendance_records where internship_id = ?)", internship.getId());
            jdbc.update("delete from attendance_records where internship_id = ?", internship.getId());
            internships.deleteById(internship.getId());
            onboardings.deleteById(internship.getOnboarding().getId());
            users.deleteById(internship.getOnboarding().getStudent().getId());
        }
        fixtures.clear();
        calendar.getHolidays().clear();
        calendar.getApprovedLeave().clear();
        clock.setInstant(NOW);
    }

    @Test
    void backfillsDailyAbsencesOnceIncludingTodayAfterNoonAndSkipsWeekends() {
        InternshipDetails internship = internship(TODAY.minusDays(7), TODAY.plusDays(3));

        assertThat(service.processOverdueAttendance()).isEqualTo(6);
        assertThat(service.processOverdueAttendance()).isZero();

        List<AttendanceRecord> records = records(internship);
        assertThat(records).extracting(AttendanceRecord::getAttendanceDate).containsExactly(
                TODAY, TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3),
                TODAY.minusDays(4), TODAY.minusDays(7));
        assertThat(records).allSatisfy(record -> {
            assertThat(record.getAttendanceResult()).isEqualTo(AttendanceResult.ABSENT);
            assertThat(record.getSubmissionType()).isEqualTo(AttendanceSubmissionType.AUTO_ABSENT);
            assertThat(record.getVerificationBasis()).isEqualTo(AttendanceVerificationBasis.AUTOMATIC);
            assertThat(record.getLivenessStatus()).isEqualTo(LivenessStatus.NOT_APPLICABLE);
            assertThat(record.getServerSubmittedAt()).isEqualTo(NOW);
            assertThat(record.getCreatedAt()).isEqualTo(NOW);
            assertThat(record.getUpdatedAt()).isEqualTo(NOW);
        });
        assertThat(jdbc.queryForObject("select count(*) from attendance_evidence e join attendance_records a "
                + "on e.attendance_id = a.id where a.internship_id = ?", Long.class, internship.getId())).isZero();
    }

    @Test
    void waitsUntilExactlyNoonInConfiguredZone() {
        InternshipDetails internship = internship(TODAY, TODAY.plusDays(3));
        clock.setInstant(Instant.parse("2026-09-18T06:29:59Z"));

        assertThat(service.processOverdueAttendance()).isZero();
        assertThat(writer.createIfMissing(internship.getId(), TODAY)).isFalse();
        assertThat(records(internship)).isEmpty();

        clock.setInstant(Instant.parse("2026-09-18T06:30:00Z"));
        assertThat(service.processOverdueAttendance()).isEqualTo(1);
        assertThat(records(internship)).extracting(AttendanceRecord::getAttendanceDate).containsExactly(TODAY);
        assertThat(writer.createIfMissing(internship.getId(), TODAY.plusDays(3))).isFalse();
    }

    @Test
    void preservesSubmittedAttendanceEvidenceAndSoftDeletedHistory() {
        InternshipDetails internship = internship(TODAY.minusDays(2), TODAY);
        User student = internship.getOnboarding().getStudent();
        AttendanceRecord submitted = attendance.saveAndFlush(new AttendanceRecord(student, internship,
                TODAY.minusDays(2), AttendanceResult.PRESENT, VerificationStatus.UNVERIFIED,
                AttendanceSubmissionType.REMOTE_CAPTURE, NOW.minusSeconds(172800)));
        submitted.useLivenessVerification();
        attendance.saveAndFlush(submitted);
        AttendanceEvidence submittedEvidence = evidence.saveAndFlush(new AttendanceEvidence(submitted,
                new ValidatedCameraImage("image/jpeg", 123, NOW.minusSeconds(172800), null, null),
                "Completed the daily task", true, VerificationReason.EDGE_AI_EVIDENCE_MISSING,
                AiVerificationDecision.missing(), NOW.minusSeconds(172800)));
        AttendanceRecord removed = attendance.saveAndFlush(new AttendanceRecord(student, internship,
                TODAY.minusDays(1), AttendanceResult.PRESENT, VerificationStatus.VERIFIED,
                AttendanceSubmissionType.REMOTE_CAPTURE, NOW.minusSeconds(86400)));
        jdbc.update("update attendance_records set deleted_at = ?, active_slot = null where id = ?",
                Timestamp.from(NOW), removed.getId());

        assertThat(service.processOverdueAttendance()).isEqualTo(1);
        assertThat(service.processOverdueAttendance()).isZero();
        assertThat(writer.createIfMissing(internship.getId(), TODAY.minusDays(1))).isFalse();

        AttendanceRecord preserved = attendance.findById(submitted.getId()).orElseThrow();
        assertThat(preserved.getAttendanceResult()).isEqualTo(AttendanceResult.PRESENT);
        assertThat(preserved.getSubmissionType()).isEqualTo(AttendanceSubmissionType.REMOTE_CAPTURE);
        assertThat(preserved.getVerificationBasis()).isEqualTo(AttendanceVerificationBasis.LIVENESS);
        assertThat(preserved.getServerSubmittedAt()).isEqualTo(NOW.minusSeconds(172800));
        AttendanceEvidence preservedEvidence = evidence.findById(submittedEvidence.getId()).orElseThrow();
        assertThat(preservedEvidence.getDailyWorkSummary()).isEqualTo("Completed the daily task");
        assertThat(preservedEvidence.getImageSizeBytes()).isEqualTo(123);
        assertThat(attendance.countIncludingDeleted(internship.getId(), TODAY.minusDays(1))).isEqualTo(1);
        assertThat(attendance.findById(removed.getId())).isEmpty();
        assertThat(records(internship)).hasSize(2);
    }

    @Test
    void excludesHolidaysApprovedLeaveAndDatesOutsideInclusiveInternshipPeriod() {
        InternshipDetails ended = internship(TODAY.minusDays(4), TODAY.minusDays(1));
        InternshipDetails future = internship(TODAY.plusDays(3), TODAY.plusDays(7));
        calendar.getHolidays().add(TODAY.minusDays(3));
        calendar.getApprovedLeave().put(ended.getOnboarding().getStudent().getId(), List.of(
                new AttendanceCalendarProperties.LeavePeriod(TODAY.minusDays(2), TODAY.minusDays(2))));

        assertThat(service.processOverdueAttendance()).isEqualTo(2);

        assertThat(records(ended)).extracting(AttendanceRecord::getAttendanceDate)
                .containsExactly(TODAY.minusDays(1), TODAY.minusDays(4));
        assertThat(records(future)).isEmpty();
    }

    @Test
    void concurrentWritersPersistOnlyOneDailyRecord() throws Exception {
        InternshipDetails internship = internship(TODAY, TODAY.plusDays(1));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent writer timed out");
            try {
                return writer.createIfMissing(internship.getId(), TODAY);
            } catch (DataIntegrityViolationException concurrentInsert) {
                assertThat(attendance.countIncludingDeleted(internship.getId(), TODAY)).isEqualTo(1);
                return false;
            }
        };
        try {
            Future<Boolean> first = workers.submit(attempt);
            Future<Boolean> second = workers.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
        assertThat(attendance.countIncludingDeleted(internship.getId(), TODAY)).isEqualTo(1);
        assertThat(service.processOverdueAttendance()).isZero();
    }

    @Test
    void migrationRestrictsAutomaticSourceToAbsentStatus() {
        InternshipDetails internship = internship(TODAY, TODAY.plusDays(1));
        assertThat(writer.createIfMissing(internship.getId(), TODAY)).isTrue();

        assertThatThrownBy(() -> jdbc.update("update attendance_records set attendance_result = 'PRESENT' "
                + "where internship_id = ?", internship.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(records(internship)).extracting(AttendanceRecord::getAttendanceResult)
                .containsExactly(AttendanceResult.ABSENT);
    }

    private List<AttendanceRecord> records(InternshipDetails internship) {
        return attendance.findAllByStudentIdOrderByAttendanceDateDescServerSubmittedAtDesc(
                internship.getOnboarding().getStudent().getId());
    }

    private InternshipDetails internship(LocalDate start, LocalDate end) {
        User student = users.saveAndFlush(new User("absence-" + UUID.randomUUID() + "@example.edu",
                "test-only-unused-password", Role.STUDENT, true));
        InternshipOnboarding onboarding = new InternshipOnboarding(student);
        onboarding.markDocumentsComplete();
        onboarding.confirmDiary();
        onboarding.markInternshipDetailsComplete();
        onboardings.saveAndFlush(onboarding);
        InternshipDetails internship = new InternshipDetails(onboarding);
        internship.apply("Test Ltd", CompanyType.IT, null, "Test address", "1234567890",
                "Supervisor", "Manager", "1234567890", 1, start, end, 4,
                InternshipMode.COLLEGE_REPORTING, BigDecimal.ZERO, false);
        internships.saveAndFlush(internship);
        fixtures.add(internship);
        return internship;
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary MutableClock absenceTestClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private volatile Instant instant = NOW;
        void setInstant(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
