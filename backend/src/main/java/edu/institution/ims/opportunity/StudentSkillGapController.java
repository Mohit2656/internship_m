package edu.institution.ims.opportunity;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/skill-gaps")
public class StudentSkillGapController {
    private final SkillGapService service;
    public StudentSkillGapController(SkillGapService service) { this.service = service; }
    @GetMapping public StudentSkillGapResponse analyze(@AuthenticationPrincipal UserPrincipal principal) { return service.analyze(principal); }
}
