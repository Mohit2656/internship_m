package edu.institution.ims.monthlyprogress;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

@Component
public class ProgressPhotoValidator {
    private static final Map<String, String> TYPES = Map.of("jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png");
    private final long maxBytes;

    public ProgressPhotoValidator(@Value("${app.monthly-progress.max-photo-size-bytes:5242880}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public ValidatedProgressPhoto validate(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) throw bad("PHOTO_REQUIRED", "Choose a photograph to submit");
        if (photo.getSize() > maxBytes) throw new MonthlyProgressException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "Photographs must be 5 MB or smaller");
        String supplied = Optional.ofNullable(photo.getOriginalFilename()).orElse("").trim();
        var path = Paths.get(supplied.replace('\\', '/')).getFileName();
        String name = (path == null ? "" : path.toString()).replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank() || name.length() > 255) throw bad("INVALID_PHOTO_NAME", "The photograph filename is invalid");
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expectedType = TYPES.get(extension);
        String declaredType = Optional.ofNullable(photo.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (expectedType == null || !expectedType.equals(declaredType)) throw bad("INVALID_PHOTO", "Only valid JPEG and PNG photographs are allowed");
        try (InputStream input = photo.getInputStream()) {
            if (ImageIO.read(input) == null) throw bad("INVALID_PHOTO", "The uploaded photograph could not be read");
        } catch (IOException exception) { throw bad("INVALID_PHOTO", "The uploaded photograph could not be read"); }

        Instant capturedAt = null;
        BigDecimal latitude = null;
        BigDecimal longitude = null;
        try (InputStream input = photo.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(input);
            ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exif != null) {
                Date captured = exif.getDateOriginal(TimeZone.getTimeZone("UTC"));
                if (captured != null) capturedAt = captured.toInstant();
            }
            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            GeoLocation point = gps == null ? null : gps.getGeoLocation();
            if (point != null && !point.isZero()) {
                latitude = BigDecimal.valueOf(point.getLatitude()).setScale(6, java.math.RoundingMode.HALF_UP);
                longitude = BigDecimal.valueOf(point.getLongitude()).setScale(6, java.math.RoundingMode.HALF_UP);
            }
        } catch (Exception ignored) {
            // A valid image remains acceptable when optional EXIF blocks are missing or malformed.
        }
        return new ValidatedProgressPhoto(name, extension, expectedType, photo.getSize(), capturedAt, latitude, longitude);
    }

    private static MonthlyProgressException bad(String code, String message) {
        return new MonthlyProgressException(HttpStatus.BAD_REQUEST, code, message);
    }
}
