package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/students/{studentId}/internship/location")
public class AdminInternshipLocationController {
    private final InternshipLocationService service;

    public AdminInternshipLocationController(InternshipLocationService service) { this.service = service; }

    @GetMapping
    public InternshipLocationResponse get(@PathVariable Long studentId) {
        return service.getForAdmin(studentId);
    }

    @PutMapping
    public InternshipLocationResponse update(@PathVariable Long studentId,
            @Valid @RequestBody InternshipLocationRequest request) {
        return service.updateForAdmin(studentId, request);
    }

    @PostMapping("/confirm")
    public InternshipLocationResponse confirm(@PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.confirmForAdmin(studentId, principal);
    }
}
