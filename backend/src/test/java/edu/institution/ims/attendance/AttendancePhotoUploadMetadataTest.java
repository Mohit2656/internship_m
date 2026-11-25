package edu.institution.ims.attendance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AttendancePhotoUploadMetadataTest {
    private PhotoMetadataExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new PhotoMetadataExtractor(10 * 1024 * 1024);
    }

    @Test
    void rejectsEmptyOrMissingFile() {
        assertThatThrownBy(() -> extractor.extractAndValidate(null))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("Please select a photo to upload");

        MockMultipartFile empty = new MockMultipartFile("photo", "empty.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> extractor.extractAndValidate(empty))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("Please select a photo to upload");
    }

    @Test
    void rejectsUnsupportedFileExtension() {
        MockMultipartFile txt = new MockMultipartFile("photo", "test.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> extractor.extractAndValidate(txt))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("Unsupported photo format");
    }

    @Test
    void rejectsPhotoMissingExifMetadata() {
        // Simple 10-byte dummy JPEG without EXIF APP1
        byte[] dummyJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MockMultipartFile file = new MockMultipartFile("photo", "no_metadata.jpg", "image/jpeg", dummyJpeg);

        assertThatThrownBy(() -> extractor.extractAndValidate(file))
                .isInstanceOf(AttendanceException.class)
                .satisfies(e -> {
                    AttendanceException ae = (AttendanceException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getMessage()).contains("missing required metadata");
                });
    }

    @Test
    void extractsValidMetadataFromGeneratedExifJpeg() throws Exception {
        byte[] jpegBytes = createExifJpeg(2026, 9, 18, 10, 30, 0, 18.5204, 73.8567);
        MockMultipartFile file = new MockMultipartFile("photo", "workplace.jpg", "image/jpeg", jpegBytes);

        PhotoMetadata metadata = extractor.extractAndValidate(file);
        assertThat(metadata).isNotNull();
        assertThat(metadata.captureDate()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(metadata.captureTime()).isEqualTo(LocalTime.of(10, 30, 0));
        assertThat(metadata.latitude().doubleValue()).isCloseTo(18.5204, org.assertj.core.data.Offset.offset(0.001));
        assertThat(metadata.longitude().doubleValue()).isCloseTo(73.8567, org.assertj.core.data.Offset.offset(0.001));
    }

    /**
     * Helper to construct a minimal valid JPEG image containing an APP1 EXIF segment
     * with DateTimeOriginal and GPSLatitude/GPSLongitude tags.
     */
    private static byte[] createExifJpeg(int year, int month, int day, int hour, int min, int sec, double lat, double lon) throws Exception {
        int offsetIfd0 = 8;
        int offsetSubIfd = 8 + 2 + 24 + 4; // 38
        int offsetGps = 38 + 2 + 12 + 4;   // 56
        int offsetValues = 56 + 2 + 48 + 4; // 110

        int offsetDt = offsetValues;       // 110
        int offsetLat = offsetDt + 20;     // 130
        int offsetLon = offsetLat + 24;    // 154
        int totalTiffLen = offsetLon + 24; // 178

        ByteBuffer buf = ByteBuffer.allocate(totalTiffLen);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        // TIFF Header (8 bytes)
        buf.put((byte) 'I').put((byte) 'I');
        buf.putShort((short) 42);
        buf.putInt(offsetIfd0);

        // IFD0 (2 entries = 26 bytes + 4 bytes next offset = 30 bytes)
        buf.putShort((short) 2);
        // Entry 1: ExifSubIFD (0x8769, LONG = 4, count = 1, offset = offsetSubIfd)
        buf.putShort((short) 0x8769).putShort((short) 4).putInt(1).putInt(offsetSubIfd);
        // Entry 2: GPSInfo (0x8825, LONG = 4, count = 1, offset = offsetGps)
        buf.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(offsetGps);
        buf.putInt(0); // next IFD

        // SubIFD (1 entry = 14 bytes + 4 bytes next offset = 18 bytes)
        buf.putShort((short) 1);
        // Entry 1: DateTimeOriginal (0x9003, ASCII = 2, count = 20, offset = offsetDt)
        buf.putShort((short) 0x9003).putShort((short) 2).putInt(20).putInt(offsetDt);
        buf.putInt(0);

        // GPS IFD (4 entries = 50 bytes + 4 bytes next offset = 54 bytes)
        buf.putShort((short) 4);
        // Entry 1: GPSLatitudeRef (0x0001, ASCII = 2, count = 2, 'N\0\0\0')
        buf.putShort((short) 0x0001).putShort((short) 2).putInt(2);
        buf.put((byte) (lat >= 0 ? 'N' : 'S')).put((byte) 0).put((byte) 0).put((byte) 0);
        // Entry 2: GPSLatitude (0x0002, RATIONAL = 5, count = 3, offset = offsetLat)
        buf.putShort((short) 0x0002).putShort((short) 5).putInt(3).putInt(offsetLat);
        // Entry 3: GPSLongitudeRef (0x0003, ASCII = 2, count = 2, 'E\0\0\0')
        buf.putShort((short) 0x0003).putShort((short) 2).putInt(2);
        buf.put((byte) (lon >= 0 ? 'E' : 'W')).put((byte) 0).put((byte) 0).put((byte) 0);
        // Entry 4: GPSLongitude (0x0004, RATIONAL = 5, count = 3, offset = offsetLon)
        buf.putShort((short) 0x0004).putShort((short) 5).putInt(3).putInt(offsetLon);
        buf.putInt(0);

        // Values area:
        // DateTimeOriginal string "YYYY:MM:DD HH:MM:SS\0" (20 bytes)
        String dtStr = String.format("%04d:%02d:%02d %02d:%02d:%02d\0", year, month, day, hour, min, sec);
        buf.put(dtStr.getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        // Latitude rationals: deg/1, min/1, sec/1000
        putDms(buf, Math.abs(lat));
        // Longitude rationals
        putDms(buf, Math.abs(lon));

        byte[] tiffBytes = buf.array();

        // Wrap into APP1 marker
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD8}); // SOI
        out.write(new byte[]{(byte) 0xFF, (byte) 0xE1}); // APP1 marker
        int app1Len = 2 + 6 + tiffBytes.length;
        out.write((app1Len >> 8) & 0xFF);
        out.write(app1Len & 0xFF);
        out.write("Exif\0\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.write(tiffBytes);
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD9}); // EOI

        return out.toByteArray();
    }

    private static void putDms(ByteBuffer buf, double val) {
        int deg = (int) val;
        double rem = (val - deg) * 60.0;
        int min = (int) rem;
        int sec = (int) Math.round((rem - min) * 60.0 * 1000.0);

        buf.putInt(deg).putInt(1);
        buf.putInt(min).putInt(1);
        buf.putInt(sec).putInt(1000);
    }
}
