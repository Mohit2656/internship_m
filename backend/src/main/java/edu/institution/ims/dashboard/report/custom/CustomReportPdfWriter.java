package edu.institution.ims.dashboard.report.custom;

import edu.institution.ims.reports.ReportPdfFonts;
import edu.institution.ims.reports.ReportPdfText;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class CustomReportPdfWriter implements AutoCloseable {

    private static final float MARGIN = 36;
    private static final float BOTTOM = 44;
    private static final float FONT_SIZE = 9;
    private static final float LEADING = 12;
    private static final float PADDING = 4;

    private static final Color INK = new Color(25, 42, 65);
    private static final Color HEADER_BG = new Color(230, 238, 248);
    private static final Color GROUP_BG = new Color(210, 226, 244);
    private static final Color ROW_ALT_BG = new Color(248, 250, 253);
    private static final Color BORDER_COLOR = new Color(205, 215, 225);

    private final Path file;
    private final List<CustomReportCatalog.Field> fields;
    private final String institutionName;
    private final String departmentName;
    private final String headerTitle;
    private final String reportTitle;
    private final String sessionName;
    private final String generatedAt;
    private final String filterSummary;
    private final String groupByFieldId;

    private final PDDocument document;
    private final ReportPdfFonts.Fonts fonts;
    private PDPageContentStream stream;
    private PDPage currentPage;
    private float y;
    private int pageNumber = 0;
    private final List<PDPage> pages = new ArrayList<>();

    public CustomReportPdfWriter(Path file, List<CustomReportCatalog.Field> fields,
                                 String institutionName, String departmentName, String headerTitle,
                                 String reportTitle, String sessionName, String generatedAt,
                                 String filterSummary, String groupByFieldId) throws IOException {
        this.file = file;
        this.fields = fields;
        this.institutionName = institutionName;
        this.departmentName = departmentName;
        this.headerTitle = headerTitle;
        this.reportTitle = reportTitle;
        this.sessionName = sessionName;
        this.generatedAt = generatedAt;
        this.filterSummary = filterSummary;
        this.groupByFieldId = groupByFieldId;

        this.document = new PDDocument();
        try {
            this.fonts = ReportPdfFonts.load(document);
        } catch (IOException | RuntimeException e) {
            document.close();
            throw e;
        }
    }

    private void newPage() throws IOException {
        if (stream != null) {
            stream.close();
        }
        // Landscape A4 for wide table presentation
        currentPage = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
        document.addPage(currentPage);
        pages.add(currentPage);
        pageNumber++;

        stream = new PDPageContentStream(document, currentPage);
        float width = currentPage.getMediaBox().getWidth() - MARGIN * 2;
        y = currentPage.getMediaBox().getHeight() - MARGIN;

        // Institution Header
        drawCenteredText(institutionName, fonts.bold(), 13, y);
        y -= 16;
        drawCenteredText(departmentName, fonts.bold(), 10, y);
        y -= 14;
        drawCenteredText(headerTitle, fonts.bold(), 10, y);
        y -= 16;

        // Prominent Report Title
        drawCenteredText(reportTitle, fonts.bold(), 12, y);
        y -= 16;

        // Metadata line
        String meta = "Academic Session: " + (sessionName != null ? sessionName : "All Sessions") + "  |  Generated: " + generatedAt;
        drawCenteredText(meta, fonts.regular(), 8, y);
        y -= 12;

        if (filterSummary != null && !filterSummary.isBlank()) {
            drawCenteredText("Filters: " + filterSummary, fonts.regular(), 8, y);
            y -= 12;
        }

        // Horizontal divider line
        stream.setStrokingColor(INK);
        stream.setLineWidth(0.8f);
        stream.moveTo(MARGIN, y);
        stream.lineTo(MARGIN + width, y);
        stream.stroke();
        y -= 12;
    }

    public void writeGroups(List<CustomReportResponse.GroupData> groups) throws IOException {
        newPage();
        float printableWidth = currentPage.getMediaBox().getWidth() - MARGIN * 2;
        float[] colWidths = calculateColumnWidths(printableWidth);

        if (groups == null || groups.isEmpty()) {
            drawTableHeader(colWidths, printableWidth);
            drawBanner("No records match the selected report filters.", printableWidth, ROW_ALT_BG, fonts.regular(), 9.0f);
        } else {
            for (var group : groups) {
                // Check space for group header and table header
                if (y - 50 < BOTTOM) {
                    newPage();
                }

                // Draw group header banner if grouped
                if (!"NONE".equalsIgnoreCase(groupByFieldId)) {
                    StringBuilder gbText = new StringBuilder();
                    gbText.append(group.groupValue()).append(" (").append(group.recordCount()).append(" records)");
                    if (group.summary() != null && !group.summary().isEmpty()) {
                        gbText.append("  |  ");
                        List<String> sumList = new ArrayList<>();
                        group.summary().forEach((k, v) -> {
                            if (v != null) sumList.add(k + ": " + v);
                        });
                        gbText.append(String.join(", ", sumList));
                    }

                    drawBanner(gbText.toString(), printableWidth, GROUP_BG, fonts.bold(), 9.5f);
                    y -= 4;
                }

                // Draw table header row
                drawTableHeader(colWidths, printableWidth);

                // Draw data rows
                int rIdx = 0;
                for (var rowMap : group.rows()) {
                    List<String> values = new ArrayList<>();
                    for (var field : fields) {
                        Object val = rowMap.get(field.id());
                        values.add(val == null ? "-" : val instanceof Boolean b ? (b ? "Yes" : "No") : val.toString());
                    }

                    float rowHeight = calculateRowHeight(values, colWidths);
                    if (y - rowHeight < BOTTOM) {
                        newPage();
                        drawTableHeader(colWidths, printableWidth);
                    }

                    drawRow(values, colWidths, rowHeight, rIdx % 2 == 1 ? ROW_ALT_BG : Color.WHITE);
                    rIdx++;
                }

                y -= 10; // Spacing between groups
            }
        }

        // Draw signatures block if space allows, or on a clean page
        if (y - 50 < BOTTOM) {
            newPage();
        }
        drawSignaturesBlock(printableWidth);

        // Add page numbers on all pages
        addPageNumbers();
    }

    private void drawTableHeader(float[] colWidths, float width) throws IOException {
        List<String> headers = fields.stream().map(CustomReportCatalog.Field::label).toList();
        float height = calculateRowHeight(headers, colWidths);

        drawRow(headers, colWidths, height, HEADER_BG, fonts.bold(), true);
    }

    private void drawRow(List<String> cells, float[] colWidths, float rowHeight, Color bg) throws IOException {
        drawRow(cells, colWidths, rowHeight, bg, fonts.regular(), false);
    }

    private void drawRow(List<String> cells, float[] colWidths, float rowHeight, Color bg, PDFont font, boolean isHeader) throws IOException {
        float x = MARGIN;

        // Background
        stream.setNonStrokingColor(bg);
        stream.addRect(x, y - rowHeight, sum(colWidths), rowHeight);
        stream.fill();

        // Border
        stream.setStrokingColor(BORDER_COLOR);
        stream.setLineWidth(0.5f);
        stream.addRect(x, y - rowHeight, sum(colWidths), rowHeight);
        stream.stroke();

        // Cell content & vertical dividers
        for (int i = 0; i < cells.size(); i++) {
            float colW = colWidths[i];
            String text = cells.get(i);

            List<String> wrapped = ReportPdfText.wrap(text, font, FONT_SIZE, colW - PADDING * 2);
            float textY = y - PADDING - FONT_SIZE;
            for (String line : wrapped) {
                drawText(line, x + PADDING, textY, font, FONT_SIZE);
                textY -= LEADING;
            }

            // Right vertical divider
            stream.setStrokingColor(BORDER_COLOR);
            stream.setLineWidth(0.5f);
            stream.moveTo(x + colW, y);
            stream.lineTo(x + colW, y - rowHeight);
            stream.stroke();

            x += colW;
        }

        y -= rowHeight;
    }

    private void drawBanner(String text, float width, Color bg, PDFont font, float size) throws IOException {
        float height = 18;
        stream.setNonStrokingColor(bg);
        stream.addRect(MARGIN, y - height, width, height);
        stream.fill();

        stream.setStrokingColor(BORDER_COLOR);
        stream.setLineWidth(0.7f);
        stream.addRect(MARGIN, y - height, width, height);
        stream.stroke();

        drawText(text, MARGIN + PADDING + 2, y - height + 5, font, size);
        y -= height;
    }

    private void drawSignaturesBlock(float width) throws IOException {
        y -= 20;
        float third = width / 3;

        stream.setStrokingColor(INK);
        stream.setLineWidth(0.6f);

        // Line 1
        stream.moveTo(MARGIN + 20, y);
        stream.lineTo(MARGIN + third - 20, y);
        stream.stroke();
        drawCenteredInBox("Prepared by: III Cell Incharge", fonts.regular(), 8.5f, MARGIN, MARGIN + third, y - 12);

        // Line 2
        stream.moveTo(MARGIN + third + 20, y);
        stream.lineTo(MARGIN + third * 2 - 20, y);
        stream.stroke();
        drawCenteredInBox("Verified by: Head of Department", fonts.regular(), 8.5f, MARGIN + third, MARGIN + third * 2, y - 12);

        // Line 3
        stream.moveTo(MARGIN + third * 2 + 20, y);
        stream.lineTo(MARGIN + width - 20, y);
        stream.stroke();
        drawCenteredInBox("Approved by: Principal", fonts.regular(), 8.5f, MARGIN + third * 2, MARGIN + width, y - 12);

        y -= 25;
    }

    private void addPageNumbers() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
        }
        int total = pages.size();
        for (int i = 0; i < total; i++) {
            PDPage page = pages.get(i);
            try (PDPageContentStream s = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                String footer = "Page " + (i + 1) + " of " + total;
                float width = fonts.regular().getStringWidth(footer) / 1000 * 8;
                float x = page.getMediaBox().getWidth() / 2 - width / 2;
                s.beginText();
                s.setFont(fonts.regular(), 8);
                s.setNonStrokingColor(INK);
                s.newLineAtOffset(x, 18);
                s.showText(footer);
                s.endText();
            }
        }
    }

    private String sanitize(String text, PDFont font) {
        if (text == null) return "";
        String clean = text
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2022', '*')
                .replace("\u2026", "...")
                .replace('\u00A0', ' ');
        StringBuilder sb = new StringBuilder();
        for (char c : clean.toCharArray()) {
            try {
                font.encode(String.valueOf(c));
                sb.append(c);
            } catch (Exception e) {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private float calculateRowHeight(List<String> cells, float[] colWidths) throws IOException {
        int maxLines = 1;
        for (int i = 0; i < cells.size(); i++) {
            String safe = sanitize(cells.get(i), fonts.regular());
            List<String> wrapped = ReportPdfText.wrap(safe, fonts.regular(), FONT_SIZE, Math.max(colWidths[i] - PADDING * 2, 8));
            maxLines = Math.max(maxLines, wrapped.size());
        }
        return maxLines * LEADING + PADDING * 2;
    }

    private float[] calculateColumnWidths(float totalWidth) {
        int count = Math.max(fields.size(), 1);
        float[] widths = new float[count];
        float baseWidth = totalWidth / count;
        Arrays.fill(widths, baseWidth);
        return widths;
    }

    private void drawText(String text, float x, float y, PDFont font, float size) throws IOException {
        String safeText = sanitize(text, font);
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(INK);
        stream.newLineAtOffset(x, y);
        stream.showText(safeText);
        stream.endText();
    }

    private void drawCenteredText(String text, PDFont font, float size, float y) throws IOException {
        String safeText = sanitize(text, font);
        float pageWidth = currentPage.getMediaBox().getWidth();
        float textWidth = font.getStringWidth(safeText) / 1000 * size;
        drawText(safeText, (pageWidth - textWidth) / 2, y, font, size);
    }

    private void drawCenteredInBox(String text, PDFont font, float size, float xStart, float xEnd, float y) throws IOException {
        String safeText = sanitize(text, font);
        float textWidth = font.getStringWidth(safeText) / 1000 * size;
        float x = xStart + ((xEnd - xStart) - textWidth) / 2;
        drawText(safeText, x, y, font, size);
    }

    private float sum(float[] arr) {
        float s = 0;
        for (float v : arr) s += v;
        return s;
    }

    @Override
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
        }
        document.save(file.toFile());
        document.close();
    }
}
