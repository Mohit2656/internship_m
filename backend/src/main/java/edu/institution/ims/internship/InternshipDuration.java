package edu.institution.ims.internship;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;

/** Both dates are included; a partial final week counts as one week. */
public final class InternshipDuration {
    public static final int MAX_WEEKS = 520;
    private InternshipDuration() {}

    public static int weeks(LocalDate start, LocalDate end) {
        return Math.toIntExact(weekCount(start,end));
    }
    private static long weekCount(LocalDate start, LocalDate end) {
        if (start == null || end == null || !end.isAfter(start))
            throw new InternshipDetailsException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "End date must be after start date");
        return (ChronoUnit.DAYS.between(start, end) + 7) / 7;
    }

    public static int generatedWeeks(LocalDate start, LocalDate end) {
        long weeks = weekCount(start, end);
        // Bound automatic record creation rather than accepting an unbounded date input.
        if (weeks > MAX_WEEKS)
            throw new InternshipDetailsException(HttpStatus.BAD_REQUEST, "INTERNSHIP_DURATION_TOO_LONG", "Internship duration cannot exceed 520 weeks (10 years)");
        return (int) weeks;
    }
}
