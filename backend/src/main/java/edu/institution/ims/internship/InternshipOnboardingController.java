package edu.institution.ims.internship;

import edu.institution.ims.internship.dto.*;
import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/v1/student/internship-onboarding")
public class InternshipOnboardingController {
    private final InternshipOnboardingService service;
    public InternshipOnboardingController(InternshipOnboardingService service) { this.service = service; }
    @GetMapping public OnboardingResponse get(@AuthenticationPrincipal UserPrincipal principal) { return service.getOwn(principal); }
    @PostMapping("/secure") public ResponseEntity<OnboardingResponse> secure(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.secure(principal));
    }
    @PutMapping("/source") public OnboardingResponse source(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody SourceRequest request) {
        return service.selectSource(principal, request.source());
    }
    @PostMapping(value = "/documents/{documentType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OnboardingResponse upload(@AuthenticationPrincipal UserPrincipal principal, @PathVariable DocumentType documentType,
            @RequestPart("file") MultipartFile file) { return service.upload(principal, documentType, file); }
    @GetMapping("/documents") public java.util.List<DocumentResponse> documents(@AuthenticationPrincipal UserPrincipal principal) {
        return service.getOwn(principal).documents();
    }
    @GetMapping("/documents/{documentId}/download") public ResponseEntity<org.springframework.core.io.Resource> download(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long documentId) { return file(service.downloadOwn(principal, documentId)); }
    @GetMapping("/documents/{documentId}/view") public ResponseEntity<org.springframework.core.io.Resource> view(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long documentId) { return inlineFile(service.downloadOwn(principal, documentId)); }
    @PostMapping("/diary/confirm") public OnboardingResponse confirmDiary(@AuthenticationPrincipal UserPrincipal principal) { return service.confirmDiary(principal); }

    public static ResponseEntity<org.springframework.core.io.Resource> file(DocumentDownload download) {
        return file(download, false);
    }

    public static ResponseEntity<org.springframework.core.io.Resource> inlineFile(DocumentDownload download) {
        return file(download, true);
    }

    private static ResponseEntity<org.springframework.core.io.Resource> file(DocumentDownload download, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(download.originalFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType())).contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).header("X-Content-Type-Options", "nosniff").body(download.resource());
    }
}
