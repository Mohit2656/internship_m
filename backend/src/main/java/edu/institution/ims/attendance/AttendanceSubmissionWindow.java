package edu.institution.ims.attendance;

import java.time.*;

/** One daily submission, closing at 12 noon in the configured attendance zone. */
final class AttendanceSubmissionWindow {
    private AttendanceSubmissionWindow() {}

    static ZoneId zone(Clock clock) {
        return clock != null && "Asia/Kolkata".equals(clock.getZone().getId())
                ? clock.getZone()
                : ZoneId.of("Asia/Kolkata");
    }

    static Instant deadline(LocalDate date, Clock clock) {
        return date.atTime(LocalTime.NOON).atZone(zone(clock)).toInstant();
    }

    static boolean isClosed(LocalDate date, Clock clock) {
        return !clock.instant().isBefore(deadline(date, clock));
    }
}
