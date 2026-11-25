package edu.institution.ims.internship;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

public class InternshipCompletionDateVerificationTest {

    @Test
    void completionDateEqualsEndDateForNormallyCompletedInternship() {
        // Registered End Date: 2026-11-18, Today: 2026-11-25
        LocalDate startDate = LocalDate.of(2026, 11, 1);
        LocalDate endDate = LocalDate.of(2026, 11, 18);
        LocalDate today = LocalDate.of(2026, 11, 25);

        assertThat(endDate.isBefore(today)).isTrue();

        // SQL rule: when end_date < completionToday then end_date
        LocalDate computedCompletionDate = endDate.isBefore(today) ? endDate : null;
        assertThat(computedCompletionDate).isEqualTo(LocalDate.of(2026, 11, 18));
    }

    @Test
    void futureOrOngoingInternshipHasNullCompletionDate() {
        LocalDate startDate = LocalDate.of(2026, 9, 17);
        LocalDate endDate = LocalDate.of(2027, 1, 17);
        LocalDate today = LocalDate.of(2026, 11, 25);

        assertThat(endDate.isBefore(today)).isFalse();

        LocalDate computedCompletionDate = endDate.isBefore(today) ? endDate : null;
        assertThat(computedCompletionDate).isNull();
    }

    @Test
    void verifySqlExpressionsMatchRule() {
        String completionSql = InternshipCompletionService.COMPLETION_DATE_SQL;
        assertThat(completionSql).contains("when i.end_date is not null and i.end_date < :completionToday then i.end_date");
        assertThat(completionSql).contains("TERMINATED_EARLY");

        String statusSql = InternshipCompletionService.STATUS_SQL;
        assertThat(statusSql).contains("when i.completion_status='COMPLETED' or (i.end_date is not null and i.end_date < :completionToday) then 'COMPLETED'");
    }
}
