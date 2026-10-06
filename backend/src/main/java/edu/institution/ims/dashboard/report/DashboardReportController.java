package edu.institution.ims.dashboard.report;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
@RequestMapping({"/api/v1/admin/dashboard/reports", "/api/v1/mentor/dashboard/reports", "/api/v1/admin/reports", "/api/v1/mentor/reports"})
public class DashboardReportController {
    private final DashboardReportService service;
    private final InstitutionalReportService institutional;
    public DashboardReportController(DashboardReportService service, InstitutionalReportService institutional) { this.service = service; this.institutional = institutional; }
    @GetMapping("/types")
    public InstitutionalReportService.Options types(@AuthenticationPrincipal UserPrincipal user) { return institutional.options(user); }
    @PostMapping("/table")
    public InstitutionalReportService.Table table(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody ReportTableRequest request) { return institutional.preview(user, request); }
    @PostMapping("/generate")
    public ResponseEntity<Resource> generate(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody ReportTableRequest request) throws IOException {
        return download(institutional.export(user,request));
    }
    @GetMapping("/options")
    public DashboardReportService.Options options(@AuthenticationPrincipal UserPrincipal user) { return service.options(user); }
    @PostMapping("/preview")
    public DashboardReportService.Preview preview(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody DashboardReportRequest request) {
        return service.preview(user, request);
    }
    @PostMapping("/export")
    public ResponseEntity<Resource> export(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody DashboardReportRequest request) throws IOException {
        // Generate before committing response headers so validation / generation failures remain JSON errors.
        return download(service.export(user, request));
    }
    private ResponseEntity<Resource> download(DashboardReportService.Download download) throws IOException {
        try {
            long length = Files.size(download.path());
            InputStream stream = new FilterInputStream(Files.newInputStream(download.path())) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { Files.deleteIfExists(download.path()); }
                }
            };
            // The normal Resource message converter streams the file and closes it on success or disconnect.
            // This follows existing download endpoints and preserves the synchronous security filter chain.
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentLength(length).contentType(MediaType.parseMediaType(download.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                    .header("X-Report-Student-Count", Integer.toString(download.studentCount()))
                    .header("X-Content-Type-Options", "nosniff").body(new InputStreamResource(stream));
        } catch (IOException | RuntimeException error) { Files.deleteIfExists(download.path()); throw error; }
    }
}
