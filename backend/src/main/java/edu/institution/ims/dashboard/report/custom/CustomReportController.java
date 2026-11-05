package edu.institution.ims.dashboard.report.custom;

import edu.institution.ims.dashboard.report.DashboardReportService;
import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class CustomReportController {

    private final CustomReportService service;

    public CustomReportController(CustomReportService service) {
        this.service = service;
    }

    @GetMapping({"/api/v1/admin/reports/custom/catalog", "/api/v1/admin/dashboard/reports/custom/catalog"})
    public List<CustomReportCatalog.Field> catalog(@AuthenticationPrincipal UserPrincipal user) {
        return service.getCatalogFields(user);
    }

    @GetMapping({"/api/v1/admin/reports/custom/companies", "/api/v1/admin/dashboard/reports/custom/companies"})
    public List<String> companies(@AuthenticationPrincipal UserPrincipal user,
                                  @RequestParam(required = false) Long academicSessionId) {
        return service.getDistinctCompanies(user, academicSessionId);
    }

    @PostMapping({"/api/v1/admin/reports/custom/preview", "/api/v1/admin/dashboard/reports/custom/preview"})
    public CustomReportResponse preview(@AuthenticationPrincipal UserPrincipal user,
                                        @Valid @RequestBody CustomReportRequest request) {
        return service.preview(user, request);
    }

    @PostMapping({"/api/v1/admin/reports/custom/count", "/api/v1/admin/dashboard/reports/custom/count"})
    public java.util.Map<String, Object> count(@AuthenticationPrincipal UserPrincipal user,
                                               @Valid @RequestBody CustomReportRequest request) {
        return service.count(user, request);
    }

    @PostMapping({"/api/v1/admin/reports/custom/export", "/api/v1/admin/dashboard/reports/custom/export"})
    public ResponseEntity<Resource> export(@AuthenticationPrincipal UserPrincipal user,
                                           @Valid @RequestBody CustomReportRequest request) throws IOException {
        DashboardReportService.Download download = service.export(user, request);
        return downloadResponse(download);
    }

    @GetMapping({"/api/v1/admin/reports/custom/saved", "/api/v1/admin/reports/saved"})
    public List<SavedReportDto> listSaved(@AuthenticationPrincipal UserPrincipal user) {
        return service.listSavedReports(user);
    }

    @GetMapping({"/api/v1/admin/reports/custom/saved/{id}", "/api/v1/admin/reports/saved/{id}"})
    public SavedReportDto getSaved(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        return service.getSavedReport(user, id);
    }

    @PostMapping({"/api/v1/admin/reports/custom/saved", "/api/v1/admin/reports/saved"})
    public SavedReportDto saveReport(@AuthenticationPrincipal UserPrincipal user,
                                     @Valid @RequestBody SaveReportRequest request) {
        return service.saveReport(user, request);
    }

    @PutMapping({"/api/v1/admin/reports/custom/saved/{id}", "/api/v1/admin/reports/saved/{id}"})
    public SavedReportDto updateSaved(@AuthenticationPrincipal UserPrincipal user,
                                      @PathVariable Long id,
                                      @Valid @RequestBody SaveReportRequest request) {
        return service.updateReport(user, id, request);
    }

    @DeleteMapping({"/api/v1/admin/reports/custom/saved/{id}", "/api/v1/admin/reports/saved/{id}"})
    public ResponseEntity<Void> deleteSaved(@AuthenticationPrincipal UserPrincipal user,
                                            @PathVariable Long id) {
        service.deleteReport(user, id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Resource> downloadResponse(DashboardReportService.Download download) throws IOException {
        try {
            long length = Files.size(download.path());
            InputStream stream = new FilterInputStream(Files.newInputStream(download.path())) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { Files.deleteIfExists(download.path()); }
                }
            };
            String safeFilename = download.filename().replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "");
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(safeFilename)
                    .build();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentLength(length)
                    .contentType(MediaType.parseMediaType(download.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header("X-Report-Student-Count", Integer.toString(download.studentCount()))
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(stream));
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(download.path());
            throw error;
        }
    }
}
