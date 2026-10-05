package edu.institution.ims.studentprofile;

import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/student/profile")
public class StudentProfileController {
    private final StudentProfileService service;
    public StudentProfileController(StudentProfileService service) { this.service = service; }
    @GetMapping public StudentProfileResponse get(@AuthenticationPrincipal UserPrincipal principal) { return service.getOwn(principal); }
    @GetMapping("/prefill") public ProfilePrefillResponse prefill(@AuthenticationPrincipal UserPrincipal principal) { return service.getOwnPrefill(principal); }
    @PostMapping public ResponseEntity<StudentProfileResponse> create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody StudentProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOwn(principal, request));
    }
    @PutMapping public StudentProfileResponse update(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody StudentProfileRequest request) { return service.updateOwn(principal, request); }
    @GetMapping("/options") public ProfileOptionsResponse options() {
        var branches = Arrays.stream(Branch.values()).map(b -> new ProfileOptionsResponse.Option(b.name(), b.getLabel())).toList();
        return new ProfileOptionsResponse(branches, List.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"), 1, 8);
    }
}
