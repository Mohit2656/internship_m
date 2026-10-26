package edu.institution.ims.attendance;
import java.math.BigDecimal;
import java.time.Instant;
public record AttendanceEvidenceResponse(BigDecimal latitude, BigDecimal longitude, BigDecimal gpsAccuracyMeters,
    BigDecimal expectedLatitude, BigDecimal expectedLongitude, BigDecimal geofenceRadiusMeters,
    BigDecimal distanceMeters, Boolean locationCheckPassed, VerificationReason verificationReason,
    String workplaceAddress, String workplacePlaceId, BigDecimal exifLatitude, BigDecimal exifLongitude,
    Instant exifTimestamp, Boolean metadataConsistent, boolean cameraCaptureUsed, String imageMediaType,
    long imageSizeBytes, String dailyWorkSummary, boolean aiLivenessPassed, boolean aiQualityPassed,
    LocationVerificationStatus locationVerificationStatus, Instant locationCapturedAt, Long workplaceRevision) {
    public static AttendanceEvidenceResponse from(AttendanceEvidence e) {
        if(e == null) return null;
        boolean legacy = e.getAttendance().getVerificationBasis() == AttendanceVerificationBasis.LEGACY;
        boolean locationVisible = legacy || e.getLocationVerificationStatus() != null;
        SubmissionLocation submitted = SubmissionLocation.from(e.getLatitude(), e.getLongitude(), e.getGpsAccuracyMeters());
        return new AttendanceEvidenceResponse(submitted.latitude(),submitted.longitude(),submitted.accuracyMeters(),
            locationVisible ? e.getExpectedLatitude() : null,locationVisible ? e.getExpectedLongitude() : null,locationVisible ? e.getGeofenceRadiusMeters() : null,locationVisible ? e.getDistanceMeters() : null,
            locationVisible ? e.getLocationCheckPassed() : null,e.getVerificationReason(),locationVisible ? e.getWorkplaceAddress() : null,locationVisible ? e.getWorkplacePlaceId() : null,
            legacy ? e.getExifLatitude() : null,legacy ? e.getExifLongitude() : null,e.getExifTimestamp(),e.getMetadataConsistent(),e.isCameraCaptureUsed(),
            e.getImageMediaType(),e.getImageSizeBytes(),e.getDailyWorkSummary(),e.isAiLivenessPassed(),e.isAiQualityPassed(),
            e.getLocationVerificationStatus(),e.getLocationCapturedAt(),e.getWorkplaceRevision());
    }
}
