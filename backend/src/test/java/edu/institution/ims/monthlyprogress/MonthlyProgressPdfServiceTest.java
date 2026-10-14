package edu.institution.ims.monthlyprogress;

import edu.institution.ims.internship.*;
import edu.institution.ims.reports.ReportPdfFonts;
import edu.institution.ims.user.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthlyProgressPdfServiceTest {
    @Test void shortAndLongReportsUseStandardPdfFontsAndKeepAllTextInsidePages() throws Exception {
        for (boolean longReport : new boolean[]{false, true}) {
            String work = longReport ? "Wide-token: " + "W".repeat(2200) + "\nFinal work paragraph." : "Implemented and verified attendance reporting.";
            var report = report(work);
            byte[] bytes = new MonthlyProgressPdfService().generate(report, photo());
            try (var document = PDDocument.load(bytes)) {
                String text = new PDFTextStripper().getText(document);
                assertThat(text).contains("MONTHLY INTERNSHIP PROGRESS REPORT", "Student Zoë", "APPROVED",
                        "Last updated by student", "Mentor@example.edu", "Faculty Mentor Signature", "Page 1 of");
                if (longReport) {
                    assertThat(document.getNumberOfPages()).isGreaterThan(1);
                    assertThat(text).contains("Final work paragraph.");
                    assertThat(text.replaceAll("Page \\d+ of \\d+", "").replaceAll("\\s+", "")).contains("W".repeat(2200));
                }
                for (var page : document.getPages()) for (var name : page.getResources().getFontNames()) {
                    var font = page.getResources().getFont(name);
                    assertThat(font.isEmbedded()).isFalse();
                    assertThat(font.getName()).isIn("Times-Roman", "Times-Bold");
                }
                new PDFTextStripper() {
                    @Override protected void processTextPosition(TextPosition position) {
                        assertThat(position.getXDirAdj()).isGreaterThanOrEqualTo(51f);
                        assertThat(position.getXDirAdj() + position.getWidthDirAdj()).isLessThanOrEqualTo(545f);
                        assertThat(position.getYDirAdj()).isBetween(40f, 816f);
                        super.processTextPosition(position);
                    }
                }.getText(document);
                Path directory = Path.of("target", "pdf-report-review");
                Files.createDirectories(directory);
                String prefix = longReport ? "monthly-long" : "monthly-short";
                Files.write(directory.resolve(prefix + ".pdf"), bytes);
                var renderer = new PDFRenderer(document);
                for (int i = 0; i < document.getNumberOfPages(); i++)
                    ImageIO.write(renderer.renderImageWithDPI(i, 100), "png", directory.resolve(prefix + "-" + (i + 1) + ".png").toFile());
            }
        }
    }

    @Test void standardPdfFontsWorkWithAStaleFontDirectorySetting() throws Exception {
        String previous = System.getProperty("report.pdf.font-directory");
        System.setProperty("report.pdf.font-directory", Path.of("target", "no-report-fonts").toAbsolutePath().toString());
        try (var document = new PDDocument()) {
            var fonts = ReportPdfFonts.load(document);
            assertThat(fonts.regular().getName()).isEqualTo("Times-Roman");
            assertThat(fonts.bold().getName()).isEqualTo("Times-Bold");
        } finally {
            if (previous == null) System.clearProperty("report.pdf.font-directory");
            else System.setProperty("report.pdf.font-directory", previous);
        }
    }

    private MonthlyProgressReport report(String work) {
        var report = mock(MonthlyProgressReport.class);
        var internship = mock(InternshipDetails.class);
        var onboarding = mock(InternshipOnboarding.class);
        var student = mock(User.class);
        var account = mock(StudentAccount.class);
        var reviewer = mock(User.class);
        when(report.getStudentInternship()).thenReturn(internship);
        when(internship.getOnboarding()).thenReturn(onboarding);
        when(onboarding.getStudent()).thenReturn(student);
        when(student.getStudentAccount()).thenReturn(account);
        when(account.getStudentName()).thenReturn("Student Zoë");
        when(account.getUid()).thenReturn("UID-001");
        when(account.getSemester()).thenReturn(7);
        when(internship.getCompanyName()).thenReturn("Long Company " + "Company name ".repeat(20));
        when(internship.getInternshipMode()).thenReturn(InternshipMode.ONLINE);
        when(report.getPeriodStart()).thenReturn(LocalDate.of(2026, 9, 1));
        when(report.getPeriodEnd()).thenReturn(LocalDate.of(2026, 9, 30));
        when(report.getSubmittedAt()).thenReturn(Instant.parse("2026-10-01T06:30:00Z"));
        when(report.getReportMonthNumber()).thenReturn(1);
        when(report.getWorkDescription()).thenReturn(work);
        when(report.getStatus()).thenReturn(MonthlyProgressStatus.APPROVED);
        when(report.getRevision()).thenReturn(2);
        when(report.getReviewedAt()).thenReturn(Instant.parse("2026-10-02T06:30:00Z"));
        when(report.getReviewer()).thenReturn(reviewer);
        when(reviewer.getEmail()).thenReturn("Mentor@example.edu");
        return report;
    }

    private byte[] photo() throws Exception {
        BufferedImage image = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new java.awt.Color(225, 236, 247)); graphics.fillRect(0, 0, 600, 300);
        graphics.setColor(new java.awt.Color(29, 78, 216)); graphics.fillRect(40, 40, 520, 220); graphics.dispose();
        try (var out = new ByteArrayOutputStream()) { ImageIO.write(image, "png", out); return out.toByteArray(); }
    }
}
