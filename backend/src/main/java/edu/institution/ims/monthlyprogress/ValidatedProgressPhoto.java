package edu.institution.ims.monthlyprogress;

import java.math.BigDecimal;
import java.time.Instant;

public record ValidatedProgressPhoto(String originalName, String extension, String mimeType, long size,
                                     Instant capturedAt, BigDecimal latitude, BigDecimal longitude, byte[] bytes,
        GeoVerificationStatus geoVerificationStatus,
        String geoVerificationMessage,
        String captureTimeStatus,
        BigDecimal gpsAltitude,
        Instant gpsTimestamp,
        String cameraMake,
        String cameraModel,
        Integer imageWidth,
        Integer imageHeight,
        Boolean exifPresent,
        Boolean gpsPresent,
        String imageSha256) {}
