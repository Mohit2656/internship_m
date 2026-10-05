package edu.institution.ims.opportunity;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/opportunities")
public class AdminOpportunityController {
    private final OpportunityService service;
    public AdminOpportunityController(OpportunityService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public OpportunityResponse create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody OpportunityRequest request) {
        return service.create(principal, request);
    }
    @GetMapping public List<OpportunityResponse> list() { return service.list(); }
    @GetMapping("/{id}") public OpportunityResponse get(@PathVariable Long id) { return service.get(id); }
    @PutMapping("/{id}") public OpportunityResponse update(@PathVariable Long id, @Valid @RequestBody OpportunityRequest request) { return service.update(id, request); }
    @PostMapping("/{id}/publish") public OpportunityResponse publish(@PathVariable Long id) { return service.publish(id); }
    @PostMapping("/{id}/close") public OpportunityResponse close(@PathVariable Long id) { return service.close(id); }
}
