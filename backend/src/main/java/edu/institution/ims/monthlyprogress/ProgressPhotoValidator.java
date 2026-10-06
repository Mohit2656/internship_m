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
        supplied = supplied.replaceAll("[\\p{Cntrl}]", "");
        var path = Paths.get(supplied.replace('\\', '/')).getFileName();
        String name = (path == null ? "" : path.toString()).replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank() || name.length() > 255) throw bad("INVALID_PHOTO_NAME", "The photograph filename is invalid");
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expectedType = TYPES.get(extension);
        String declaredType = Optional.ofNullable(photo.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (expectedType == null || !expectedType.equals(declaredType)) throw bad("INVALID_PHOTO", "Only valid JPEG and PNG photographs are allowed");
        byte[] bytes;
        int width, height;
        try (InputStream input = photo.getInputStream()) {
            bytes = input.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
            if (bytes.length > maxBytes) throw new MonthlyProgressException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "Photographs must be 5 MB or smaller");
            boolean complete = expectedType.equals("image/jpeg")
                    ? bytes.length >= 4 && bytes[bytes.length-2] == (byte) 0xff && bytes[bytes.length-1] == (byte) 0xd9
                    : bytes.length >= 12 && Arrays.equals(Arrays.copyOfRange(bytes, bytes.length-12, bytes.length), new byte[]{0,0,0,0,73,69,78,68,(byte)174,66,96,(byte)130});
            if (!complete) throw bad("INVALID_PHOTO", "The uploaded photograph is incomplete or malformed");
            try (var imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) throw bad("INVALID_PHOTO", "Only valid JPEG and PNG photographs are allowed");
                var reader = readers.next();
                try {
                    reader.setInput(imageInput);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    String actualType = format.equals("jpeg") || format.equals("jpg") ? "image/jpeg" : format.equals("png") ? "image/png" : "";
                    if (!actualType.equals(expectedType)) throw bad("INVALID_PHOTO", "The photograph content does not match its file type");
                    width = reader.getWidth(0); height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || (long) width * height > 40_000_000) throw bad("INVALID_PHOTO", "The photograph dimensions are unsupported");
                    if (reader.read(0) == null) throw bad("INVALID_PHOTO", "The uploaded photograph could not be read");
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException exception) { throw bad("INVALID_PHOTO", "The uploaded photograph could not be read"); }

        Instant capturedAt = null, gpsTimestamp = null;
        BigDecimal latitude = null, longitude = null, altitude = null;
        boolean exifPresent = false, gpsPresent = false;
        String make = null, model = null, captureStatus = "MISSING_CAPTURE_TIME";
        GeoVerificationStatus status = GeoVerificationStatus.MISSING_GPS;
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            exifPresent = metadata.getDirectories().iterator().hasNext() &&
                    (metadata.getFirstDirectoryOfType(ExifIFD0Directory.class) != null || metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class) != null || metadata.getFirstDirectoryOfType(GpsDirectory.class) != null);
            ExifIFD0Directory camera = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (camera != null) { make = clean(camera.getString(ExifIFD0Directory.TAG_MAKE)); model = clean(camera.getString(ExifIFD0Directory.TAG_MODEL)); }
            ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exif != null && exif.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)) {
                // EXIF without an offset is represented in UTC, matching the existing convention.
                try {
                    String date = exif.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                    var local = java.time.LocalDateTime.parse(date, java.time.format.DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT));
                    String offset = exif.getString(0x9011);
                    capturedAt = local.toInstant(offset == null ? java.time.ZoneOffset.UTC : java.time.ZoneOffset.of(offset));
                    captureStatus = "PRESENT";
                } catch (RuntimeException invalid) { captureStatus = "INVALID_METADATA"; }
            }
            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            gpsPresent = gps != null;
            if (gps != null) {
                boolean coordinates = gps.containsTag(GpsDirectory.TAG_LATITUDE) || gps.containsTag(GpsDirectory.TAG_LONGITUDE);
                if (coordinates) {
                    status = GeoVerificationStatus.INVALID_GPS;
                    try {
                        var lat = gps.getRationalArray(GpsDirectory.TAG_LATITUDE);
                        var lon = gps.getRationalArray(GpsDirectory.TAG_LONGITUDE);
                        String latRef = gps.getString(GpsDirectory.TAG_LATITUDE_REF), lonRef = gps.getString(GpsDirectory.TAG_LONGITUDE_REF);
                        if (validDms(lat) && validDms(lon) && Set.of("N", "S").contains(latRef == null ? "" : latRef) && Set.of("E", "W").contains(lonRef == null ? "" : lonRef)) {
                            GeoLocation point = gps.getGeoLocation();
                            if (point != null && Double.isFinite(point.getLatitude()) && Double.isFinite(point.getLongitude()) && Math.abs(point.getLatitude()) <= 90 && Math.abs(point.getLongitude()) <= 180) {
                                latitude = BigDecimal.valueOf(point.getLatitude()).setScale(9, java.math.RoundingMode.HALF_UP);
                                longitude = BigDecimal.valueOf(point.getLongitude()).setScale(9, java.math.RoundingMode.HALF_UP);
                                status = GeoVerificationStatus.VERIFIED;
                            }
                        }
                    } catch (RuntimeException invalid) { status = GeoVerificationStatus.INVALID_GPS; }
                } else if (gps.hasErrors() || gps.containsTag(GpsDirectory.TAG_LATITUDE_REF) || gps.containsTag(GpsDirectory.TAG_LONGITUDE_REF)) status = GeoVerificationStatus.INVALID_GPS;
                try {
                    var value = gps.getRational(GpsDirectory.TAG_ALTITUDE);
                    Integer ref = gps.getInteger(GpsDirectory.TAG_ALTITUDE_REF);
                    if (value != null && value.getDenominator() != 0 && Double.isFinite(value.doubleValue()) && Math.abs(value.doubleValue()) < 1_000_000 && ref != null && (ref == 0 || ref == 1)) altitude = BigDecimal.valueOf(value.doubleValue() * (ref == 1 ? -1 : 1)).setScale(6, java.math.RoundingMode.HALF_UP);
                } catch (RuntimeException invalid) { /* Optional altitude does not affect coordinates. */ }
                try { Date time = gps.getGpsDate(); if (time != null) gpsTimestamp = time.toInstant(); }
                catch (RuntimeException invalid) { /* Optional GPS time remains absent. */ }
            }
        } catch (Exception failed) { status = GeoVerificationStatus.METADATA_READ_FAILED; }
        String message = switch (status) {
            case VERIFIED -> "Valid GPS latitude and longitude were found in the image EXIF metadata.";
            case MISSING_GPS -> "No GPS coordinates were found in the image EXIF metadata.";
            case INVALID_GPS -> "GPS metadata was present but the coordinates were invalid.";
            case METADATA_READ_FAILED -> "The image metadata could not be read safely.";
        };
        String sha256;
        try { sha256 = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return new ValidatedProgressPhoto(name, extension, expectedType, bytes.length, capturedAt, latitude, longitude, bytes,
                status, message, captureStatus, altitude, gpsTimestamp, make, model, width, height, exifPresent, gpsPresent, sha256);
    }

    private static boolean validDms(com.drew.lang.Rational[] values) {
        if (values == null || values.length != 3) return false;
        for (var value : values) if (value == null || value.getDenominator() == 0 || !Double.isFinite(value.doubleValue()) || value.doubleValue() < 0) return false;
        return values[1].doubleValue() < 60 && values[2].doubleValue() < 60;
    }
    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return cleaned.isEmpty() ? null : cleaned.substring(0, Math.min(255, cleaned.length()));
    }

    private static MonthlyProgressException bad(String code, String message) {
        return new MonthlyProgressException(HttpStatus.BAD_REQUEST, code, message);
    }
}
