package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetailsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttendanceSubmissionWindowTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);

    @Test
    void submissionStaysOpenAllDayAndClosesAtMidnightInConfiguredZone() {
        Clock noon = Clock.fixed(Instant.parse("2026-10-19T06:30:00Z"), ZoneId.of("Asia/Kolkata")); // 12:00 PM IST
        Clock beforeMidnight = Clock.fixed(Instant.parse("2026-10-19T18:29:59Z"), ZoneId.of("Asia/Kolkata")); // 23:59:59 IST
        Clock afterMidnight = Clock.fixed(Instant.parse("2026-10-19T18:30:00Z"), ZoneId.of("Asia/Kolkata")); // 00:00:00 IST Oct 20

        assertThat(AttendanceSubmissionWindow.isClosed(DAY, noon)).isFalse();
        assertThatCode(() -> policy(noon).requireSubmissionOpen(10L, null, DAY)).doesNotThrowAnyException();
        assertThat(AttendanceSubmissionWindow.isClosed(DAY, beforeMidnight)).isFalse();
        assertThat(AttendanceSubmissionWindow.isClosed(DAY, afterMidnight)).isTrue();
        assertThatThrownBy(() -> policy(afterMidnight).requireSubmissionOpen(DAY))
                .isInstanceOfSatisfying(AttendanceException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("ATTENDANCE_WINDOW_CLOSED");
                    assertThat(failure.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void deadlineFollowsTheConfiguredZoneInsteadOfTheHostZone() {
        Instant indiaMidnight = Instant.parse("2026-10-19T18:30:00Z"); // 00:00 Oct 20 IST
        Clock india = Clock.fixed(indiaMidnight, ZoneId.of("Asia/Kolkata"));
        Clock utc = Clock.fixed(indiaMidnight, ZoneOffset.UTC);

        assertThat(AttendanceSubmissionWindow.isClosed(DAY, india)).isTrue();
        assertThat(AttendanceSubmissionWindow.isClosed(DAY, utc)).isTrue(); // Attendance zone defaults to Asia/Kolkata
        assertThat(AttendanceSubmissionWindow.deadline(DAY, india)).isEqualTo(indiaMidnight);
    }

    @Test
    void historicalDaysAreClosedAndFutureDaysAreNotEligibleForAbsence() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T06:30:00Z"), ZoneId.of("Asia/Kolkata"));

        assertThat(AttendanceSubmissionWindow.isClosed(DAY.minusDays(1), clock)).isTrue();
        assertThat(AttendanceSubmissionWindow.isClosed(DAY.plusDays(1), clock)).isFalse();
    }

    @Test
    void sameDaySubmissionsAreAllowedAnyTimeWithoutLatePermission() {
        Clock evening = Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZoneId.of("Asia/Kolkata")); // 8:30 PM IST
        LateAttendancePermissionService permissions = mock(LateAttendancePermissionService.class);
        AttendancePolicyService policy = policy(evening, permissions);

        assertThatCode(() -> policy.requireSubmissionOpen(10L, DAY)).doesNotThrowAnyException();
        verifyNoInteractions(permissions);
    }

    @Test
    void pastOrFutureDatesAreRejectedEvenWithoutLatePermission() {
        Clock noon = Clock.fixed(Instant.parse("2026-10-19T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        LateAttendancePermissionService permissions = mock(LateAttendancePermissionService.class);
        AttendancePolicyService policy = policy(noon, permissions);

        for (LocalDate date : new LocalDate[]{DAY.minusDays(1), DAY.plusDays(1)}) {
            assertThatThrownBy(() -> policy.requireSubmissionOpen(10L, date))
                    .isInstanceOfSatisfying(AttendanceException.class,
                            failure -> assertThat(failure.getCode()).isEqualTo("ATTENDANCE_DATE_CHANGED"));
        }
        verifyNoInteractions(permissions);
    }

    private static AttendancePolicyService policy(Clock clock) {
        return policy(clock, mock(LateAttendancePermissionService.class));
    }

    private static AttendancePolicyService policy(Clock clock, LateAttendancePermissionService permissions) {
        return new AttendancePolicyService(mock(HybridWorkScheduleRepository.class),
                mock(InternshipDetailsRepository.class), mock(AttendanceRepository.class), clock, permissions, mock(AttendanceLocationVerificationService.class));
    }
}
