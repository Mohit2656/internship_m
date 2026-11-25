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

/** All formats consume the same ordered columns and row values. */
final class DashboardReportWriter implements AutoCloseable {
    private final Path file;
    private final List<DashboardReportCatalog.Field> fields;
    private final ZoneId zone;
    private SXSSFWorkbook workbook;
    private Sheet students;
    private BufferedWriter csv;
    private DashboardReportPdfWriter pdf;
    private final Map<String, CellStyle> styles = new HashMap<>();
    private int count;
    private boolean finished;
    private int headerRow;
    private String reportTitle = "Dashboard Student Report";
    private String reference = "Main report ";
    private final Map<Integer,Double> totals = new HashMap<>();
    private boolean historical;
    private boolean hasSubTables;

    DashboardReportWriter(Path file, DashboardReportRequest.Format format, List<DashboardReportCatalog.Field> fields, ZoneId zone, String title, String reference) throws IOException {
        this(file, format, fields, zone);
        this.reportTitle=title; this.reference=reference; historical=true; styles.remove("header");
        if (pdf != null) pdf.title(title);
        if (workbook != null) {
            // The historical template uses merged title bands above the data table.
            students.removeRow(students.getRow(0)); headerRow=3;
            Row titleRow=students.createRow(0); titleRow.setHeightInPoints(32);
            Cell cell=titleRow.createCell(0);cell.setCellValue(title);cell.setCellStyle(bandStyle("FFFFFF"));
            if(fields.size()>1) students.addMergedRegion(new CellRangeAddress(0,0,0,fields.size()-1));
            Row cellRow=students.createRow(1);cellRow.setHeightInPoints(24);cell=cellRow.createCell(0);cell.setCellValue("Industry–Institute Interaction (III) Cell");cell.setCellStyle(bandStyle("B3CEFB"));
            if(fields.size()>1) students.addMergedRegion(new CellRangeAddress(1,1,0,fields.size()-1));
            Row header=students.createRow(headerRow);header.setHeightInPoints(54);
            for(int n=0;n<fields.size();n++) {cell=header.createCell(n);cell.setCellValue(fields.get(n).label());
                String fill=reference.startsWith("Attedance") ? (fields.get(n).kind()==DashboardReportCatalog.Kind.DATE || fields.get(n).kind()==DashboardReportCatalog.Kind.DATETIME ? "FFFF00" : Set.of("uid","studentName","company","studentId").contains(fields.get(n).id()) ? "FFFFFF" : "FBDAD7") : reference.startsWith("CDC") ? "FFFFFF" : "FBDAD7";
                cell.setCellStyle(bandStyle(fill));}
            students.createFreezePane(Math.min(2,fields.size()),headerRow+1);
            students.setRepeatingRows(new CellRangeAddress(0,headerRow,-1,-1));
        }
    }

    DashboardReportWriter(Path file, DashboardReportRequest.Format format, List<DashboardReportCatalog.Field> fields, ZoneId zone) throws IOException {
        this.file = file; this.fields = fields; this.zone = zone;
        if (format == DashboardReportRequest.Format.CSV) {
            csv = Files.newBufferedWriter(file, StandardCharsets.UTF_8); csv.write('\ufeff');
            csvRow(fields.stream().map(DashboardReportCatalog.Field::label).toList());
        } else if (format == DashboardReportRequest.Format.PDF) {
            pdf = new DashboardReportPdfWriter(file, fields, reportTitle);
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
        if (workbook != null && count >= 1_048_575 - headerRow) throw new IllegalArgumentException("This report exceeds the Excel row limit. Narrow the student filters.");
        if (csv != null) {
            List<String> cells = new ArrayList<>();
            for (var field : fields) cells.add(display(values.get(field.id())));
            csvRow(cells); count++; return;
        }
        if (pdf != null) {
            List<String> cells = new ArrayList<>();
            for (var field : fields) cells.add(display(values.get(field.id())));
            pdf.row(cells, ++count); return;
        }
        Row row = students.createRow(++count + headerRow); int lines = 1;
        for (int n = 0; n < fields.size(); n++) {
            var field = fields.get(n); Object value = values.get(field.id()); Cell cell = row.createCell(n);
            cell.setCellStyle(style(field, count % 2 == 0));
            if (value == null) continue;
            String text = display(value);
            if (text.length() > 32_767) throw new IllegalArgumentException("The '" + field.label() + "' cell exceeds Excel's 32,767 character limit. Use a narrower history date range or CSV.");
            if (field.kind() == DashboardReportCatalog.Kind.NUMBER && value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
                if (!Set.of("stipend","percentage","studentId","gpsLatitude","gpsLongitude","gpsAltitude","monthNumber").contains(field.id())) totals.merge(n,number.doubleValue(),Double::sum);
            }
            else if (field.kind() == DashboardReportCatalog.Kind.DATE) cell.setCellValue(value instanceof java.sql.Date date ? date.toLocalDate() : LocalDate.parse(text));
            else if (field.kind() == DashboardReportCatalog.Kind.DATETIME) cell.setCellValue(instant(value).atZone(zone).toLocalDateTime());
            else cell.setCellValue(text); // Always a string, never a formula from student-controlled content.
            double widthCharacters=Math.max(8,students.getColumnWidth(n)/256.0-3);
            int wrappedLines=Arrays.stream(text.split("\n",-1)).mapToInt(part->Math.max(1,(int)Math.ceil(part.length()/widthCharacters))).sum();
            lines=Math.max(lines,wrappedLines);
        }
        row.setHeightInPoints(Math.min(409, Math.max(24, lines * 17)));
    }
    void subTable(String title, List<DashboardReportCatalog.Field> subFields, List<Map<String, Object>> rows) throws IOException {
        hasSubTables = true;
        if (csv != null) {
            if (title != null && !title.isBlank()) {
                csv.write("\r\n\"" + title.replace("\"", "\"\"") + "\"\r\n");
            }
            csvRow(subFields.stream().map(DashboardReportCatalog.Field::label).toList(), subFields);
            for (var rVal : rows) {
                List<String> cells = new ArrayList<>();
                for (var f : subFields) cells.add(display(rVal.get(f.id())));
                csvRow(cells, subFields);
                count++;
            }
            return;
        }
        if (pdf != null) {
            pdf.subTable(title, subFields, rows);
            count += rows.size();
            return;
        }
        if (workbook != null) {
            if (title != null && !title.isBlank()) {
                count++;
                Row titleRow = students.createRow(count + headerRow);
                titleRow.setHeightInPoints(26);
                Cell tCell = titleRow.createCell(0);
                tCell.setCellValue(title);
                tCell.setCellStyle(bandStyle("B3CEFB"));
                if (subFields.size() > 1) {
                    students.addMergedRegion(new CellRangeAddress(count + headerRow, count + headerRow, 0, subFields.size() - 1));
                }
            }
            count++;
            Row hRow = students.createRow(count + headerRow);
            hRow.setHeightInPoints(36);
            for (int n = 0; n < subFields.size(); n++) {
                Cell c = hRow.createCell(n);
                c.setCellValue(subFields.get(n).label());
                c.setCellStyle(bandStyle("FBDAD7"));
                int width = switch (subFields.get(n).kind()) { case DATE -> 16; case DATETIME -> 24; case NUMBER -> 18; default -> Math.min(48, Math.max(22, subFields.get(n).label().length() + 3)); };
                students.setColumnWidth(n, Math.max(students.getColumnWidth(n), width * 256));
            }
            for (var rVal : rows) {
                count++;
                Row r = students.createRow(count + headerRow);
                boolean isTotal = Boolean.TRUE.equals(rVal.get("isTotal")) || "Total".equals(rVal.get("category")) || "Total".equals(rVal.get("company")) || "Total".equals(rVal.get("parameter")) || "Total".equals(rVal.get("range"));
                for (int n = 0; n < subFields.size(); n++) {
                    var f = subFields.get(n);
                    Object val = rVal.get(f.id());
                    Cell cell = r.createCell(n);
                    cell.setCellStyle(isTotal ? bandStyle("E9EFF7") : style(f, count % 2 == 0));
                    if (val != null) {
                        if (f.kind() == DashboardReportCatalog.Kind.NUMBER && val instanceof Number num) {
                            cell.setCellValue(num.doubleValue());
                        } else {
                            cell.setCellValue(display(val));
                        }
                    }
                }
                r.setHeightInPoints(24);
            }
        }
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
        csvRow(values, this.fields);
    }
    private void csvRow(List<String> values, List<DashboardReportCatalog.Field> rowFields) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) csv.write(',');
            String value = values.get(i);
            // Neutralize formulas even when prefixed by whitespace/control characters.
            String significant = value.stripLeading();
            boolean numeric = (rowFields != null && i < rowFields.size() && rowFields.get(i).kind() == DashboardReportCatalog.Kind.NUMBER) && value.matches("-?\\d+(\\.\\d+)?");
            if (!numeric && ((!significant.isEmpty() && "=+-@".indexOf(significant.charAt(0)) >= 0)
                    || (!value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0))) value = "'" + value;
            csv.write('"'); csv.write(value.replace("\"", "\"\"")); csv.write('"');
        }
        csv.write("\r\n");
    }
    int count() { return count; }
    void finish(Map<String, String> metadata) throws IOException {
        if (workbook != null) {
            if (!hasSubTables && fields.size() > 0 && count > 0) {
                students.setAutoFilter(new CellRangeAddress(headerRow, count + headerRow, 0, fields.size() - 1));
            }
            if(historical && !hasSubTables && !reference.startsWith("Attedance") && !reportTitle.contains("Comparison") && count>0 && !totals.isEmpty() && !reportTitle.equals("Stipend Distribution")) {
                Row total=students.createRow(count+headerRow+1);total.setHeightInPoints(28);
                for(int n=0;n<fields.size();n++) {Cell c=total.createCell(n);c.setCellStyle(bandStyle("BFBFBF"));if(n==0 && fields.get(n).kind()!=DashboardReportCatalog.Kind.NUMBER)c.setCellValue("Total");else if(totals.containsKey(n))c.setCellValue(totals.get(n));}
            }
            Sheet info = workbook.createSheet("Report Details"); if(!historical) workbook.setSheetOrder("Report Details", 0); workbook.setActiveSheet(0);
            info.setColumnWidth(0, 30 * 256); info.setColumnWidth(1, 100 * 256);
            Row title = info.createRow(0); title.setHeightInPoints(32);
            Cell cell = title.createCell(0); cell.setCellValue(reportTitle); cell.setCellStyle(headerStyle());
            info.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
            int index = 2;
            for (var entry : metadata.entrySet()) {
                Row row = info.createRow(index++); row.setHeightInPoints(Math.min(409,Math.max(30,17*(int)Math.ceil(entry.getValue().length()/90.0))));
                row.createCell(0).setCellValue(entry.getKey());
                Cell detail = row.createCell(1); detail.setCellValue(entry.getValue());
                var field = new DashboardReportCatalog.Field("metadata", "", "", DashboardReportCatalog.Kind.TEXT, false, null, false);
                detail.setCellStyle(style(field, false));
            }
            try (OutputStream output = Files.newOutputStream(file)) { workbook.write(output); }
        } else if (pdf != null) pdf.finish(metadata);
        else csv.flush();
        finished = true;
    }
    private CellStyle bandStyle(String hex) {
        CellStyle base=headerStyle();
        return styles.computeIfAbsent("band"+hex,unused->{
            CellStyle style=workbook.createCellStyle();style.cloneStyleFrom(base);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle)style).setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(java.util.HexFormat.of().parseHex(hex),new org.apache.poi.xssf.usermodel.DefaultIndexedColorMap()));
            return style;
        });
    }
    private CellStyle headerStyle() {
        return styles.computeIfAbsent("header", unused -> {
            CellStyle style = workbook.createCellStyle(); style.setFillForegroundColor(IndexedColors.YELLOW.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            if(historical && !reference.startsWith("Attedance")) {
                // The source workbook theme uses accent1 4285F4 and accent4 34A853.
                String hex=reference.startsWith("CDC")?"FFFFFF":"FBDAD7";
                ((org.apache.poi.xssf.usermodel.XSSFCellStyle)style).setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(java.util.HexFormat.of().parseHex(hex),new org.apache.poi.xssf.usermodel.DefaultIndexedColorMap()));
            }
            style.setWrapText(true); style.setVerticalAlignment(VerticalAlignment.CENTER);
            Font font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.BLACK.getIndex()); font.setFontName("Times New Roman"); font.setFontHeightInPoints((short) 12); style.setFont(font); style.setAlignment(HorizontalAlignment.CENTER);
            style.setBorderTop(BorderStyle.THIN);style.setBorderBottom(BorderStyle.THIN);style.setBorderLeft(BorderStyle.THIN);style.setBorderRight(BorderStyle.THIN);return style;
        });
    }
    private CellStyle style(DashboardReportCatalog.Field field, boolean alternate) {
        String format = switch (field.kind()) { case DATE -> "yyyy-mm-dd"; case DATETIME -> "yyyy-mm-dd hh:mm:ss"; case NUMBER -> Set.of("gpsLatitude","gpsLongitude").contains(field.id()) ? "0.#########" : field.id().equals("gpsAltitude") ? "0.######" : field.id().equals("stipend") ? "\"INR\" #,##0.00" : field.id().equals("percentage") ? "0.0\"%\"" : historical ? "#,##0" : "0.######"; default -> "@"; };
        return styles.computeIfAbsent(format + alternate, unused -> {
            CellStyle style = workbook.createCellStyle(); style.setWrapText(true); style.setVerticalAlignment(VerticalAlignment.TOP);
            style.setDataFormat(workbook.createDataFormat().getFormat(format));
            Font bodyFont=workbook.createFont();bodyFont.setFontName("Times New Roman");bodyFont.setFontHeightInPoints((short)12);style.setFont(bodyFont);
            style.setBorderTop(BorderStyle.THIN);style.setBorderBottom(BorderStyle.THIN);style.setBorderLeft(BorderStyle.THIN);style.setBorderRight(BorderStyle.THIN);
            return style;
        });
    }
    @Override public void close() throws IOException {
        try { if (csv != null) csv.close(); if (pdf != null) pdf.close(); if (workbook != null) workbook.close(); }
        finally { if (workbook != null) workbook.dispose(); if (!finished) Files.deleteIfExists(file); }
    }
}
