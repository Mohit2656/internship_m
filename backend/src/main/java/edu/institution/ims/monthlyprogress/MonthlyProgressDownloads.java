package edu.institution.ims.monthlyprogress;

import org.springframework.http.*;
import java.nio.charset.StandardCharsets;

public final class MonthlyProgressDownloads {
    private MonthlyProgressDownloads() {}

    public static ResponseEntity<?> response(ProgressFileDownload download, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(download.filename(), StandardCharsets.UTF_8).build();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size()).header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff").header(HttpHeaders.CACHE_CONTROL, "private, no-store");
        return download.bytes() == null ? builder.body(download.resource()) : builder.body(download.bytes());
    }
}
