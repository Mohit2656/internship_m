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
    void backfillsDailyAbsencesOnceExcludingSameDayBeforeMidnightAndSkipsWeekends() {
        InternshipDetails internship = internship(TODAY.minusDays(7), TODAY.plusDays(3));

        // At Friday 12:30 PM (NOW), 5 past working days (Mon..Thu + previous Fri) * 2 slots = 10 overdue absent entries
        assertThat(service.processOverdueAttendance()).isEqualTo(10);
        assertThat(service.processOverdueAttendance()).isZero();

        List<AttendanceRecord> records = records(internship);
        assertThat(records).hasSize(10);
        assertThat(records).extracting(AttendanceRecord::getAttendanceDate).containsExactly(
                TODAY.minusDays(1), TODAY.minusDays(1),
                TODAY.minusDays(2), TODAY.minusDays(2),
                TODAY.minusDays(3), TODAY.minusDays(3),
                TODAY.minusDays(4), TODAY.minusDays(4),
                TODAY.minusDays(7), TODAY.minusDays(7));
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
    void waitsUntilEndOfDayInConfiguredZone() {
        InternshipDetails internship = internship(TODAY, TODAY.plusDays(3));
        clock.setInstant(Instant.parse("2026-09-18T18:29:59Z")); // 23:59:59 IST

        assertThat(service.processOverdueAttendance()).isZero();
        assertThat(writer.createIfMissing(internship.getId(), TODAY, 1)).isFalse();
        assertThat(records(internship)).isEmpty();

        clock.setInstant(Instant.parse("2026-09-18T18:30:00Z")); // 00:00:00 IST Sep 19
        assertThat(service.processOverdueAttendance()).isEqualTo(2);
        assertThat(records(internship)).extracting(AttendanceRecord::getAttendanceDate).containsExactly(TODAY, TODAY);
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

        // Overdue days are TODAY-2 (1 submitted + 1 missing slot) and TODAY-1 (1 soft-deleted + 1 missing slot) -> 2 created
        assertThat(service.processOverdueAttendance()).isEqualTo(2);
        assertThat(service.processOverdueAttendance()).isZero();

        AttendanceRecord preserved = attendance.findById(submitted.getId()).orElseThrow();
        assertThat(preserved.getAttendanceResult()).isEqualTo(AttendanceResult.PRESENT);
        assertThat(preserved.getSubmissionType()).isEqualTo(AttendanceSubmissionType.REMOTE_CAPTURE);
        assertThat(preserved.getVerificationBasis()).isEqualTo(AttendanceVerificationBasis.LIVENESS);
        assertThat(preserved.getServerSubmittedAt()).isEqualTo(NOW.minusSeconds(172800));
        AttendanceEvidence preservedEvidence = evidence.findById(submittedEvidence.getId()).orElseThrow();
        assertThat(preservedEvidence.getDailyWorkSummary()).isEqualTo("Completed the daily task");
        assertThat(preservedEvidence.getImageSizeBytes()).isEqualTo(123);
        assertThat(attendance.countIncludingDeleted(internship.getId(), TODAY.minusDays(1))).isEqualTo(2);
        assertThat(attendance.findById(removed.getId())).isEmpty();
        assertThat(records(internship)).hasSize(3);
    }

    @Test
    void excludesHolidaysApprovedLeaveAndDatesOutsideInclusiveInternshipPeriod() {
        InternshipDetails ended = internship(TODAY.minusDays(4), TODAY.minusDays(1));
        InternshipDetails future = internship(TODAY.plusDays(3), TODAY.plusDays(7));
        calendar.getHolidays().add(TODAY.minusDays(3));
        calendar.getApprovedLeave().put(ended.getOnboarding().getStudent().getId(), List.of(
                new AttendanceCalendarProperties.LeavePeriod(TODAY.minusDays(2), TODAY.minusDays(2))));

        assertThat(service.processOverdueAttendance()).isEqualTo(4);

        assertThat(records(ended)).extracting(AttendanceRecord::getAttendanceDate)
                .containsExactly(TODAY.minusDays(1), TODAY.minusDays(1), TODAY.minusDays(4), TODAY.minusDays(4));
        assertThat(records(future)).isEmpty();
    }

    @Test
    void completedInternshipNeverReceivesNewAutomaticAbsences() {
        InternshipDetails completed = internship(TODAY.minusDays(2), TODAY.plusDays(2));
        jdbc.update("update internship_details set completion_status='COMPLETED',completed_at=?,completion_reviewer_id=? where id=?",
                Timestamp.from(NOW.minusSeconds(60)), completed.getOnboarding().getStudent().getId(), completed.getId());
        internships.flush();

        assertThat(service.processOverdueAttendance()).isZero();
        assertThat(writer.createIfMissing(completed.getId(), TODAY)).isFalse();
        assertThat(records(completed)).isEmpty();
    }

    @Test
    void concurrentWritersPersistUpToTwoDailyRecordsAndRejectDuplicateSlot() throws Exception {
        InternshipDetails internship = internship(TODAY.minusDays(1), TODAY.plusDays(1));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent writer timed out");
            try {
                return writer.createIfMissing(internship.getId(), TODAY.minusDays(1), 1);
            } catch (DataIntegrityViolationException concurrentInsert) {
                assertThat(attendance.countIncludingDeleted(internship.getId(), TODAY.minusDays(1))).isEqualTo(1);
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
        assertThat(attendance.countIncludingDeleted(internship.getId(), TODAY.minusDays(1))).isEqualTo(1);
        assertThat(service.processOverdueAttendance()).isEqualTo(1); // fills slot 2 for TODAY-1
        assertThat(attendance.countIncludingDeleted(internship.getId(), TODAY.minusDays(1))).isEqualTo(2);
        assertThat(service.processOverdueAttendance()).isZero();
    }

    @Test
    void migrationAllowsAutomaticSourceOverrideToPresentAndBlocksPendingOrThirdSlot() {
        InternshipDetails internship = internship(TODAY.minusDays(1), TODAY.plusDays(1));
        assertThat(writer.createIfMissing(internship.getId(), TODAY.minusDays(1), 1)).isTrue();

        assertThatThrownBy(() -> jdbc.update("update attendance_records set attendance_result = 'PENDING_MENTOR_REVIEW' "
                + "where internship_id = ?", internship.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatCode(() -> jdbc.update("update attendance_records set attendance_result = 'PRESENT' "
                + "where internship_id = ?", internship.getId()))
                .doesNotThrowAnyException();

        assertThat(records(internship)).extracting(AttendanceRecord::getAttendanceResult)
                .containsExactly(AttendanceResult.PRESENT);
    }

    @Test
    void processOverdueForInternshipBackfillsPastStartDateAndReconcilesUpdatedWorkingDays() {
        User student = users.saveAndFlush(new User("backfill-" + UUID.randomUUID() + "@example.edu",
                "test-only-unused-password", Role.STUDENT, true));
        InternshipOnboarding onboarding = new InternshipOnboarding(student);
        onboarding.markDocumentsComplete();
        onboarding.confirmDiary();
        onboarding.markInternshipDetailsComplete();
        onboardings.saveAndFlush(onboarding);
        InternshipDetails details = new InternshipDetails(onboarding);
        details.apply("Seven Day Co", CompanyType.IT, null, "Test address", "1234567890",
                "Supervisor", "Manager", "1234567890", 6,
                TODAY.minusDays(4), TODAY.plusDays(14), 3,
                InternshipMode.HYBRID, BigDecimal.ZERO, false,
                7, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY");
        details = internships.saveAndFlush(details);
        fixtures.add(details);

        // TODAY is Friday 2026-09-18 (open until midnight) -> Mon(14)..Thu(17) = 4 past overdue days * 2 slots = 8
        assertThat(service.processOverdueForInternship(details.getId())).isEqualTo(8);
        assertThat(attendance.findAllByStudentIdOrderByAttendanceDateDescServerSubmittedAtDesc(student.getId()))
                .extracting(AttendanceRecord::getAttendanceDate)
                .containsExactly(
                        TODAY.minusDays(1), TODAY.minusDays(1),
                        TODAY.minusDays(2), TODAY.minusDays(2),
                        TODAY.minusDays(3), TODAY.minusDays(3),
                        TODAY.minusDays(4), TODAY.minusDays(4));

        // Update schedule to 3 working days/week (Mon, Wed, Fri) and shift startDate later to Wed (TODAY - 2)
        details.apply("Seven Day Co", CompanyType.IT, null, "Test address", "1234567890",
                "Supervisor", "Manager", "1234567890", 6,
                TODAY.minusDays(2), TODAY.plusDays(14), 3,
                InternshipMode.HYBRID, BigDecimal.ZERO, false,
                3, "MONDAY,WEDNESDAY,FRIDAY");
        details = internships.saveAndFlush(details);

        assertThat(service.processOverdueForInternship(details.getId())).isZero();
        // Mon(14), Tue(15) pruned (< startDate); Thu(17) pruned (non-working day); Wed(16) slots 1 & 2 remain
        assertThat(attendance.findAllByStudentIdOrderByAttendanceDateDescServerSubmittedAtDesc(student.getId()))
                .extracting(AttendanceRecord::getAttendanceDate)
                .containsExactly(TODAY.minusDays(2), TODAY.minusDays(2));
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
