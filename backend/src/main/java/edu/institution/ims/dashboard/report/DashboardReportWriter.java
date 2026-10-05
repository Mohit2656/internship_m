package edu.institution.ims.dashboard.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Both formats consume the same ordered columns and row values. */
final class DashboardReportWriter implements AutoCloseable {
    private final Path file;
    private final List<DashboardReportCatalog.Field> fields;
    private final ZoneId zone;
    private SXSSFWorkbook workbook;
    private Sheet students;
    private BufferedWriter csv;
    private final Map<String, CellStyle> styles = new HashMap<>();
    private int count;
    private boolean finished;

    DashboardReportWriter(Path file, DashboardReportRequest.Format format, List<DashboardReportCatalog.Field> fields, ZoneId zone) throws IOException {
        this.file = file; this.fields = fields; this.zone = zone;
        if (format == DashboardReportRequest.Format.CSV) {
            csv = Files.newBufferedWriter(file, StandardCharsets.UTF_8); csv.write('\ufeff');
            csvRow(fields.stream().map(DashboardReportCatalog.Field::label).toList());
        } else {
            workbook = new SXSSFWorkbook(100); workbook.setCompressTempFiles(true);
            students = workbook.createSheet("Students");
            students.createFreezePane(0, 1); students.setRepeatingRows(new CellRangeAddress(0, 0, -1, -1));
            students.getPrintSetup().setLandscape(true); students.setFitToPage(true);
            students.getPrintSetup().setFitWidth((short) 1); students.getPrintSetup().setFitHeight((short) 0);
            CellStyle headerStyle = headerStyle(); Row header = students.createRow(0); header.setHeightInPoints(44);
            for (int n = 0; n < fields.size(); n++) {
                Cell cell = header.createCell(n); cell.setCellValue(fields.get(n).label()); cell.setCellStyle(headerStyle);
                int width = switch (fields.get(n).kind()) { case DATE -> 16; case DATETIME -> 24; case NUMBER -> 20; default -> Math.min(48, Math.max(22, fields.get(n).label().length() + 3)); };
                students.setColumnWidth(n, width * 256);
            }
        }
    }
    void row(Map<String, Object> values) throws IOException {
        if (workbook != null && count >= 1_048_575) throw new IllegalArgumentException("This report exceeds the Excel row limit. Narrow the student filters.");
        if (csv != null) {
            List<String> cells = new ArrayList<>();
            for (var field : fields) cells.add(display(values.get(field.id())));
            csvRow(cells); count++; return;
        }
        Row row = students.createRow(++count); int lines = 1;
        for (int n = 0; n < fields.size(); n++) {
            var field = fields.get(n); Object value = values.get(field.id()); Cell cell = row.createCell(n);
            cell.setCellStyle(style(field, count % 2 == 0));
            if (value == null) continue;
            String text = display(value);
            if (text.length() > 32_767) throw new IllegalArgumentException("The '" + field.label() + "' cell exceeds Excel's 32,767 character limit. Use a narrower history date range or CSV.");
            if (field.kind() == DashboardReportCatalog.Kind.NUMBER && value instanceof Number number) cell.setCellValue(number.doubleValue());
            else if (field.kind() == DashboardReportCatalog.Kind.DATE) cell.setCellValue(value instanceof java.sql.Date date ? date.toLocalDate() : LocalDate.parse(text));
            else if (field.kind() == DashboardReportCatalog.Kind.DATETIME) cell.setCellValue(instant(value).atZone(zone).toLocalDateTime());
            else cell.setCellValue(text); // Always a string, never a formula from student-controlled content.
            lines = Math.max(lines, Math.min(20, text.split("\n", -1).length + text.length() / 80));
        }
        row.setHeightInPoints(Math.min(409, Math.max(24, lines * 15)));
    }
    private String display(Object value) {
        if (value instanceof OffsetDateTime || value instanceof Timestamp || value instanceof Instant)
            return instant(value).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX"));
        return DashboardReportRepository.text(value);
    }
    private static Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        if (value instanceof Instant instant) return instant;
        return Instant.parse(value.toString());
    }
    private void csvRow(List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) csv.write(',');
            String value = values.get(i);
            // Neutralize formulas even when prefixed by whitespace/control characters.
            String significant = value.stripLeading();
            boolean numeric = fields.get(i).kind() == DashboardReportCatalog.Kind.NUMBER && value.matches("-?\\d+(\\.\\d+)?");
            if (!numeric && ((!significant.isEmpty() && "=+-@".indexOf(significant.charAt(0)) >= 0)
                    || (!value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0))) value = "'" + value;
            csv.write('"'); csv.write(value.replace("\"", "\"\"")); csv.write('"');
        }
        csv.write("\r\n");
    }
    int count() { return count; }
    void finish(Map<String, String> metadata) throws IOException {
        if (workbook != null) {
            students.setAutoFilter(new CellRangeAddress(0, count, 0, fields.size() - 1));
            Sheet info = workbook.createSheet("Report Details"); workbook.setSheetOrder("Report Details", 0); workbook.setActiveSheet(0);
            info.setColumnWidth(0, 30 * 256); info.setColumnWidth(1, 100 * 256);
            Row title = info.createRow(0); title.setHeightInPoints(32);
            Cell cell = title.createCell(0); cell.setCellValue("Dashboard Student Report"); cell.setCellStyle(headerStyle());
            info.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
            int index = 2;
            for (var entry : metadata.entrySet()) {
                Row row = info.createRow(index++); row.setHeightInPoints(entry.getValue().length() > 100 ? 75 : 30);
                row.createCell(0).setCellValue(entry.getKey());
                Cell detail = row.createCell(1); detail.setCellValue(entry.getValue());
                var field = new DashboardReportCatalog.Field("metadata", "", "", DashboardReportCatalog.Kind.TEXT, false, null, false);
                detail.setCellStyle(style(field, false));
            }
            try (OutputStream output = Files.newOutputStream(file)) { workbook.write(output); }
        } else csv.flush();
        finished = true;
    }
    private CellStyle headerStyle() {
        return styles.computeIfAbsent("header", unused -> {
            CellStyle style = workbook.createCellStyle(); style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setWrapText(true); style.setVerticalAlignment(VerticalAlignment.CENTER);
            Font font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex()); font.setFontHeightInPoints((short) 11); style.setFont(font); return style;
        });
    }
    private CellStyle style(DashboardReportCatalog.Field field, boolean alternate) {
        String format = switch (field.kind()) { case DATE -> "yyyy-mm-dd"; case DATETIME -> "yyyy-mm-dd hh:mm:ss"; case NUMBER -> field.id().equals("stipend") ? "#,##0.00" : "0.######"; default -> "@"; };
        return styles.computeIfAbsent(format + alternate, unused -> {
            CellStyle style = workbook.createCellStyle(); style.setWrapText(true); style.setVerticalAlignment(VerticalAlignment.TOP);
            style.setDataFormat(workbook.createDataFormat().getFormat(format));
            if (alternate) { style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); }
            return style;
        });
    }
    @Override public void close() throws IOException {
        try { if (csv != null) csv.close(); if (workbook != null) workbook.close(); }
        finally { if (workbook != null) workbook.dispose(); if (!finished) Files.deleteIfExists(file); }
    }
}
