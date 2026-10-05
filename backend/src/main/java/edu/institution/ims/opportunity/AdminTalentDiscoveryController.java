package edu.institution.ims.opportunity;

import edu.institution.ims.studentprofile.Branch;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/opportunities/{opportunityId}/talent-discovery")
public class AdminTalentDiscoveryController {
    private final AdminTalentDiscoveryService service;

    public AdminTalentDiscoveryController(AdminTalentDiscoveryService service) { this.service = service; }

    @GetMapping
    public TalentDiscoveryResponse discover(@PathVariable Long opportunityId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ELIGIBLE_OR_UNKNOWN") TalentEligibilityFilter eligibility,
            @RequestParam(required = false) Branch branch,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String skill,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.discover(opportunityId, search, eligibility, branch, semester, skill, page, size);
    }

    @GetMapping("/skill-gaps")
    public AdminSkillGapResponse skillGaps(@PathVariable Long opportunityId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ELIGIBLE_OR_UNKNOWN") TalentEligibilityFilter eligibility,
            @RequestParam(required = false) Branch branch,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String skill) {
        return service.skillGaps(opportunityId, search, eligibility, branch, semester, skill);
    }
}
