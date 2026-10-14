package edu.institution.ims.dashboard.report;

import edu.institution.ims.reports.ReportPdfFonts;
import edu.institution.ims.reports.ReportPdfText;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Keeps wide selections readable without discarding columns or shrinking the type. */
final class DashboardReportPdfWriter implements AutoCloseable {
    private static final float MARGIN = 48;
    private static final float WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
    private static final float BOTTOM = 56;
    private static final float SIZE = 11;
    private static final float LEADING = 15;
    private static final float PADDING = 7;
    private static final Color INK = new Color(25, 42, 65);
    private static final Color LINE = new Color(180, 192, 207);
    private final PDDocument document;
    private final ReportPdfFonts.Fonts fonts;
    private final Path file;
    private final List<DashboardReportCatalog.Field> fields;
    private final boolean tabular;
    private PDPageContentStream stream;
    private String title;
    private float y;
    private boolean tableHeader;
    private int records;

    DashboardReportPdfWriter(Path file, List<DashboardReportCatalog.Field> fields, String title) throws IOException {
        this.file = file; this.fields = fields; this.title = title; this.tabular = fields.size() <= 5;
        document = new PDDocument();
        try { fonts = ReportPdfFonts.load(document); }
        catch (IOException | RuntimeException error) { document.close(); throw error; }
    }

    void title(String title) { this.title = title; }

    void row(List<String> values, int record) throws IOException {
        if (stream == null) newPage();
        if (tabular) {
            ensureTableHeader();
            List<List<String>> lines = new ArrayList<>();
            float[] widths = new float[fields.size()]; Arrays.fill(widths, WIDTH / fields.size());
            for (int n = 0; n < fields.size(); n++)
                lines.add(ReportPdfText.wrap(values.get(n), fonts.regular(), SIZE, widths[n] - PADDING * 2));
            cells(lines, widths, fonts.regular(), record % 2 == 0 ? new Color(247, 249, 252) : Color.WHITE, true);
        } else {
            heading("Record " + record);
            for (int n = 0; n < fields.size(); n++) detail(fields.get(n).label(), values.get(n));
            y -= 12;
        }
        records++;
    }

    private void newPage() throws IOException {
        if (stream != null) stream.close();
        PDPage page = new PDPage(PDRectangle.A4); document.addPage(page);
        stream = new PDPageContentStream(document, page);
        y = page.getMediaBox().getHeight() - MARGIN;
        for (String line : ReportPdfText.wrap(title, fonts.bold(), 17, WIDTH)) {
            text(line, MARGIN, y - 17, fonts.bold(), 17); y -= 22;
        }
        text("Industry-Institute Interaction (III) Cell", MARGIN, y - 11, fonts.regular(), 11);
        y -= 23;
        stream.setStrokingColor(INK); stream.setLineWidth(.7f);
        stream.moveTo(MARGIN, y); stream.lineTo(MARGIN + WIDTH, y); stream.stroke();
        y -= 20; tableHeader = false;
    }

    private void ensureTableHeader() throws IOException {
        if (tableHeader) return;
        float[] widths = new float[fields.size()]; Arrays.fill(widths, WIDTH / fields.size());
        List<List<String>> labels = new ArrayList<>();
        for (var field : fields) labels.add(ReportPdfText.wrap(field.label(), fonts.bold(), SIZE, WIDTH / fields.size() - PADDING * 2));
        int lines = labels.stream().mapToInt(List::size).max().orElse(1);
        float height = lines * LEADING + PADDING * 2;
        // Catalog labels are bounded; leave space for at least one body line below them.
        if (y - height - LEADING - PADDING * 2 < BOTTOM) newPage();
        drawCells(labels, widths, 0, lines, fonts.bold(), new Color(233, 239, 247));
        tableHeader = true;
    }

    private void heading(String label) throws IOException {
        if (y - 62 < BOTTOM) newPage();
        text(label, MARGIN, y - 13, fonts.bold(), 13); y -= 27;
    }

    private void detail(String label, String value) throws IOException {
        float labelWidth = 150;
        cells(List.of(ReportPdfText.wrap(label, fonts.bold(), SIZE, labelWidth - PADDING * 2),
                      ReportPdfText.wrap(value, fonts.regular(), SIZE, WIDTH - labelWidth - PADDING * 2)),
                new float[] { labelWidth, WIDTH - labelWidth }, null, Color.WHITE, false);
    }

    /** Splits tall rows into page-sized slices and repeats their column headings. */
    private void cells(List<List<String>> lines, float[] widths, PDFont font, Color background, boolean inTable) throws IOException {
        int total = lines.stream().mapToInt(List::size).max().orElse(1);
        int offset = 0;
        while (offset < total) {
            int fits = (int) ((y - BOTTOM - PADDING * 2) / LEADING);
            if (fits < 1) {
                newPage();
                if (inTable) ensureTableHeader();
                fits = (int) ((y - BOTTOM - PADDING * 2) / LEADING);
            }
            int portion = Math.min(fits, total - offset);
            drawCells(lines, widths, offset, portion, font, background);
            offset += portion;
            if (offset < total) {
                newPage();
                if (inTable) ensureTableHeader();
                else {
                    text("Continued from previous page", MARGIN, y - 10, fonts.regular(), 10); y -= 21;
                }
            }
        }
    }

    private void drawCells(List<List<String>> lines, float[] widths, int offset, int count, PDFont font, Color background) throws IOException {
        float height = count * LEADING + PADDING * 2;
        float x = MARGIN;
        for (int n = 0; n < lines.size(); n++) {
            stream.setNonStrokingColor(font == null && n == 0 ? new Color(247, 249, 252) : background);
            stream.addRect(x, y - height, widths[n], height); stream.fill();
            stream.setStrokingColor(LINE); stream.setLineWidth(.4f);
            stream.addRect(x, y - height, widths[n], height); stream.stroke();
            PDFont cellFont = font != null ? font : n == 0 ? fonts.bold() : fonts.regular();
            int start = font == null && n == 0 && offset >= lines.get(n).size() ? 0 : offset;
            for (int line = 0; line < count && start + line < lines.get(n).size(); line++)
                text(lines.get(n).get(start + line), x + PADDING, y - PADDING - SIZE - line * LEADING, cellFont, SIZE);
            x += widths[n];
        }
        y -= height;
    }

    private void text(String text, float x, float baseline, PDFont font, float size) throws IOException {
        stream.setNonStrokingColor(INK);
        stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(x, baseline);
        stream.showText(ReportPdfText.clean(text, font)); stream.endText();
    }

    void finish(Map<String, String> metadata) throws IOException {
        if (stream == null) newPage();
        if (records == 0) {
            if (tabular) ensureTableHeader();
            y -= 16; text("No records match the selected filters.", MARGIN, y - SIZE, fonts.regular(), SIZE); y -= 24;
        }
        y -= 20; heading("Report details");
        for (var entry : metadata.entrySet()) detail(entry.getKey(), entry.getValue());
        stream.close(); stream = null;
        int pages = document.getNumberOfPages();
        document.getDocumentInformation().setTitle(title);
        document.getDocumentInformation().setCreator("Internship Management System");
        for (int n = 0; n < pages; n++) {
            PDPage page = document.getPage(n);
            try (PDPageContentStream footer = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                footer.setNonStrokingColor(INK);
                footer.beginText(); footer.setFont(fonts.regular(), 10); footer.newLineAtOffset(MARGIN, 34);
                footer.showText("Page " + (n + 1) + " of " + pages); footer.endText();
                String count = records + " record" + (records == 1 ? "" : "s");
                float width = fonts.regular().getStringWidth(count) / 1000 * 10;
                footer.beginText(); footer.setFont(fonts.regular(), 10); footer.newLineAtOffset(MARGIN + WIDTH - width, 34);
                footer.showText(count); footer.endText();
            }
        }
        document.save(file.toFile());
    }

    @Override public void close() throws IOException {
        try { if (stream != null) stream.close(); }
        finally { document.close(); }
    }
}
