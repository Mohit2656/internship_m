package edu.institution.ims.internship;

import edu.institution.ims.internship.dto.InternshipDetailsResponse;
import edu.institution.ims.internship.dto.InternshipLifecycleAdminRequest;
import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/internships")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInternshipLifecycleController {
    private final InternshipLifecycleAdminService service;

    public AdminInternshipLifecycleController(InternshipLifecycleAdminService service) {
        this.service = service;
    }

    @PostMapping("/{internshipId}/terminate")
    public InternshipDetailsResponse terminateEarly(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable Long internshipId,
            @Valid @RequestBody InternshipLifecycleAdminRequest request) {
        return service.terminateEarly(admin, internshipId, request.reason());
    }

    @DeleteMapping("/{internshipId}")
    public InternshipDetailsResponse deleteInternship(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable Long internshipId,
            @RequestParam(required = false) String reason) {
        return service.softDelete(admin, internshipId, reason != null && !reason.isBlank() ? reason : "Administrative deletion by III Cell Incharge");
    }
}
