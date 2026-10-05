package edu.institution.ims.dashboard;

import edu.institution.ims.dashboard.dto.DashboardSummaryResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }
    @GetMapping("/summary") public DashboardSummaryResponse summary() { return service.summary(); }
}
