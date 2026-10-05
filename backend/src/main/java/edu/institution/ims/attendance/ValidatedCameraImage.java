package edu.institution.ims.attendance;

import java.math.BigDecimal;
import java.time.Instant;

public record ValidatedCameraImage(
        String mediaType,
        long sizeBytes,
        Instant exifTimestamp,
        BigDecimal exifLatitude,
        BigDecimal exifLongitude
) {}
