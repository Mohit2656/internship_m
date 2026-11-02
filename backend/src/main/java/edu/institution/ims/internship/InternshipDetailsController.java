package edu.institution.ims.internship;

import edu.institution.ims.internship.dto.*;
import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/student/internship")
public class InternshipDetailsController {
    private final InternshipDetailsService service;
    public InternshipDetailsController(InternshipDetailsService service) { this.service = service; }
    @GetMapping public InternshipDetailsResponse get(@AuthenticationPrincipal UserPrincipal principal) { return service.getOwn(principal); }
    @GetMapping("/all") public java.util.List<InternshipDetailsResponse> all(@AuthenticationPrincipal UserPrincipal principal) { return service.getAllOwn(principal); }
    @PostMapping public ResponseEntity<InternshipDetailsResponse> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InternshipDetailsRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.createOwn(principal, request)); }
    @PostMapping("/second") public ResponseEntity<InternshipDetailsResponse> createSecond(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InternshipDetailsRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.createSecond(principal, request)); }
    @PutMapping public InternshipDetailsResponse update(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long internshipId,
            @Valid @RequestBody InternshipDetailsRequest request) {
        return internshipId != null ? service.updateOwn(principal, internshipId, request) : service.updateOwn(principal, request);
    }
    @PutMapping("/second") public InternshipDetailsResponse updateSecond(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InternshipDetailsRequest request) { return service.updateSecond(principal, request); }
}
