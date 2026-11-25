package edu.institution.ims.monthlyprogress;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;

@Component
public class ProgressReportPdfValidator {
    public record ValidatedPdf(String originalName, byte[] bytes) { public long size() { return bytes.length; } }
    private final long maxBytes;

    public ProgressReportPdfValidator(@Value("${app.progress-reports.max-pdf-size-bytes:10485760}") long maxBytes) {
        if (maxBytes <= 0 || maxBytes > 20L * 1024 * 1024) throw new IllegalArgumentException("Progress report PDF limit must be between 1 byte and 20 MiB");
        this.maxBytes = maxBytes;
    }
    public long maxBytes() { return maxBytes; }
    public ValidatedPdf validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("EMPTY_REPORT_PDF", "Choose a non-empty PDF to upload");
        if (file.getSize() > maxBytes) throw new MonthlyProgressException(HttpStatus.PAYLOAD_TOO_LARGE, "REPORT_PDF_TOO_LARGE", "Report PDFs must be 10 MiB or smaller");
        String supplied = Optional.ofNullable(file.getOriginalFilename()).orElse("").replace('\\', '/');
        var path = Paths.get(supplied).getFileName();
        String name = (path == null ? "" : path.toString()).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank() || name.length() > 255 || !name.toLowerCase(Locale.ROOT).endsWith(".pdf"))
            throw bad("INVALID_REPORT_PDF", "Choose a PDF file with a valid filename");
        if (!"application/pdf".equalsIgnoreCase(Optional.ofNullable(file.getContentType()).orElse("")))
            throw bad("REPORT_PDF_TYPE_MISMATCH", "The report attachment must be a PDF");
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes((int)Math.min(maxBytes + 1, Integer.MAX_VALUE));
            if (bytes.length > maxBytes) throw new MonthlyProgressException(HttpStatus.PAYLOAD_TOO_LARGE, "REPORT_PDF_TOO_LARGE", "Report PDFs must be 10 MiB or smaller");
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-')
                throw bad("REPORT_PDF_TYPE_MISMATCH", "The uploaded content is not a valid PDF file");
            return new ValidatedPdf(name, bytes);
        } catch (IOException exception) { throw bad("REPORT_PDF_READ_FAILED", "The uploaded PDF could not be read"); }
    }
    private static MonthlyProgressException bad(String code, String message) { return new MonthlyProgressException(HttpStatus.BAD_REQUEST, code, message); }
}
