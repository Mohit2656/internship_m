package edu.institution.ims.internship;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Authoritative institutional lifecycle calculation for internships.
 * Both start and end dates are inclusive.
 * All lifecycle decisions use the institutional zone (Asia/Kolkata).
 */
public enum InternshipLifecycle {
    NOT_STARTED,
    ONGOING,
    COMPLETED;

    public static final ZoneId INSTITUTIONAL_ZONE = ZoneId.of("Asia/Kolkata");

    public static LocalDate today(Clock clock) {
        if (clock == null) {
            return LocalDate.now(INSTITUTIONAL_ZONE);
        }
        return LocalDate.now(clock.getZone().getId().equals("Asia/Kolkata") ? clock : clock.withZone(INSTITUTIONAL_ZONE));
    }

    public static InternshipLifecycle resolve(InternshipDetails internship, LocalDate today) {
        if (internship == null) return null;
        if (internship.getOnboarding() == null || internship.getOnboarding().getWorkflowStatus() != WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE) {
            return null;
        }
        if (internship.getStartDate() == null || internship.getEndDate() == null) {
            return null;
        }
        if ("COMPLETED".equals(internship.getCompletionStatus())) {
            return COMPLETED;
        }
        if (today.isBefore(internship.getStartDate())) {
            return NOT_STARTED;
        }
        if (!today.isAfter(internship.getEndDate())) {
            return ONGOING;
        }
        return COMPLETED;
    }

    public boolean isCompleted() { return this == COMPLETED; }
    public boolean isOngoing() { return this == ONGOING; }
    public boolean isNotStarted() { return this == NOT_STARTED; }
}
