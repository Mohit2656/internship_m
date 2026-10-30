package edu.institution.ims.opportunity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OpportunityMatchingScoringTest {
    @Test void requiredAndPreferredWeightsAreTransparent() {
        assertThat(OpportunityMatchingService.score(2,1,1,1)).isEqualTo(60);
        assertThat(OpportunityMatchingService.score(2,2,1,0)).isEqualTo(80);
        assertThat(OpportunityMatchingService.score(2,2,1,1)).isEqualTo(100);
    }
    @Test void missingCriteriaAreExcludedRatherThanAwardedInventedCredit() {
        assertThat(OpportunityMatchingService.score(0,0,0,0)).isZero();
        assertThat(OpportunityMatchingService.score(2,1,0,0)).isEqualTo(50);
        assertThat(OpportunityMatchingService.score(0,0,2,1)).isEqualTo(50);
    }
    @Test void partialCoverageRoundsConsistentlyToTwoDecimals() {
        assertThat(OpportunityMatchingService.score(3,1,0,0)).isEqualTo(33.33);
        assertThat(OpportunityMatchingService.score(3,2,0,0)).isEqualTo(66.67);
    }
}
