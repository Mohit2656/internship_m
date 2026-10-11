package edu.institution.ims.attendance;
import java.math.BigDecimal;
import java.time.Instant;
public record AttendanceEvidenceResponse(BigDecimal latitude, BigDecimal longitude, BigDecimal gpsAccuracyMeters,
    BigDecimal expectedLatitude, BigDecimal expectedLongitude, BigDecimal geofenceRadiusMeters,
    BigDecimal distanceMeters, Boolean locationCheckPassed, VerificationReason verificationReason,
    String workplaceAddress, String workplacePlaceId, BigDecimal exifLatitude, BigDecimal exifLongitude,
    Instant exifTimestamp, Boolean metadataConsistent, boolean cameraCaptureUsed, String imageMediaType,
    long imageSizeBytes, String dailyWorkSummary, boolean aiLivenessPassed, boolean aiQualityPassed) {
    public static AttendanceEvidenceResponse from(AttendanceEvidence e) {
        if(e == null) return null;
        return new AttendanceEvidenceResponse(e.getLatitude(),e.getLongitude(),e.getGpsAccuracyMeters(),
            e.getExpectedLatitude(),e.getExpectedLongitude(),e.getGeofenceRadiusMeters(),e.getDistanceMeters(),
            e.getLocationCheckPassed(),e.getVerificationReason(),e.getWorkplaceAddress(),e.getWorkplacePlaceId(),
            e.getExifLatitude(),e.getExifLongitude(),e.getExifTimestamp(),e.getMetadataConsistent(),e.isCameraCaptureUsed(),
            e.getImageMediaType(),e.getImageSizeBytes(),e.getDailyWorkSummary(),e.isAiLivenessPassed(),e.isAiQualityPassed());
    }
}
