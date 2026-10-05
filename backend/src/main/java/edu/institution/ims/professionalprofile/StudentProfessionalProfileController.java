package edu.institution.ims.professionalprofile;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/student")
public class StudentProfessionalProfileController {
    private final ProfessionalProfileService service;

    public StudentProfessionalProfileController(ProfessionalProfileService service) { this.service = service; }

    @GetMapping("/professional-profile")
    public ProfessionalProfileResponse profile(@AuthenticationPrincipal UserPrincipal principal) {
        return service.getOwn(principal);
    }

    @PutMapping("/professional-profile")
    public ProfessionalProfileResponse update(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProfessionalProfileRequest request) {
        return service.updateOwn(principal, request);
    }

    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfessionalProfileResponse upload(@AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        return service.uploadOwn(principal, file);
    }

    @GetMapping("/resume")
    public ResumeResponse resume(@AuthenticationPrincipal UserPrincipal principal) {
        return service.getOwnResume(principal);
    }

    @GetMapping("/resume/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@AuthenticationPrincipal UserPrincipal principal) {
        return file(service.downloadOwn(principal));
    }

    static ResponseEntity<org.springframework.core.io.Resource> file(ResumeDownload download) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalFilename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }
}
