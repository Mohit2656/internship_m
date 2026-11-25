package edu.institution.ims.attendance;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Component
public class PhotoMetadataExtractor {
    public static final ZoneId INSTITUTIONAL_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "heic", "heif");
    private final long maxSizeBytes;

    public PhotoMetadataExtractor(@Value("${app.attendance.max-image-size-bytes:10485760}") long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public PhotoMetadata extractAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AttendanceException(HttpStatus.BAD_REQUEST, "PHOTO_REQUIRED", "Please select a photo to upload.");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new AttendanceException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE",
                    "Uploaded photo exceeds maximum allowed size of " + (maxSizeBytes / (1024 * 1024)) + "MB.");
        }

        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").trim().toLowerCase(Locale.ROOT);
        int dot = filename.lastIndexOf('.');
        String ext = dot >= 0 ? filename.substring(dot + 1) : "";
        String contentType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);

        boolean allowed = ALLOWED_EXTENSIONS.contains(ext)
                || contentType.contains("jpeg") || contentType.contains("png")
                || contentType.contains("heic") || contentType.contains("heif");

        if (!allowed) {
            throw new AttendanceException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PHOTO_FORMAT",
                    "Unsupported photo format. Allowed formats are JPEG, PNG, and HEIC/HEIF.");
        }

        LocalDate captureDate = null;
        LocalTime captureTime = null;
        BigDecimal latitude = null;
        BigDecimal longitude = null;

        try (InputStream is = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(is);

            // 1. Try ExifSubIFDDirectory for capture date & time
            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (subIfd != null) {
                Date originalDate = subIfd.getDateOriginal(TimeZone.getTimeZone(INSTITUTIONAL_ZONE));
                if (originalDate == null) {
                    originalDate = subIfd.getDateDigitized(TimeZone.getTimeZone(INSTITUTIONAL_ZONE));
                }
                if (originalDate != null) {
                    var zdt = originalDate.toInstant().atZone(INSTITUTIONAL_ZONE);
                    captureDate = zdt.toLocalDate();
                    captureTime = zdt.toLocalTime();
                }
            }

            // 2. Try ExifIFD0Directory if not found in SubIFD
            if (captureDate == null || captureTime == null) {
                ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
                if (ifd0 != null) {
                    Date date = ifd0.getDate(ExifIFD0Directory.TAG_DATETIME, TimeZone.getTimeZone(INSTITUTIONAL_ZONE));
                    if (date != null) {
                        var zdt = date.toInstant().atZone(INSTITUTIONAL_ZONE);
                        if (captureDate == null) captureDate = zdt.toLocalDate();
                        if (captureTime == null) captureTime = zdt.toLocalTime();
                    }
                }
            }

            // 3. Try GPS directory
            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir != null) {
                GeoLocation geoLocation = gpsDir.getGeoLocation();
                if (geoLocation != null && !geoLocation.isZero()) {
                    latitude = BigDecimal.valueOf(geoLocation.getLatitude());
                    longitude = BigDecimal.valueOf(geoLocation.getLongitude());
                }
                // Fallback for date/time from GPS timestamp if missing in EXIF
                if (captureDate == null || captureTime == null) {
                    Date gpsDate = gpsDir.getGpsDate();
                    if (gpsDate != null) {
                        var zdt = gpsDate.toInstant().atZone(INSTITUTIONAL_ZONE);
                        if (captureDate == null) captureDate = zdt.toLocalDate();
                        if (captureTime == null) captureTime = zdt.toLocalTime();
                    }
                }
            }
        } catch (Exception e) {
            // Cannot extract metadata or malformed image
            throw new AttendanceException(HttpStatus.BAD_REQUEST, "IMAGE_METADATA_UNREADABLE",
                    "Unable to read metadata from the uploaded photo. Please ensure it is an original, unedited photo with EXIF metadata intact.");
        }

        // Validate required metadata fields
        List<String> missing = new ArrayList<>();
        if (captureDate == null) missing.add("Capture Date");
        if (captureTime == null) missing.add("Capture Time");
        if (latitude == null || longitude == null) missing.add("GPS Location Coordinates (Latitude & Longitude)");

        if (!missing.isEmpty()) {
            throw new AttendanceException(HttpStatus.BAD_REQUEST, "MISSING_PHOTO_METADATA",
                    "Photo verification failed. The uploaded photo is missing required metadata: " + String.join(", ", missing) +
                            ". Please ensure your camera has location/GPS tagging enabled and capture a photo directly.");
        }

        return new PhotoMetadata(captureDate, captureTime, latitude, longitude);
    }
}
