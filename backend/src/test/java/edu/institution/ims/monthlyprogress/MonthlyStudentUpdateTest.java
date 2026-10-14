package edu.institution.ims.monthlyprogress;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.user.User;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MonthlyStudentUpdateTest {
    @Test void mentorDecisionPreservesLastStudentSubmission() {
        Instant submitted = Instant.parse("2026-10-01T08:00:00Z");
        Instant reviewed = submitted.plusSeconds(3600);
        MonthlyProgressReport report = report(submitted);
        report.decide(mock(User.class), false, "Explain your contribution", reviewed);
        assertThat(report.getSubmittedAt()).isEqualTo(submitted);
        assertThat(report.getReviewedAt()).isEqualTo(reviewed);
    }

    @Test void correctedSubmissionUpdatesStudentTimeAndLaterApprovalPreservesIt() {
        Instant first = Instant.parse("2026-10-01T08:00:00Z");
        Instant corrected = first.plusSeconds(7200);
        MonthlyProgressReport report = report(first);
        report.decide(mock(User.class), false, "Explain your contribution", first.plusSeconds(3600));
        ValidatedProgressPhoto image = new ValidatedProgressPhoto("corrected.png", "png", "image/png", 10,
                null, null, null, new byte[10], null, null, null, null, null, null, null, 20, 20, false, false, "checksum");
        report.resubmit("Corrected work", "corrected-key", image, corrected);
        assertThat(report.getSubmittedAt()).isEqualTo(corrected);
        assertThat(report.getReviewedAt()).isNull();
        report.decide(mock(User.class), true, null, corrected.plusSeconds(3600));
        assertThat(report.getSubmittedAt()).isEqualTo(corrected);
        assertThat(report.getRevision()).isEqualTo(2);
    }

    private MonthlyProgressReport report(Instant submitted) {
        return new MonthlyProgressReport(mock(InternshipDetails.class), 1, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), "Work", "key", "photo.png", "image/png", 10, null, null, null, submitted);
    }
}
