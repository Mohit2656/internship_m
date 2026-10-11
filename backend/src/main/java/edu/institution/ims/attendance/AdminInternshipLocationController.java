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

    @PostMapping("/review")
    public InternshipLocationResponse review(@PathVariable Long studentId,@AuthenticationPrincipal UserPrincipal principal,@jakarta.validation.Valid @RequestBody WorkplaceReview request) { return service.review(studentId,principal,request); }
    @GetMapping
    public InternshipLocationResponse get(@PathVariable Long studentId) {
        return service.getForAdmin(studentId);
    }

    @PutMapping
    public InternshipLocationResponse update(@PathVariable Long studentId,
            @Valid @RequestBody InternshipLocationRequest request) {
        return service.updateForAdmin(studentId, request);
    }

    @PutMapping("/correction")
    public InternshipLocationResponse correction(@PathVariable Long studentId,@AuthenticationPrincipal UserPrincipal principal,@Valid @RequestBody WorkplaceCorrection request) { return service.correctForAdmin(studentId,principal,request); }
    public record Radius(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("10") @jakarta.validation.constraints.DecimalMax("5000") java.math.BigDecimal radiusMeters) {}
    @PutMapping("/radius")
    public InternshipLocationResponse radius(@PathVariable Long studentId,@AuthenticationPrincipal UserPrincipal principal,@Valid @RequestBody Radius request) { return service.radiusForAdmin(studentId,principal,request.radiusMeters()); }
    @PostMapping("/confirm")
    public InternshipLocationResponse confirm(@PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.confirmForAdmin(studentId, principal);
    }
}
