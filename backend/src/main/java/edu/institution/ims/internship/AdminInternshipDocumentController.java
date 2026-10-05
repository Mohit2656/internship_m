package edu.institution.ims.internship;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/students/{studentId}/internship-onboarding/documents")
public class AdminInternshipDocumentController {
    private final InternshipOnboardingService service;
    public AdminInternshipDocumentController(InternshipOnboardingService service) { this.service = service; }
    @GetMapping("/{documentId}/download") public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable Long studentId, @PathVariable Long documentId) {
        return InternshipOnboardingController.file(service.downloadForAdmin(studentId, documentId));
    }
    @GetMapping("/{documentId}/view") public ResponseEntity<org.springframework.core.io.Resource> view(
            @PathVariable Long studentId, @PathVariable Long documentId) {
        return InternshipOnboardingController.inlineFile(service.downloadForAdmin(studentId, documentId));
    }
}
