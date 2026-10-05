package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.*;

@Component
public class PhysicalVerificationPolicy {
    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    private final BigDecimal maxGpsAccuracyMeters;
    private final BigDecimal maxMetadataLocationDifferenceMeters;
    private final Duration maxMetadataAge;

    public PhysicalVerificationPolicy(
            @Value("${app.attendance.max-gps-accuracy-meters:100}") BigDecimal maxGpsAccuracyMeters,
            @Value("${app.attendance.max-metadata-location-difference-meters:100}") BigDecimal maxMetadataLocationDifferenceMeters,
            @Value("${app.attendance.max-metadata-age-seconds:600}") long maxMetadataAgeSeconds) {
        this.maxGpsAccuracyMeters = maxGpsAccuracyMeters;
        this.maxMetadataLocationDifferenceMeters = maxMetadataLocationDifferenceMeters;
        this.maxMetadataAge = Duration.ofSeconds(maxMetadataAgeSeconds);
    }

    public VerificationDecision evaluate(InternshipMode mode, InternshipLocation location,
            PhysicalEvidenceInput input, Instant serverTime) {
        BigDecimal distance = distance(input.latitude(), input.longitude(), location);
        Boolean metadataConsistent = metadataConsistency(input, serverTime);
        Boolean locationPassed = locationCheck(distance, input.accuracyMeters(), location);

        if (mode != InternshipMode.OFFICE_REPORTING) {
            return unverified(VerificationReason.PHYSICAL_VERIFICATION_NOT_APPLICABLE,
                    distance, locationPassed, metadataConsistent);
        }
        if (location == null || location.getStatus() != InternshipLocationStatus.CONFIRMED) {
            return unverified(VerificationReason.EXPECTED_LOCATION_NOT_CONFIRMED,
                    distance, locationPassed, metadataConsistent);
        }
        if (input.latitude() == null) {
            return unverified(VerificationReason.LIVE_LOCATION_UNAVAILABLE,
                    distance, locationPassed, metadataConsistent);
        }
        if (input.accuracyMeters().compareTo(maxGpsAccuracyMeters) > 0) {
            return unverified(VerificationReason.GPS_ACCURACY_TOO_LOW,
                    distance, false, metadataConsistent);
        }
        if (Boolean.FALSE.equals(metadataConsistent)) {
            return unverified(VerificationReason.METADATA_CONTRADICTION,
                    distance, locationPassed, false);
        }
        if (!Boolean.TRUE.equals(locationPassed)) {
            return unverified(VerificationReason.OUTSIDE_GEOFENCE,
                    distance, false, metadataConsistent);
        }
        return new VerificationDecision(VerificationStatus.VERIFIED, VerificationReason.VERIFIED_GEOFENCE,
                distance, true, metadataConsistent);
    }

    private Boolean locationCheck(BigDecimal distance, BigDecimal accuracy, InternshipLocation location) {
        if (distance == null || accuracy == null || location == null) return null;
        if (accuracy.compareTo(maxGpsAccuracyMeters) > 0) return false;
        return distance.add(accuracy).compareTo(location.getGeofenceRadiusMeters()) <= 0;
    }

    public Boolean metadataConsistency(PhysicalEvidenceInput input, Instant serverTime) {
        ValidatedCameraImage image = input.image();
        boolean hasTimestamp = image.exifTimestamp() != null;
        boolean hasCoordinates = image.exifLatitude() != null && image.exifLongitude() != null;
        if (!hasTimestamp && !hasCoordinates) return null;
        if (hasTimestamp && Duration.between(image.exifTimestamp(), serverTime).abs().compareTo(maxMetadataAge) > 0) {
            return false;
        }
        if (hasCoordinates && input.latitude() != null) {
            BigDecimal metadataDistance = haversineMeters(input.latitude(), input.longitude(),
                    image.exifLatitude(), image.exifLongitude());
            if (metadataDistance.compareTo(maxMetadataLocationDifferenceMeters) > 0) return false;
        }
        return true;
    }

    private static BigDecimal distance(BigDecimal latitude, BigDecimal longitude, InternshipLocation location) {
        if (latitude == null || longitude == null || location == null) return null;
        return haversineMeters(latitude, longitude, location.getLatitude(), location.getLongitude());
    }

    static BigDecimal haversineMeters(BigDecimal latitude, BigDecimal longitude,
            BigDecimal expectedLatitude, BigDecimal expectedLongitude) {
        double lat1 = Math.toRadians(latitude.doubleValue());
        double lat2 = Math.toRadians(expectedLatitude.doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(expectedLongitude.doubleValue() - longitude.doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double distance = EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);
    }

    private static VerificationDecision unverified(VerificationReason reason, BigDecimal distance,
            Boolean locationPassed, Boolean metadataConsistent) {
        return new VerificationDecision(VerificationStatus.UNVERIFIED, reason, distance,
                locationPassed, metadataConsistent);
    }
}
