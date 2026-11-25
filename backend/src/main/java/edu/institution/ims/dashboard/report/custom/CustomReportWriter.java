package edu.institution.ims.dashboard.report.custom;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

public class CustomReportWriter implements AutoCloseable {

    private final Path file;
    private final String format;
    private final List<CustomReportCatalog.Field> fields;
    private final String institutionName;
    private final String departmentName;
    private final String headerTitle;
    private final String reportTitle;
    private final String sessionName;
    private final String generatedAt;
    private final String filterSummary;
    private final String groupByFieldId;

    private SXSSFWorkbook workbook;
    private Sheet sheet;
    private BufferedWriter csv;
    private int rowCount = 0;

    private CellStyle titleStyle;
    private CellStyle subHeaderStyle;
    private CellStyle groupHeaderStyle;
    private CellStyle tableHeaderStyle;
    private CellStyle dataStyle;
    private CellStyle numStyle;
    private CellStyle dateStyle;

    public CustomReportWriter(Path file, String format, List<CustomReportCatalog.Field> fields,
                              String institutionName, String departmentName, String headerTitle,
                              String reportTitle, String sessionName, String generatedAt,
                              String filterSummary, String groupByFieldId) throws IOException {
        this.file = file;
        this.format = format.toUpperCase(Locale.ROOT);
        this.fields = fields;
        this.institutionName = institutionName;
        this.departmentName = departmentName;
        this.headerTitle = headerTitle;
        this.reportTitle = reportTitle;
        this.sessionName = sessionName;
        this.generatedAt = generatedAt;
        this.filterSummary = filterSummary;
        this.groupByFieldId = groupByFieldId;

        if ("CSV".equals(this.format)) {
            initCsv();
        } else {
            initExcel();
        }
    }

    private void initCsv() throws IOException {
        csv = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        csv.write('\ufeff'); // UTF-8 BOM

        List<String> headers = new ArrayList<>();
        if (!"NONE".equalsIgnoreCase(groupByFieldId)) {
            headers.add("Group");
        }
        for (var f : fields) {
            headers.add(f.label());
        }
        writeCsvRow(headers);
    }

    private void initExcel() {
        workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        sheet = workbook.createSheet("Report");
        sheet.getPrintSetup().setLandscape(true);

        createExcelStyles();

        int colSpan = Math.max(fields.size(), 4);

        // Row 0: Institution Name
        Row r0 = sheet.createRow(rowCount++);
        r0.setHeightInPoints(24);
        Cell c0 = r0.createCell(0);
        c0.setCellValue(institutionName);
        c0.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colSpan - 1));

        // Row 1: Department Name
        Row r1 = sheet.createRow(rowCount++);
        r1.setHeightInPoints(20);
        Cell c1 = r1.createCell(0);
        c1.setCellValue(departmentName);
        c1.setCellStyle(subHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, colSpan - 1));

        // Row 2: Common Header
        Row r2 = sheet.createRow(rowCount++);
        r2.setHeightInPoints(18);
        Cell c2 = r2.createCell(0);
        c2.setCellValue(headerTitle);
        c2.setCellStyle(subHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, colSpan - 1));

        // Row 3: Report Title
        Row r3 = sheet.createRow(rowCount++);
        r3.setHeightInPoints(22);
        Cell c3 = r3.createCell(0);
        c3.setCellValue(reportTitle);
        c3.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, colSpan - 1));

        // Row 4: Academic Session & Generated Date Metadata
        Row r4 = sheet.createRow(rowCount++);
        r4.setHeightInPoints(18);
        Cell c4 = r4.createCell(0);
        c4.setCellValue("Academic Session: " + (sessionName != null ? sessionName : "All Sessions") + "  |  Generated: " + generatedAt);
        sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, colSpan - 1));

        // Row 5: Filter Summary
        Row r5 = sheet.createRow(rowCount++);
        r5.setHeightInPoints(18);
        Cell c5 = r5.createCell(0);
        c5.setCellValue("Filters: " + (filterSummary != null ? filterSummary : "None"));
        sheet.addMergedRegion(new CellRangeAddress(5, 5, 0, colSpan - 1));

        // Row 6: Empty spacer
        sheet.createRow(rowCount++).setHeightInPoints(8);
    }

    private void createExcelStyles() {
        Font boldLarge = workbook.createFont();
        boldLarge.setBold(true);
        boldLarge.setFontHeightInPoints((short) 14);

        Font boldMedium = workbook.createFont();
        boldMedium.setBold(true);
        boldMedium.setFontHeightInPoints((short) 11);

        Font boldSmall = workbook.createFont();
        boldSmall.setBold(true);
        boldSmall.setFontHeightInPoints((short) 10);

        titleStyle = workbook.createCellStyle();
        titleStyle.setFont(boldLarge);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);

        subHeaderStyle = workbook.createCellStyle();
        subHeaderStyle.setFont(boldMedium);
        subHeaderStyle.setAlignment(HorizontalAlignment.CENTER);

        groupHeaderStyle = workbook.createCellStyle();
        groupHeaderStyle.setFont(boldMedium);
        groupHeaderStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
        groupHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        groupHeaderStyle.setBorderBottom(BorderStyle.THIN);

        tableHeaderStyle = workbook.createCellStyle();
        tableHeaderStyle.setFont(boldSmall);
        tableHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        tableHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tableHeaderStyle.setBorderBottom(BorderStyle.MEDIUM);
        tableHeaderStyle.setBorderTop(BorderStyle.THIN);
        tableHeaderStyle.setWrapText(true);

        dataStyle = workbook.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

        numStyle = workbook.createCellStyle();
        numStyle.cloneStyleFrom(dataStyle);
        numStyle.setAlignment(HorizontalAlignment.RIGHT);

        dateStyle = workbook.createCellStyle();
        dateStyle.cloneStyleFrom(dataStyle);
        DataFormat df = workbook.createDataFormat();
        dateStyle.setDataFormat(df.getFormat("yyyy-mm-dd"));
    }

    public void writeGroups(List<CustomReportResponse.GroupData> groups) throws IOException {
        if ("CSV".equals(format)) {
            if (groups == null || groups.isEmpty()) {
                writeCsvRow(List.of("No records match the selected report filters."));
                return;
            }
            for (var group : groups) {
                String groupVal = group.groupValue();
                for (var row : group.rows()) {
                    List<String> line = new ArrayList<>();
                    if (!"NONE".equalsIgnoreCase(groupByFieldId)) {
                        line.add(groupVal);
                    }
                    for (var field : fields) {
                        line.add(formatValue(row.get(field.id())));
                    }
                    writeCsvRow(line);
                }
            }
        } else {
            int colCount = Math.max(fields.size(), 1);

            if (groups == null || groups.isEmpty()) {
                // Table headers
                Row thRow = sheet.createRow(rowCount++);
                thRow.setHeightInPoints(24);
                for (int i = 0; i < fields.size(); i++) {
                    Cell thCell = thRow.createCell(i);
                    thCell.setCellValue(fields.get(i).label());
                    thCell.setCellStyle(tableHeaderStyle);
                }

                Row emptyRow = sheet.createRow(rowCount++);
                emptyRow.setHeightInPoints(22);
                Cell emptyCell = emptyRow.createCell(0);
                emptyCell.setCellValue("No records match the selected report filters.");
                emptyCell.setCellStyle(dataStyle);
                if (colCount > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(rowCount - 1, rowCount - 1, 0, colCount - 1));
                }
                return;
            }

            for (var group : groups) {
                if (!"NONE".equalsIgnoreCase(groupByFieldId)) {
                    // Group banner row
                    Row gRow = sheet.createRow(rowCount++);
                    gRow.setHeightInPoints(24);
                    Cell gCell = gRow.createCell(0);

                    StringBuilder gText = new StringBuilder();
                    gText.append(group.groupValue()).append(" (").append(group.recordCount()).append(" records)");
                    if (group.summary() != null && !group.summary().isEmpty()) {
                        gText.append(" | ");
                        List<String> sumParts = new ArrayList<>();
                        group.summary().forEach((k, v) -> {
                            if (v != null) sumParts.add(k + ": " + v);
                        });
                        gText.append(String.join(", ", sumParts));
                    }
                    gCell.setCellValue(gText.toString());
                    gCell.setCellStyle(groupHeaderStyle);
                    sheet.addMergedRegion(new CellRangeAddress(rowCount - 1, rowCount - 1, 0, colCount - 1));
                }

                // Table headers
                Row thRow = sheet.createRow(rowCount++);
                thRow.setHeightInPoints(24);
                for (int i = 0; i < fields.size(); i++) {
                    Cell thCell = thRow.createCell(i);
                    thCell.setCellValue(fields.get(i).label());
                    thCell.setCellStyle(tableHeaderStyle);
                }

                // Data rows
                for (var rowMap : group.rows()) {
                    Row dRow = sheet.createRow(rowCount++);
                    for (int i = 0; i < fields.size(); i++) {
                        var field = fields.get(i);
                        Cell dCell = dRow.createCell(i);
                        Object val = rowMap.get(field.id());

                        if (val == null) {
                            dCell.setCellValue("—");
                            dCell.setCellStyle(dataStyle);
                        } else if (val instanceof Number n) {
                            dCell.setCellValue(n.doubleValue());
                            dCell.setCellStyle(numStyle);
                        } else if (val instanceof Boolean b) {
                            dCell.setCellValue(b ? "Yes" : "No");
                            dCell.setCellStyle(dataStyle);
                        } else if (val instanceof LocalDate d) {
                            dCell.setCellValue(d.toString());
                            dCell.setCellStyle(dateStyle);
                        } else {
                            dCell.setCellValue(val.toString());
                            dCell.setCellStyle(dataStyle);
                        }
                    }
                }

                // Small spacing row after group
                sheet.createRow(rowCount++).setHeightInPoints(6);
            }

            // Adjust column widths
            for (int i = 0; i < fields.size(); i++) {
                int width = Math.max(14, Math.min(45, fields.get(i).label().length() + 4));
                sheet.setColumnWidth(i, width * 256);
            }
        }
    }

    private void writeCsvRow(List<String> cells) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i) == null ? "" : cells.get(i);
            // Formula injection protection
            if (!cell.isEmpty() && (cell.startsWith("=") || cell.startsWith("+") || cell.startsWith("-") || cell.startsWith("@"))) {
                cell = "'" + cell;
            }
            if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
                cell = "\"" + cell.replace("\"", "\"\"") + "\"";
            }
            sb.append(cell);
            if (i < cells.size() - 1) sb.append(",");
        }
        sb.append("\r\n");
        csv.write(sb.toString());
    }

    private String formatValue(Object val) {
        if (val == null) return "—";
        if (val instanceof Boolean b) return b ? "Yes" : "No";
        return val.toString();
    }

    @Override
    public void close() throws IOException {
        if (csv != null) {
            csv.flush();
            csv.close();
        }
        if (workbook != null) {
            try (var out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
            workbook.dispose();
            workbook.close();
        }
    }
}
