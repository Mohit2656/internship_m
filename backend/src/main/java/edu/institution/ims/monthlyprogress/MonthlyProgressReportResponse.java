package edu.institution.ims.monthlyprogress;

import java.math.BigDecimal;
import java.time.*;

public record MonthlyProgressReportResponse(
        Long id,
        Long studentUserId,
        String studentName,
        String uid,
        String branch,
        Integer semester,
        Long internshipId,
        String companyName,
        String internshipMode,
        int reportMonthNumber,
        LocalDate periodStart,
        LocalDate periodEnd,
        String workDescription,
        String photoOriginalName,
        String photoMimeType,
        long photoSize,
        Instant exifCapturedAt,
        BigDecimal exifLatitude,
        BigDecimal exifLongitude,
        String locationName,
        Instant submittedAt,
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
