package edu.institution.ims.opportunity;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/opportunities/{opportunityId}/ai-analysis")
public class AdminOpportunityAiController {
    private final OpportunityAiAnalysisService service;
    public AdminOpportunityAiController(OpportunityAiAnalysisService service) { this.service = service; }
    @GetMapping public OpportunityAiAnalysisResponse get(@PathVariable Long opportunityId) { return service.get(opportunityId); }
    @PostMapping public OpportunityAiAnalysisResponse analyze(@PathVariable Long opportunityId) { return service.analyze(opportunityId); }
    @PostMapping("/suggestions/{suggestionId}/confirm")
    public OpportunityAiAnalysisResponse confirm(@PathVariable Long opportunityId, @PathVariable Long suggestionId) { return service.confirm(opportunityId, suggestionId); }
    @PostMapping("/suggestions/{suggestionId}/reject")
    public OpportunityAiAnalysisResponse reject(@PathVariable Long opportunityId, @PathVariable Long suggestionId) { return service.reject(opportunityId, suggestionId); }
    @PostMapping("/suggestions/confirm-all")
    public OpportunityAiAnalysisResponse confirmAll(@PathVariable Long opportunityId) { return service.confirmAll(opportunityId); }
}
