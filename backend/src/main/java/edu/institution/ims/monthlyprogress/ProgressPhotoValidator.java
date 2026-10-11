package edu.institution.ims.monthlyprogress;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;

@Component
public class ProgressPhotoValidator {
    private static final Map<String, String> TYPES = Map.of("jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png");
    private final long maxBytes;

    public ProgressPhotoValidator(@Value("${app.monthly-progress.max-photo-size-bytes:20971520}") long maxBytes) {
        if (maxBytes <= 0 || maxBytes > 20L * 1024 * 1024) throw new IllegalArgumentException("Monthly progress photo limit must be between 1 byte and 20 MiB");
        this.maxBytes = maxBytes;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void verifyMultipartLimits(org.springframework.boot.autoconfigure.web.servlet.MultipartProperties multipart) {
        if (multipart.getMaxFileSize().toBytes() < maxBytes || multipart.getMaxRequestSize().toBytes() < maxBytes + 65536)
            throw new IllegalArgumentException("Multipart limits must accommodate the configured monthly progress photo limit and form overhead");
    }

    public ValidatedProgressPhoto validate(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) throw bad("PHOTO_REQUIRED", "Choose a photograph to submit");
        if (photo.getSize() > maxBytes) throw new MonthlyProgressException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", sizeMessage());
        String supplied = Optional.ofNullable(photo.getOriginalFilename()).orElse("").trim();
        supplied = supplied.replaceAll("[\\p{Cntrl}]", "");
        var path = Paths.get(supplied.replace('\\', '/')).getFileName();
        String name = (path == null ? "" : path.toString()).replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank() || name.length() > 255) throw bad("INVALID_PHOTO_NAME", "The photograph filename is invalid");
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expectedType = TYPES.get(extension);
        String declaredType = Optional.ofNullable(photo.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (expectedType == null || !(declaredType.isEmpty() || declaredType.equals("application/octet-stream") || expectedType.equals(declaredType) || expectedType.equals("image/jpeg") && declaredType.equals("image/jpg"))) throw bad("INVALID_PHOTO", "Only valid JPEG and PNG photographs are allowed");
        byte[] bytes;
        int width, height;
        try (InputStream input = photo.getInputStream()) {
            bytes = input.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
            if (bytes.length > maxBytes) throw new MonthlyProgressException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", sizeMessage());
            if (!(expectedType.equals("image/jpeg") ? completeJpeg(bytes) : completePng(bytes)))
                throw bad("INVALID_PHOTO", "The uploaded photograph is incomplete or malformed");
            try (var imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) throw bad("INVALID_PHOTO", "Only valid JPEG and PNG photographs are allowed");
                var reader = readers.next();
                try {
                    List<String> warnings = new ArrayList<>();
                    reader.addIIOReadWarningListener((source, warning) -> warnings.add(warning));
                    reader.setInput(imageInput);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    String actualType = format.equals("jpeg") || format.equals("jpg") ? "image/jpeg" : format.equals("png") ? "image/png" : "";
                    if (!actualType.equals(expectedType)) throw bad("INVALID_PHOTO", "The photograph content does not match its file type");
                    width = reader.getWidth(0); height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || (long) width * height > 40_000_000) throw bad("INVALID_PHOTO", "The photograph dimensions are unsupported");
                    if (reader.read(0) == null || !warnings.isEmpty()) throw bad("INVALID_PHOTO", "The uploaded photograph could not be read");
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException exception) { throw bad("INVALID_PHOTO", "The uploaded photograph could not be read"); }

        String sha256;
        try { sha256 = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return new ValidatedProgressPhoto(name, extension, expectedType, bytes.length, null, null, null, bytes,
                null, null, null, null, null, null, null, width, height, null, null, sha256);
    }

    public long maxBytes() { return maxBytes; }
    private String sizeMessage() { return "Photographs must be " + maxBytes + " bytes (" + (maxBytes / 1048576.0) + " MiB) or smaller"; }

    // Walk marker boundaries rather than looking for a byte pattern inside EXIF/scan data.
    private static boolean completeJpeg(byte[] data) {
        if (data.length < 4 || (data[0] & 255) != 255 || (data[1] & 255) != 216) return false;
        int position = 2;
        boolean scan = false, sawScan = false;
        while (position < data.length) {
            if (scan) {
                while (position < data.length && (data[position] & 255) != 255) position++;
            }
            if (position >= data.length || (data[position++] & 255) != 255) return false;
            while (position < data.length && (data[position] & 255) == 255) position++;
            if (position >= data.length) return false;
            int marker = data[position++] & 255;
            if (scan && (marker == 0 || marker >= 208 && marker <= 215)) continue;
            scan = false;
            if (marker == 217) return sawScan && paddingOnly(data, position);
            if (marker == 0 || marker == 216 || marker >= 208 && marker <= 215) return false;
            if (marker == 1) continue;
            if (position + 2 > data.length) return false;
            int length = ((data[position] & 255) << 8) | (data[position + 1] & 255);
            if (length < 2 || length > data.length - position) return false;
            position += length;
            if (marker == 218) { scan = true; sawScan = true; }
        }
        return false;
    }

    private static boolean completePng(byte[] data) {
        byte[] signature = {(byte)137,80,78,71,13,10,26,10};
        if (data.length < 20 || !Arrays.equals(Arrays.copyOf(data, 8), signature)) return false;
        int position = 8;
        boolean header = false, imageData = false;
        while (data.length - position >= 12) {
            long length = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(data, position, 4).getInt());
            if (length > data.length - position - 12) return false;
            int size = (int) length;
            String type = new String(data, position + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (!header && (!type.equals("IHDR") || size != 13)) return false;
            header = true;
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(data, position + 4, size + 4);
            long supplied = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(data, position + 8 + size, 4).getInt());
            if (crc.getValue() != supplied) return false;
            position += size + 12;
            if (type.equals("IDAT")) imageData = true;
            if (type.equals("IEND")) return size == 0 && imageData && paddingOnly(data, position);
        }
        return false;
    }

    // A small amount of camera/export padding is harmless; arbitrary appended payloads are rejected.
    private static boolean paddingOnly(byte[] data, int position) {
        if (data.length - position > 4096) return false;
        for (; position < data.length; position++) {
            int value = data[position] & 255;
            if (value != 0 && value != 255 && value != 32 && value != 9 && value != 10 && value != 13) return false;
        }
        return true;
    }

    private static MonthlyProgressException bad(String code, String message) {
        return new MonthlyProgressException(HttpStatus.BAD_REQUEST, code, message);
    }
}
