package edu.institution.ims.monthlyprogress;

import edu.institution.ims.studentprofile.StudentProfile;
import edu.institution.ims.user.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import edu.institution.ims.reports.ReportPdfFonts;
import edu.institution.ims.reports.ReportPdfText;
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
            var fonts = ReportPdfFonts.load(document);
            Writer writer = new Writer(document, fonts);
            writer.center("ST. VINCENT PALLOTTI COLLEGE OF ENGINEERING & TECHNOLOGY, NAGPUR", fonts.bold(), 12);
            writer.center("Department of Computer Science and Engineering", fonts.regular(), 10);
            writer.space(10);
            writer.center("MONTHLY INTERNSHIP PROGRESS REPORT", fonts.bold(), 15);
            writer.center("Month " + report.getReportMonthNumber(), fonts.bold(), 12);
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
            writer.label("Last updated by student", DATE_TIME.format(report.getSubmittedAt()));

            writer.heading("WORK PERFORMED DURING THE MONTH");
            writer.paragraph(report.getWorkDescription(), 10);
            writer.label("Review Status", report.getStatus().name());
            writer.label("Revision", String.valueOf(report.getRevision()));
            if (report.getReviewedAt()!=null) {
                writer.label("Reviewed At", DATE_TIME.format(report.getReviewedAt()));
                writer.label("Faculty Mentor", report.getReviewer().getEmail());
            }
            if (report.getRejectionReason()!=null) writer.paragraph("Rejection reason: " + report.getRejectionReason(),10);
            if (report.getStatus()==MonthlyProgressStatus.PENDING_MENTOR_REVIEW)
                writer.paragraph("Sent to faculty mentor for review. This report is not yet approved.",10);
            writer.image(photo);
            writer.space(24);
            writer.paragraph("Student Signature: ______________________", 10);
            writer.paragraph("Faculty Mentor Signature: _______________", 10);
            writer.paragraph("III Cell Incharge: ______________________", 10);
            writer.close();
            writer.pageNumbers();
            document.save(output);
            return output.toByteArray();
        } catch (ReportPdfFonts.FontUnavailableException exception) {
            throw new MonthlyProgressException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "REPORT_FONT_UNAVAILABLE", exception.getMessage());
        } catch (ReportPdfText.UnrenderableTextException exception) {
            throw new MonthlyProgressException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "REPORT_TEXT_UNSUPPORTED", exception.getMessage());
        } catch (IOException exception) {
            throw new MonthlyProgressException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "PDF_GENERATION_FAILED", "The monthly progress PDF could not be generated");
        }
    }

    private static final class Writer {
        private final PDDocument document;
        private final ReportPdfFonts.Fonts fonts;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        Writer(PDDocument document, ReportPdfFonts.Fonts fonts) throws IOException { this.document = document; this.fonts = fonts; newPage(); }
        void newPage() throws IOException {
            if (stream != null) stream.close();
            page = new PDPage(PDRectangle.A4); document.addPage(page);
            stream = new PDPageContentStream(document, page); y = page.getMediaBox().getHeight() - MARGIN;
        }
        void ensure(float height) throws IOException { if (y - height < MARGIN) newPage(); }
        void space(float amount) throws IOException { ensure(amount); y -= amount; }
        void center(String value, PDFont font, float size) throws IOException {
            for (String line : ReportPdfText.wrap(value, font, size, WIDTH)) {
                ensure(size + 5); float textWidth = font.getStringWidth(line) / 1000 * size;
                text(line, MARGIN + (WIDTH - textWidth) / 2, font, size); y -= size + 5;
            }
        }
        void text(String value, float x, PDFont font, float size) throws IOException {
            stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(x, y); stream.showText(ReportPdfText.clean(value, font)); stream.endText();
        }
        void rule() throws IOException { space(5); stream.moveTo(MARGIN, y); stream.lineTo(MARGIN + WIDTH, y); stream.stroke(); space(14); }
        void heading(String value) throws IOException { ensure(72); space(12); text(value, MARGIN, fonts.bold(), 11); y -= 16; rule(); }
        void label(String label, String value) throws IOException {
            float labelWidth = 132;
            var labels = ReportPdfText.wrap(label + ":", fonts.bold(), 10, labelWidth - 8);
            var values = ReportPdfText.wrap(value, fonts.regular(), 10, WIDTH - labelWidth);
            int lines = Math.max(labels.size(), values.size());
            // Keep ordinary metadata rows together; oversized values may span pages.
            if (lines * 15 <= PDRectangle.A4.getHeight() - MARGIN * 2) ensure(lines * 15);
            for (int i = 0; i < lines; i++) {
                ensure(15);
                if (i < labels.size()) text(labels.get(i), MARGIN, fonts.bold(), 10);
                if (i < values.size()) text(values.get(i), MARGIN + labelWidth, fonts.regular(), 10);
                y -= 15;
            }
        }
        void paragraph(String value, float size) throws IOException {
            for (String line : ReportPdfText.wrap(value, fonts.regular(), size, WIDTH)) { ensure(size + 5); text(line, MARGIN, fonts.regular(), size); y -= size + 5; }
        }
        void image(byte[] bytes) throws IOException {
            PDImageXObject image = PDImageXObject.createFromByteArray(document, bytes, "monthly-progress-photo");
            // Keep the photo heading and signatures with the image when practical.
            ensure(286); heading("PHOTOGRAPH");
            float maxHeight = Math.min(280, y - MARGIN - 96);
            float scale = Math.min(WIDTH / image.getWidth(), maxHeight / image.getHeight());
            float width = image.getWidth() * scale; float height = image.getHeight() * scale;
            ensure(height + 15); stream.drawImage(image, MARGIN + (WIDTH - width) / 2, y - height, width, height); y -= height + 15;
        }
        void close() throws IOException { stream.close(); stream = null; }
        void pageNumbers() throws IOException {
            int count = document.getNumberOfPages();
            for (int i = 0; i < count; i++) {
                String footer = "Page " + (i + 1) + " of " + count;
                float width = fonts.regular().getStringWidth(footer) / 1000 * 9;
                try (var footerStream = new PDPageContentStream(document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footerStream.beginText(); footerStream.setFont(fonts.regular(), 9);
                    footerStream.newLineAtOffset((PDRectangle.A4.getWidth() - width) / 2, 28);
                    footerStream.showText(footer); footerStream.endText();
                }
            }
        }
    }
}
