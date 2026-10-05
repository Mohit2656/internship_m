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
    @PostMapping public ResponseEntity<InternshipDetailsResponse> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InternshipDetailsRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.createOwn(principal, request)); }
    @PutMapping public InternshipDetailsResponse update(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InternshipDetailsRequest request) { return service.updateOwn(principal, request); }
}
