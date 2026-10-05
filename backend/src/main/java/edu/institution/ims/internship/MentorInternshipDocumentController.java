package edu.institution.ims.internship;

import edu.institution.ims.internship.dto.OnboardingResponse;
import edu.institution.ims.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mentor/students/{studentId}/internship-onboarding")
public class MentorInternshipDocumentController {
    private final InternshipOnboardingService service;

    public MentorInternshipDocumentController(InternshipOnboardingService service) { this.service = service; }

    @GetMapping
    public OnboardingResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long studentId) {
        return service.getForMentor(principal, studentId);
    }

    @GetMapping("/documents/{documentId}/view")
    public ResponseEntity<org.springframework.core.io.Resource> view(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long studentId, @PathVariable Long documentId) {
        return InternshipOnboardingController.inlineFile(service.downloadForMentor(principal, studentId, documentId));
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long studentId, @PathVariable Long documentId) {
        return InternshipOnboardingController.file(service.downloadForMentor(principal, studentId, documentId));
    }
}
