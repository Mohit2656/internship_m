package edu.institution.ims.attendance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record PhotoMetadata(
        LocalDate captureDate,
        LocalTime captureTime,
        BigDecimal latitude,
        BigDecimal longitude
) {
    public boolean hasCaptureDate() {
        return captureDate != null;
    }

    public boolean hasCaptureTime() {
        return captureTime != null;
    }

    public boolean hasGpsCoordinates() {
        return latitude != null && longitude != null;
    }
}
