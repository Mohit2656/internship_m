package edu.institution.ims.opportunity;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class OpportunityMatchingController {
    private final OpportunityMatchingService service;
    private final SystemMatchedStudentService systemMatches;
    private final OpportunityInterestService interests;
    public OpportunityMatchingController(OpportunityMatchingService service, SystemMatchedStudentService systemMatches,
            OpportunityInterestService interests) {
        this.service=service; this.systemMatches=systemMatches; this.interests=interests;
    }
    @GetMapping("/api/v1/admin/opportunities/{id}/matching-runs")
    public List<OpportunityMatchingService.Summary> history(@PathVariable Long id) { return service.history(id); }
    @GetMapping("/api/v1/admin/opportunities/{id}/matching-runs/{runId}")
    public OpportunityMatchingService.Run get(@PathVariable Long id,@PathVariable Long runId) { return service.get(id,runId); }
    @PostMapping("/api/v1/admin/opportunities/{id}/matching-runs")
    public OpportunityMatchingService.Run rerun(@PathVariable Long id) { return service.rerun(id); }
    @GetMapping("/api/v1/student/opportunities/{id}/matching-score")
    public OpportunityMatchingService.Candidate own(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user) { return service.ownScore(user,id); }
    @PostMapping("/api/v1/admin/opportunities/{id}/recommended-students/{studentId}/notify")
    public void notifyCandidate(@PathVariable Long id,@PathVariable Long studentId) { service.notifyCandidate(id,studentId); }
    @GetMapping("/api/v1/admin/opportunities/{id}/system-matched-students")
    public SystemMatchedStudentService.Response systemMatchedStudents(@PathVariable Long id) {
        return systemMatches.forOpportunity(id);
    }
    @GetMapping("/api/v1/admin/opportunities/{id}/interested-students")
    public OpportunityInterestService.InterestedStudents interestedStudents(@PathVariable Long id) {
        return interests.forOpportunity(id);
    }
}
