package edu.institution.ims.internship;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class SecondInternshipRequestController {
    private final SecondInternshipRequestService service;
    public SecondInternshipRequestController(SecondInternshipRequestService service){this.service=service;}
    @PostMapping("/api/v1/student/second-internship-requests") @PreAuthorize("hasRole('STUDENT')")
    ResponseEntity<SecondInternshipRequestService.RequestView> apply(@AuthenticationPrincipal UserPrincipal p){return ResponseEntity.status(HttpStatus.CREATED).body(service.apply(p));}
    @GetMapping("/api/v1/student/second-internship-requests") @PreAuthorize("hasRole('STUDENT')")
    List<SecondInternshipRequestService.RequestView> own(@AuthenticationPrincipal UserPrincipal p){return service.own(p);}
    @GetMapping("/api/v1/admin/second-internship-requests") @PreAuthorize("hasRole('ADMIN')")
    List<SecondInternshipRequestService.RequestView> pending(){return service.pending();}
    @GetMapping("/api/v1/admin/second-internship-requests/{id}") @PreAuthorize("hasRole('ADMIN')")
    SecondInternshipRequestService.RequestView get(@PathVariable Long id){return service.findForAdmin(id);}
    @PutMapping("/api/v1/admin/second-internship-requests/{id}") @PreAuthorize("hasRole('ADMIN')")
    SecondInternshipRequestService.RequestView review(@AuthenticationPrincipal UserPrincipal p,@PathVariable Long id,@RequestBody SecondInternshipRequestService.ReviewRequest r){return service.review(p,id,r);}
}
