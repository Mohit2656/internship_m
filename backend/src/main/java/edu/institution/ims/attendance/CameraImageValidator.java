package edu.institution.ims.attendance;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

@Component
public class CameraImageValidator {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");
    private final long maxSizeBytes;

    public CameraImageValidator(@Value("${app.attendance.max-image-size-bytes:5242880}") long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public ValidatedCameraImage validate(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "CAMERA_IMAGE_REQUIRED", "Capture a camera image before submitting attendance");
        }
        if (photo.getSize() > maxSizeBytes) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "CAMERA_IMAGE_TOO_LARGE", "Camera image is too large");
        }
        String mediaType = photo.getContentType();
        if (mediaType == null || !ALLOWED_TYPES.contains(mediaType.toLowerCase())) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_CAMERA_IMAGE", "Camera image must be JPEG or PNG");
        }
        try (var input = photo.getInputStream()) {
            if (ImageIO.read(input) == null) {
                throw error(HttpStatus.BAD_REQUEST, "INVALID_CAMERA_IMAGE", "Camera image could not be read");
            }
        } catch (IOException exception) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_CAMERA_IMAGE", "Camera image could not be read");
        }

        Instant timestamp = null;
        BigDecimal exifLatitude = null;
        BigDecimal exifLongitude = null;
        try (var input = photo.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(input);
            ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exif != null) {
                Date date = exif.getDateOriginal(TimeZone.getTimeZone("UTC"));
                if (date != null) timestamp = date.toInstant();
            }
            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            GeoLocation coordinates = gps == null ? null : gps.getGeoLocation();
            if (coordinates != null && !coordinates.isZero()) {
                exifLatitude = BigDecimal.valueOf(coordinates.getLatitude());
                exifLongitude = BigDecimal.valueOf(coordinates.getLongitude());
            }
        } catch (Exception ignored) {
            // EXIF is optional supporting evidence; an unreadable metadata block is treated as absent.
        }
        return new ValidatedCameraImage(mediaType.toLowerCase(), photo.getSize(), timestamp,
                exifLatitude, exifLongitude);
    }

    private static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
