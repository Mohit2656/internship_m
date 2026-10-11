package edu.institution.ims.attendance;
import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/mentor/students/{studentId}/internship/location")
public class MentorInternshipLocationController {
    private final InternshipLocationService service;
    public MentorInternshipLocationController(InternshipLocationService service) { this.service=service; }
    @PostMapping("/review")
    public InternshipLocationResponse review(@PathVariable Long studentId,@AuthenticationPrincipal UserPrincipal principal,@jakarta.validation.Valid @RequestBody WorkplaceReview request) { return service.review(studentId,principal,request); }
    @GetMapping public InternshipLocationResponse get(@PathVariable Long studentId,@AuthenticationPrincipal UserPrincipal principal) { return service.getForMentor(studentId,principal); }
}
