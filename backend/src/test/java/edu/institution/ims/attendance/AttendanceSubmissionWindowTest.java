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
    void submissionIsOpenBeforeNoonAndClosedAtNoonInConfiguredZone() {
        Clock beforeNoon = Clock.fixed(Instant.parse("2026-10-19T06:29:59Z"), ZoneId.of("Asia/Kolkata"));
        Clock noon = Clock.fixed(Instant.parse("2026-10-19T06:30:00Z"), ZoneId.of("Asia/Kolkata"));

        assertThat(AttendanceSubmissionWindow.isClosed(DAY, beforeNoon)).isFalse();
        assertThatCode(() -> policy(beforeNoon).requireSubmissionOpen(DAY)).doesNotThrowAnyException();
        assertThat(AttendanceSubmissionWindow.isClosed(DAY, noon)).isTrue();
        assertThatThrownBy(() -> policy(noon).requireSubmissionOpen(DAY))
                .isInstanceOfSatisfying(AttendanceException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("ATTENDANCE_WINDOW_CLOSED");
                    assertThat(failure.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void deadlineFollowsTheConfiguredZoneInsteadOfTheHostZone() {
        Instant instant = Instant.parse("2026-10-19T06:30:00Z");
        Clock india = Clock.fixed(instant, ZoneId.of("Asia/Kolkata"));
        Clock utc = Clock.fixed(instant, ZoneOffset.UTC);

        assertThat(AttendanceSubmissionWindow.isClosed(DAY, india)).isTrue();
        assertThat(AttendanceSubmissionWindow.isClosed(DAY, utc)).isFalse();
        assertThat(AttendanceSubmissionWindow.deadline(DAY, india)).isEqualTo(instant);
        assertThat(AttendanceSubmissionWindow.deadline(DAY, utc))
                .isEqualTo(Instant.parse("2026-10-19T12:00:00Z"));
    }

    @Test
    void historicalDaysAreClosedAndFutureDaysAreNotEligibleForAbsence() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T06:30:00Z"), ZoneId.of("Asia/Kolkata"));

        assertThat(AttendanceSubmissionWindow.isClosed(DAY.minusDays(1), clock)).isTrue();
        assertThat(AttendanceSubmissionWindow.isClosed(DAY.plusDays(1), clock)).isFalse();
    }

    @Test
    void sameDayPermissionAllowsAtNoonOnlyForItsStudent() {
        Clock noon = Clock.fixed(Instant.parse("2026-10-19T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        LateAttendancePermissionService permissions = mock(LateAttendancePermissionService.class);
        when(permissions.isEnabled(10L, DAY)).thenReturn(true);
        AttendancePolicyService policy = policy(noon, permissions);

        assertThatCode(() -> policy.requireSubmissionOpen(10L, DAY)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireSubmissionOpen(11L, DAY))
                .isInstanceOfSatisfying(AttendanceException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo("ATTENDANCE_WINDOW_CLOSED"));
    }

    @Test
    void permissionCannotOpenPastOrFutureAttendanceDates() {
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
                mock(InternshipDetailsRepository.class), mock(AttendanceRepository.class), clock, permissions);
    }
}
