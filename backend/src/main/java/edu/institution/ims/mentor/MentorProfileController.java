package edu.institution.ims.mentor;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mentor/profile")
public class MentorProfileController {
    private final MentorAccountService service;
    public MentorProfileController(MentorAccountService service) { this.service = service; }
    @GetMapping public MentorAccountResponse own(@AuthenticationPrincipal UserPrincipal principal) { return service.own(principal); }
}
