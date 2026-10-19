package edu.institution.ims.attendance;

import java.time.*;

/** One daily submission, closing at 12 noon in the configured attendance zone. */
final class AttendanceSubmissionWindow {
    private AttendanceSubmissionWindow() {}

    static Instant deadline(LocalDate date, Clock clock) {
        return date.atTime(LocalTime.NOON).atZone(clock.getZone()).toInstant();
    }

    static boolean isClosed(LocalDate date, Clock clock) {
        return !clock.instant().isBefore(deadline(date, clock));
    }
}
