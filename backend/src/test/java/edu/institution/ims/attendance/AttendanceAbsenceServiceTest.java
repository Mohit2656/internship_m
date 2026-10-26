package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttendanceAbsenceServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final long INTERNSHIP_ID = 10L;
    private static final long STUDENT_ID = 20L;

    private final List<InternshipDetails> candidates = new ArrayList<>();
    private InternshipDetailsRepository internships;
    private AttendanceRepository attendance;
    private HybridWorkScheduleRepository schedules;
    private AttendanceCalendarProperties calendar;
    private AttendanceAbsenceWriter writer;

    @BeforeEach
    void setup() {
        internships = mock(InternshipDetailsRepository.class);
        when(internships.findEligibleForAbsenceProcessing(any(LocalDate.class)))
                .thenAnswer(invocation -> List.copyOf(candidates));
        attendance = mock(AttendanceRepository.class);
        schedules = mock(HybridWorkScheduleRepository.class);
        calendar = new AttendanceCalendarProperties();
        writer = mock(AttendanceAbsenceWriter.class);
        when(writer.createIfMissing(anyLong(), any(LocalDate.class))).thenReturn(true);
    }

    @Test
    void createsOneDailyAbsenceForEachExpiredWeekdayAndSkipsWeekend() {
        candidates.add(internship(MONDAY.minusDays(3), MONDAY.plusDays(10), InternshipMode.ONLINE));

        assertThat(service("2026-10-21T05:00:00Z").processOverdueAttendance()).isEqualTo(3);

        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY.minusDays(3));
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY);
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY.plusDays(1));
        verifyNoMoreInteractions(writer);
    }

    @Test
    void waitsUntilNoonSubmissionDeadlineUsingConfiguredTimeZone() {
        candidates.add(internship(MONDAY.plusDays(1), MONDAY.plusDays(5), InternshipMode.ONLINE));

        assertThat(service("2026-10-20T06:29:59Z").processOverdueAttendance()).isZero();
        verifyNoInteractions(writer);

        assertThat(service("2026-10-20T06:30:00Z").processOverdueAttendance()).isEqualTo(1);
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY.plusDays(1));
        verifyNoMoreInteractions(writer);
    }

    @Test
    void neverGeneratesCurrentAttendanceBeforeNoonOrFutureAttendance() {
        candidates.add(internship(MONDAY, MONDAY.plusDays(10), InternshipMode.COLLEGE_REPORTING));

        assertThat(service("2026-10-19T05:00:00Z").processOverdueAttendance()).isZero();
        verifyNoInteractions(writer);
    }

    @Test
    void atNoonGeneratesOnlyTodayAndNeverFutureDates() {
        candidates.add(internship(MONDAY, MONDAY.plusDays(10), InternshipMode.COLLEGE_REPORTING));

        assertThat(service("2026-10-19T06:30:00Z").processOverdueAttendance()).isEqualTo(1);
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY);
        verifyNoMoreInteractions(writer);
    }

    @Test
    void preservesSubmittedAndSoftDeletedHistoricalDays() {
        candidates.add(internship(MONDAY, MONDAY.plusDays(2), InternshipMode.ONLINE));
        when(attendance.findRecordedDatesIncludingDeleted(INTERNSHIP_ID))
                .thenReturn(List.of(java.sql.Date.valueOf(MONDAY), java.sql.Date.valueOf(MONDAY.plusDays(1))));

        assertThat(service("2026-10-22T05:00:00Z").processOverdueAttendance()).isEqualTo(1);
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY.plusDays(2));
        verifyNoMoreInteractions(writer);
    }

    @Test
    void includesInternshipStartAndEndButNoDaysOutsideThePeriod() {
        candidates.add(internship(MONDAY, MONDAY.plusDays(1), InternshipMode.OFFICE_REPORTING));

        assertThat(service("2026-10-23T05:00:00Z").processOverdueAttendance()).isEqualTo(2);
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY);
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY.plusDays(1));
        verifyNoMoreInteractions(writer);
    }

    @Test
    void excludesHolidayAndApprovedLeaveIncludingBothLeaveBoundaries() {
        candidates.add(internship(MONDAY, MONDAY.plusDays(4), InternshipMode.ONLINE));
        calendar.getHolidays().add(MONDAY.plusDays(1));
        calendar.getApprovedLeave().put(STUDENT_ID, List.of(
                new AttendanceCalendarProperties.LeavePeriod(MONDAY.plusDays(2), MONDAY.plusDays(3))));

        assertThat(service("2026-10-24T05:00:00Z").processOverdueAttendance()).isEqualTo(2);
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY);
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY.plusDays(4));
        verifyNoMoreInteractions(writer);
    }

    @Test
    void onlyConfiguredHybridWorkdaysAreApplicable() {
        InternshipDetails internship = internship(MONDAY, MONDAY.plusDays(2), InternshipMode.HYBRID);
        candidates.add(internship);
        when(schedules.findByInternshipIdAndDayOfWeek(INTERNSHIP_ID, DayOfWeek.MONDAY))
                .thenReturn(Optional.of(new HybridWorkSchedule(internship, DayOfWeek.MONDAY,
                        HybridWorkMode.OFFICE, Instant.parse("2026-10-01T00:00:00Z"))));
        when(schedules.findByInternshipIdAndDayOfWeek(INTERNSHIP_ID, DayOfWeek.WEDNESDAY))
                .thenReturn(Optional.of(new HybridWorkSchedule(internship, DayOfWeek.WEDNESDAY,
                        HybridWorkMode.REMOTE, Instant.parse("2026-10-01T00:00:00Z"))));

        assertThat(service("2026-10-22T05:00:00Z").processOverdueAttendance()).isEqualTo(2);
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY);
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY.plusDays(2));
        verifyNoMoreInteractions(writer);
    }

    @Test
    void repeatedExecutionCountsOnlyNewRecords() {
        candidates.add(internship(MONDAY, MONDAY.plusDays(1), InternshipMode.ONLINE));
        Set<LocalDate> storedDates = new HashSet<>();
        when(writer.createIfMissing(eq(INTERNSHIP_ID), any(LocalDate.class)))
                .thenAnswer(invocation -> storedDates.add(invocation.getArgument(1)));
        AttendanceAbsenceService service = service("2026-10-21T05:00:00Z");

        assertThat(service.processOverdueAttendance()).isEqualTo(2);
        assertThat(service.processOverdueAttendance()).isZero();
        assertThat(storedDates).containsExactlyInAnyOrder(MONDAY, MONDAY.plusDays(1));
    }

    @Test
    void failureForOneDayDoesNotPreventOtherDaysAndIsRetriedOnNextRun() {
        candidates.add(internship(MONDAY, MONDAY.plusDays(1), InternshipMode.ONLINE));
        when(writer.createIfMissing(INTERNSHIP_ID, MONDAY))
                .thenThrow(new DataAccessResourceFailureException("temporary database failure"))
                .thenReturn(true);
        when(writer.createIfMissing(INTERNSHIP_ID, MONDAY.plusDays(1))).thenReturn(true, false);
        AttendanceAbsenceService service = service("2026-10-21T05:00:00Z");

        assertThat(service.processOverdueAttendance()).isEqualTo(1);
        assertThat(service.processOverdueAttendance()).isEqualTo(1);
        verify(writer, times(2)).createIfMissing(INTERNSHIP_ID, MONDAY);
        verify(writer, times(2)).createIfMissing(INTERNSHIP_ID, MONDAY.plusDays(1));
    }

    @Test
    void concurrentDailyInsertIsAcceptedWithoutReplacingTheExistingRecord() {
        candidates.add(internship(MONDAY, MONDAY.plusDays(1), InternshipMode.ONLINE));
        when(writer.createIfMissing(INTERNSHIP_ID, MONDAY))
                .thenThrow(new DataIntegrityViolationException("concurrent daily record"));
        when(attendance.countIncludingDeleted(INTERNSHIP_ID, MONDAY)).thenReturn(1L);

        assertThat(service("2026-10-21T05:00:00Z").processOverdueAttendance()).isEqualTo(1);
        verify(writer).createIfMissing(INTERNSHIP_ID, MONDAY.plusDays(1));
        verify(attendance, never()).save(any(AttendanceRecord.class));
        verify(attendance, never()).delete(any(AttendanceRecord.class));
    }

    private AttendanceAbsenceService service(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZONE);
        AttendancePolicyService policies = new AttendancePolicyService(schedules, internships, attendance, clock, mock(LateAttendancePermissionService.class), mock(AttendanceLocationVerificationService.class));
        return new AttendanceAbsenceService(internships, attendance, policies, calendar, writer, clock);
    }

    private static InternshipDetails internship(LocalDate start, LocalDate end, InternshipMode mode) {
        User student = new User("absence.student@example.edu", "unused", Role.STUDENT, true);
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);
        InternshipOnboarding onboarding = new InternshipOnboarding(student);
        onboarding.confirmDiary();
        onboarding.markInternshipDetailsComplete();
        InternshipDetails internship = new InternshipDetails(onboarding);
        ReflectionTestUtils.setField(internship, "id", INTERNSHIP_ID);
        internship.apply("Example Ltd", CompanyType.IT, null, "Test address", "1234567890",
                "Supervisor", "Manager", "1234567890", 1, start, end, 4, mode, BigDecimal.ZERO, false);
        return internship;
    }
}
