package edu.institution.ims.attendance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Optional submission evidence, never an attendance-verification decision. */
public record SubmissionLocation(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {
    public static SubmissionLocation from(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy) {
        if (!bounded(latitude) || !bounded(longitude) || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0)
            return new SubmissionLocation(null, null, null);
        BigDecimal safeAccuracy = !bounded(accuracy) || accuracy.signum() < 0
                || accuracy.compareTo(new BigDecimal("999999.99")) > 0 ? null : accuracy.setScale(2, RoundingMode.HALF_UP);
        return new SubmissionLocation(latitude.setScale(6, RoundingMode.HALF_UP),
                longitude.setScale(6, RoundingMode.HALF_UP), safeAccuracy);
    }
    private static boolean bounded(BigDecimal value) {
        return value != null && value.precision() <= 40 && Math.abs((long) value.scale()) <= 20;
    }
}
