package edu.institution.ims.attendance;

import java.time.*;

/** Daily submission window remains open for the full calendar day in the configured attendance zone. */
final class AttendanceSubmissionWindow {
    private AttendanceSubmissionWindow() {}

    static ZoneId zone(Clock clock) {
        return clock != null && "Asia/Kolkata".equals(clock.getZone().getId())
                ? clock.getZone()
                : ZoneId.of("Asia/Kolkata");
    }

    static Instant deadline(LocalDate date, Clock clock) {
        return date.plusDays(1).atStartOfDay(zone(clock)).toInstant();
    }

    static boolean isClosed(LocalDate date, Clock clock) {
        return !clock.instant().isBefore(deadline(date, clock));
    }
}
