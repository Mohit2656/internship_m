package edu.institution.ims.attendance;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SubmissionLocationTest {
 @Test void realCoordinatesAndZeroAreNormalizedWithoutAStatusDecision() {
  var point=SubmissionLocation.from(new BigDecimal("18.5204001"),new BigDecimal("73.8567001"),new BigDecimal("106.125"));
  assertThat(point.latitude()).isEqualByComparingTo("18.520400");assertThat(point.longitude()).isEqualByComparingTo("73.856700");assertThat(point.accuracyMeters()).isEqualByComparingTo("106.13");
  assertThat(SubmissionLocation.from(BigDecimal.ZERO,BigDecimal.ZERO,null).latitude()).isEqualByComparingTo("0");
 }
 @Test void missingPartialOutOfRangeAndExtremePrecisionRemainUnavailable() {
  for(BigDecimal[] pair:new BigDecimal[][]{{null,null},{BigDecimal.ONE,null},{new BigDecimal("91"),BigDecimal.ZERO},{BigDecimal.ZERO,new BigDecimal("-181")},{new BigDecimal("1e-2147483647"),BigDecimal.ZERO}}) {
   var point=SubmissionLocation.from(pair[0],pair[1],BigDecimal.TEN);assertThat(point.latitude()).isNull();assertThat(point.longitude()).isNull();assertThat(point.accuracyMeters()).isNull();
  }
 }
 @Test void invalidAccuracyDoesNotEraseValidCoordinates() {
  for(BigDecimal accuracy:new BigDecimal[]{null,new BigDecimal("-1"),new BigDecimal("1000000"),new BigDecimal("1e2147483647")}) {
   var point=SubmissionLocation.from(BigDecimal.ONE,BigDecimal.TEN,accuracy);assertThat(point.latitude()).isEqualByComparingTo("1");assertThat(point.accuracyMeters()).isNull();
  }
 }
}
