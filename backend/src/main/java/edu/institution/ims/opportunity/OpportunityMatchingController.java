package edu.institution.ims.opportunity;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class OpportunityMatchingController {
    private final OpportunityMatchingService service;
    public OpportunityMatchingController(OpportunityMatchingService service) { this.service=service; }
    @GetMapping("/api/v1/admin/opportunities/{id}/matching-runs")
    public List<OpportunityMatchingService.Summary> history(@PathVariable Long id) { return service.history(id); }
    @GetMapping("/api/v1/admin/opportunities/{id}/matching-runs/{runId}")
    public OpportunityMatchingService.Run get(@PathVariable Long id,@PathVariable Long runId) { return service.get(id,runId); }
    @PostMapping("/api/v1/admin/opportunities/{id}/matching-runs")
    public OpportunityMatchingService.Run rerun(@PathVariable Long id) { return service.rerun(id); }
    @GetMapping("/api/v1/student/opportunities/{id}/matching-score")
    public OpportunityMatchingService.Candidate own(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user) { return service.ownScore(user,id); }
}
