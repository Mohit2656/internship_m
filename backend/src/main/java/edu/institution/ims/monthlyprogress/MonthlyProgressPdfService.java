package edu.institution.ims.monthlyprogress;

import edu.institution.ims.studentprofile.StudentProfile;
import edu.institution.ims.user.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import java.io.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MonthlyProgressPdfService {
    private static final float MARGIN = 52;
    private static final float WIDTH = PDRectangle.A4.getWidth() - (MARGIN * 2);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    public byte[] generate(MonthlyProgressReport report, byte[] photo) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Writer writer = new Writer(document);
            writer.center("ST. VINCENT PALLOTTI COLLEGE OF ENGINEERING & TECHNOLOGY, NAGPUR", PDType1Font.HELVETICA_BOLD, 12);
            writer.center("Department of Computer Science and Engineering", PDType1Font.HELVETICA, 10);
            writer.space(10);
            writer.center("MONTHLY INTERNSHIP PROGRESS REPORT", PDType1Font.HELVETICA_BOLD, 15);
            writer.center("Month " + report.getReportMonthNumber(), PDType1Font.HELVETICA_BOLD, 12);
            writer.rule();

            User student = report.getStudentInternship().getOnboarding().getStudent();
            StudentProfile profile = student.getStudentProfile();
            StudentAccount account = student.getStudentAccount();
            String name = profile != null ? profile.getStudentName() : account == null ? "Not available" : account.getStudentName();
            String uid = profile != null ? profile.getUid() : account == null ? "Not available" : account.getUid();
            String branch = profile == null ? "Not available" : profile.getBranch().getLabel();
            String semester = profile != null ? String.valueOf(profile.getSemester()) : account == null || account.getSemester() == null ? "Not available" : String.valueOf(account.getSemester());
            writer.label("Student Name", name);
            writer.label("UID", uid);
            writer.label("Branch", branch);
            writer.label("Semester", semester);
            writer.space(5);
            writer.label("Company", report.getStudentInternship().getCompanyName());
            writer.label("Internship Mode", report.getStudentInternship().getInternshipMode().getLabel());
            writer.label("Reporting Period", DATE.format(report.getPeriodStart()) + " - " + DATE.format(report.getPeriodEnd()));
            writer.label("Submission Date", DATE_TIME.format(report.getSubmittedAt()));

            writer.heading("WORK PERFORMED DURING THE MONTH");
            writer.paragraph(report.getWorkDescription(), 10);
            writer.heading("GEO-TAGGED PHOTOGRAPH");
            writer.image(photo);
            writer.label("Capture Date/Time", report.getExifCapturedAt() == null ? "Not available" : DATE_TIME.format(report.getExifCapturedAt()));
            writer.label("Latitude", report.getExifLatitude() == null ? "Not available" : report.getExifLatitude().toPlainString());
            writer.label("Longitude", report.getExifLongitude() == null ? "Not available" : report.getExifLongitude().toPlainString());
            writer.label("Location", report.getLocationName() == null ? "Not available" : report.getLocationName());
            writer.space(24);
            writer.paragraph("Student Signature: ______________________", 10);
            writer.paragraph("Faculty Mentor Signature: _______________", 10);
            writer.paragraph("III Cell Incharge: ______________________", 10);
            writer.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new MonthlyProgressException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "PDF_GENERATION_FAILED", "The monthly progress PDF could not be generated");
        }
    }

    private static final class Writer {
        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        Writer(PDDocument document) throws IOException { this.document = document; newPage(); }
        void newPage() throws IOException {
            if (stream != null) stream.close();
            page = new PDPage(PDRectangle.A4); document.addPage(page);
            stream = new PDPageContentStream(document, page); y = page.getMediaBox().getHeight() - MARGIN;
        }
        void ensure(float height) throws IOException { if (y - height < MARGIN) newPage(); }
        void space(float amount) throws IOException { ensure(amount); y -= amount; }
        void center(String text, PDType1Font font, float size) throws IOException {
            ensure(size + 5); float textWidth = font.getStringWidth(ascii(text)) / 1000 * size;
            text(text, MARGIN + Math.max(0, (WIDTH - textWidth) / 2), font, size); y -= size + 5;
        }
        void text(String value, float x, PDType1Font font, float size) throws IOException {
            stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(x, y); stream.showText(ascii(value)); stream.endText();
        }
        void rule() throws IOException { space(5); stream.moveTo(MARGIN, y); stream.lineTo(MARGIN + WIDTH, y); stream.stroke(); space(14); }
        void heading(String value) throws IOException { space(12); ensure(30); text(value, MARGIN, PDType1Font.HELVETICA_BOLD, 11); y -= 16; rule(); }
        void label(String label, String value) throws IOException {
            ensure(17); text(label + ":", MARGIN, PDType1Font.HELVETICA_BOLD, 10); text(value, MARGIN + 120, PDType1Font.HELVETICA, 10); y -= 17;
        }
        void paragraph(String value, float size) throws IOException {
            for (String line : wrap(value, 92)) { ensure(size + 5); text(line, MARGIN, PDType1Font.HELVETICA, size); y -= size + 5; }
        }
        void image(byte[] bytes) throws IOException {
            PDImageXObject image = PDImageXObject.createFromByteArray(document, bytes, "monthly-progress-photo");
            float maxHeight = 280; float scale = Math.min(WIDTH / image.getWidth(), maxHeight / image.getHeight());
            float width = image.getWidth() * scale; float height = image.getHeight() * scale;
            ensure(height + 15); stream.drawImage(image, MARGIN + (WIDTH - width) / 2, y - height, width, height); y -= height + 15;
        }
        void close() throws IOException { stream.close(); stream = null; }
        private static List<String> wrap(String value, int max) {
            List<String> lines = new ArrayList<>();
            for (String paragraph : value.replace('\r', '\n').split("\\n")) {
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.trim().split("\\s+")) {
                    if (line.length() > 0 && line.length() + word.length() + 1 > max) { lines.add(line.toString()); line.setLength(0); }
                    if (line.length() > 0) line.append(' '); line.append(word);
                }
                if (line.length() > 0) lines.add(line.toString());
                if (paragraph.isBlank()) lines.add("");
            }
            return lines;
        }
        private static String ascii(String value) { return value.replace('\u2013', '-').replace('\u2014', '-').replaceAll("[^\\x20-\\x7E]", "?"); }
    }
}
