package edu.institution.ims.internship;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InternshipDurationTest {
    @Test void includesBothDatesAndRoundsPartialWeeksUp() {
        assertThat(InternshipDuration.weeks(LocalDate.of(2026,11,1), LocalDate.of(2026,12,30))).isEqualTo(9);
        assertThat(InternshipDuration.weeks(LocalDate.of(2026,11,1), LocalDate.of(2026,11,7))).isEqualTo(1);
        assertThat(InternshipDuration.weeks(LocalDate.of(2026,11,1), LocalDate.of(2026,11,8))).isEqualTo(2);
        assertThat(InternshipDuration.weeks(LocalDate.of(2024,2,27), LocalDate.of(2024,3,4))).isEqualTo(1);
    }
    @Test void rejectsEqualReversedAndUnboundedGeneratedRangesWhileLegacyCalculationRemainsReadable() {
        var start = LocalDate.of(2026,11,1);
        assertThatThrownBy(() -> InternshipDuration.weeks(start,start)).isInstanceOf(InternshipDetailsException.class);
        assertThatThrownBy(() -> InternshipDuration.weeks(start,start.minusDays(1))).isInstanceOf(InternshipDetailsException.class);
        assertThat(InternshipDuration.generatedWeeks(start,start.plusDays(3639))).isEqualTo(520);
        assertThatThrownBy(() -> InternshipDuration.generatedWeeks(start,start.plusDays(3640))).isInstanceOf(InternshipDetailsException.class);
        assertThat(InternshipDuration.weeks(start,start.plusYears(20))).isGreaterThan(520);
        assertThatThrownBy(() -> InternshipDuration.generatedWeeks(LocalDate.MIN,LocalDate.MAX)).isInstanceOfSatisfying(InternshipDetailsException.class,e->assertThat(e.getCode()).isEqualTo("INTERNSHIP_DURATION_TOO_LONG"));
    }
}
