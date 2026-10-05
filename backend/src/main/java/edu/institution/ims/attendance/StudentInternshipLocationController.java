package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/internship/location")
public class StudentInternshipLocationController {
    private final InternshipLocationService service;

    public StudentInternshipLocationController(InternshipLocationService service) { this.service = service; }

    @GetMapping
    public InternshipLocationResponse get(@AuthenticationPrincipal UserPrincipal principal) {
        return service.getOwn(principal);
    }

    @PutMapping
    public InternshipLocationResponse propose(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InternshipLocationRequest request) {
        return service.proposeOwn(principal, request);
    }
}
