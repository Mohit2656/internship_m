package edu.institution.ims.opportunity;

import edu.institution.ims.common.PagedResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import edu.institution.ims.security.UserPrincipal;

@RestController
@RequestMapping("/api/v1/student/opportunities")
public class StudentOpportunityController {
    private final StudentOpportunityService service;
    private final OpportunityMatchService matchService;
    private final OpportunityInterestService interests;
    public StudentOpportunityController(StudentOpportunityService service, OpportunityMatchService matchService,
            OpportunityInterestService interests) { this.service = service; this.matchService = matchService; this.interests = interests; }

    @GetMapping
    public PagedResponse<StudentOpportunitySummary> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) OpportunityMode mode,
            @RequestParam(defaultValue = "NEWEST") StudentOpportunitySort sort,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return service.list(search, mode, sort, page, size);
    }

    @GetMapping("/{id}")
    public StudentOpportunityDetail detail(@PathVariable Long id) { return service.detail(id); }

    @GetMapping("/{id}/match")
    public StudentOpportunityMatchResponse match(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return matchService.match(principal, id);
    }

    @GetMapping("/interests")
    public java.util.Set<Long> interests(@AuthenticationPrincipal UserPrincipal principal) {
        return interests.ownInterests(principal);
    }

    @PostMapping("/{id}/interest")
    public OpportunityInterestService.InterestStatus interest(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) { return interests.express(principal, id); }
}
