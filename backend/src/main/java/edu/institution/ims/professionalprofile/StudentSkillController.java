package edu.institution.ims.professionalprofile;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/professional-profile")
public class StudentSkillController {
    private final StudentSkillService service;
    public StudentSkillController(StudentSkillService service) { this.service = service; }

    @GetMapping("/skills")
    public SkillProfileResponse skills(@AuthenticationPrincipal UserPrincipal principal) { return service.getOwn(principal); }
    @PostMapping("/skills")
    public SkillProfileResponse add(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody SkillNameRequest request) { return service.addOwn(principal, request); }
    @PatchMapping("/skills/{skillId}")
    public SkillProfileResponse rename(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long skillId,
            @Valid @RequestBody SkillNameRequest request) { return service.renameOwn(principal, skillId, request); }
    @DeleteMapping("/skills/{skillId}")
    public SkillProfileResponse remove(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long skillId) { return service.removeOwn(principal, skillId); }
    @PostMapping("/skill-suggestions/{suggestionId}/confirm")
    public SkillProfileResponse confirm(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long suggestionId) { return service.confirmOwn(principal, suggestionId); }
    @PostMapping("/skill-suggestions/{suggestionId}/reject")
    public SkillProfileResponse reject(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long suggestionId) { return service.rejectOwn(principal, suggestionId); }
    @PostMapping("/skill-suggestions/confirm-all")
    public SkillProfileResponse confirmAll(@AuthenticationPrincipal UserPrincipal principal) { return service.confirmAllOwn(principal); }
}
