package edu.institution.ims.monthlyprogress;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ProgressPhotoValidatorTest {
    private final ProgressPhotoValidator validator = new ProgressPhotoValidator(20971520);
    static byte[] fixture(boolean gps, boolean capture, int degrees) throws Exception {
        ByteBuffer t = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
        t.put((byte)'I').put((byte)'I').putShort((short)42).putInt(8);
        t.position(206);
        directory(t,8,List.of(ascii(0x10f,"Test Camera"),ascii(0x110,"Model, \"One\""),number(0x8769,62),number(0x8825,gps?92:0)));
        directory(t,62,capture?List.of(ascii(0x9003,"2026:10:06 16:02:00"),ascii(0x9011,"+05:30")):List.of());
        if(gps) directory(t,92,List.of(ascii(1,"N"),rationals(2,degrees,7,30),ascii(3,"E"),rationals(4,79,5,15),new Tag(5,1,1,new byte[]{0}),rationals(6,317),rationals(7,10,32,0),ascii(29,"2026:10:06")));
        else { t.putInt(8+2+3*12+8,0); t.putShort(8,(short)3); t.putInt(8+2+3*12,0); }
        byte[] jpeg=plain("jpeg"); ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(jpeg,0,2);out.write(0xff);out.write(0xe1);int length=t.position()+8;
        out.write(length>>>8);out.write(length&255);out.write("Exif\0\0".getBytes(StandardCharsets.US_ASCII));out.write(t.array(),0,t.position());out.write(jpeg,2,jpeg.length-2);return out.toByteArray();
    }
    private record Tag(int tag,int type,int count,byte[] data) {}
    private static Tag ascii(int tag,String value) {return new Tag(tag,2,value.length()+1,(value+"\0").getBytes(StandardCharsets.US_ASCII));}
    private static Tag number(int tag,int value) {return new Tag(tag,4,1,ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());}
    private static Tag rationals(int tag,int... values) {ByteBuffer b=ByteBuffer.allocate(values.length*8).order(ByteOrder.LITTLE_ENDIAN);for(int v:values)b.putInt(v).putInt(1);return new Tag(tag,5,values.length,b.array());}
    private static void directory(ByteBuffer b,int offset,List<Tag> tags) {
        b.putShort(offset,(short)tags.size());int entry=offset+2;
        for(Tag tag:tags){ b.putShort(entry,(short)tag.tag()).putShort(entry+2,(short)tag.type()).putInt(entry+4,tag.count());
            if(tag.data().length<=4){for(int n=0;n<tag.data().length;n++)b.put(entry+8+n,tag.data()[n]);}
            else {b.putInt(entry+8,b.position());b.put(tag.data());}entry+=12;
        }b.putInt(entry,0);
    }
    static byte[] plain(String format) throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(16,12,BufferedImage.TYPE_INT_RGB),format,out);return out.toByteArray();}
    private ValidatedProgressPhoto read(String name,byte[] bytes) {return validator.validate(new MockMultipartFile("photo",name,name.endsWith("png")?"image/png":"image/jpeg",bytes));}
    @Test void gpsMetadataIsOptionalAndNeverDeterminesReviewStatus() throws Exception {
        for(var photo:List.of(read("gps.jpg",fixture(true,true,21)),read("invalid-gps.jpeg",fixture(true,false,91)),read("plain.jpg",plain("jpeg")),read("plain.jpeg",plain("jpeg")),read("plain.png",plain("png")))) {
            assertThat(photo.geoVerificationStatus()).isNull(); assertThat(photo.geoVerificationMessage()).isNull();
            assertThat(photo.latitude()).isNull(); assertThat(photo.longitude()).isNull(); assertThat(photo.capturedAt()).isNull();
            assertThat(photo.imageWidth()).isEqualTo(16); assertThat(photo.imageHeight()).isEqualTo(12);
        }
    }
    @Test void hashTracksExactBytesAndChangesWithImageMetadata() throws Exception {
        byte[] source=fixture(true,true,21);var original=read("one.jpg",source);assertThat(original.bytes()).isEqualTo(source);
        assertThat(original.imageSha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source)));
        assertThat(read("same.jpg",source).imageSha256()).isEqualTo(original.imageSha256());assertThat(read("different.jpg",fixture(true,true,22)).imageSha256()).isNotEqualTo(original.imageSha256());
    }
    @Test void corruptAndOversizedFilesFailCleanly() {
        assertThatThrownBy(()->read("corrupt.jpg",new byte[]{(byte)255,(byte)216,1,(byte)255,(byte)217})).isInstanceOf(MonthlyProgressException.class);
        assertThatThrownBy(()->read("oversize.jpg",new byte[20971521])).isInstanceOfSatisfying(MonthlyProgressException.class,e->{assertThat(e.getCode()).isEqualTo("PHOTO_TOO_LARGE");assertThat(e.getStatus().value()).isEqualTo(413);});
    }
    @Test void validCameraPaddingDoesNotMakeJpegMalformed() throws Exception {
        byte[] jpeg = fixture(true, true, 21);
        byte[] padded = Arrays.copyOf(jpeg, jpeg.length + 128);
        assertThat(read("camera.jpeg", padded).geoVerificationStatus()).isNull();
        assertThat(read("camera.jpeg", padded).bytes()).isEqualTo(padded);
    }
    @Test void truncatedAndDisguisedFilesAreRejected() throws Exception {
        for (String format : List.of("jpeg", "png")) {
            byte[] source = plain(format);
            String name = format.equals("png") ? "photo.png" : "photo.jpg";
            assertThatThrownBy(() -> read(name, Arrays.copyOf(source, source.length - 8))).isInstanceOf(MonthlyProgressException.class);
            byte[] padded = Arrays.copyOf(source, source.length + 2); padded[source.length] = 'M'; padded[source.length + 1] = 'Z';
            assertThatThrownBy(() -> read(name, padded)).isInstanceOf(MonthlyProgressException.class);
        }
        assertThatThrownBy(() -> read("disguised.jpg", plain("png"))).isInstanceOf(MonthlyProgressException.class);
        byte[] png = plain("png"); png[png.length - 1] ^= 1;
        assertThatThrownBy(() -> read("bad-crc.png", png)).isInstanceOf(MonthlyProgressException.class);
    }
    @Test void jpegAboveOldFiveMiBLimitAndMissingBrowserMimeAreAccepted() throws Exception {
        byte[] jpeg = plain("jpeg"); ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(jpeg, 0, 2);
        for (int i = 0; i < 90; i++) { out.write(new byte[]{(byte)255, (byte)239, (byte)255, (byte)255}); out.write(new byte[65533]); }
        out.write(jpeg, 2, jpeg.length - 2);
        var photo = validator.validate(new MockMultipartFile("photo", "large.JPEG", "application/octet-stream", out.toByteArray()));
        assertThat(photo.size()).isGreaterThan(5L * 1024 * 1024);
        assertThat(photo.geoVerificationStatus()).isNull();
        assertThat(validator.validate(new MockMultipartFile("photo", "empty-mime.jpg", null, jpeg)).mimeType()).isEqualTo("image/jpeg");
    }
    @Test void writeDeterministicLocalBrowserFixtures() throws Exception {
        Path root=Path.of("target","exif-fixtures");Files.createDirectories(root);
        Files.write(root.resolve("valid-gps.jpg"),fixture(true,true,21));Files.write(root.resolve("invalid-gps.jpg"),fixture(true,true,91));
        Files.write(root.resolve("missing-capture.jpg"),fixture(true,false,21));Files.write(root.resolve("no-gps.jpg"),plain("jpeg"));Files.write(root.resolve("no-exif.png"),plain("png"));
        Files.write(root.resolve("corrupt.jpg"),new byte[]{1,2,3});Files.write(root.resolve("oversize.jpg"),new byte[20971521]);
    }
}
