package edu.institution.ims.dashboard;

import edu.institution.ims.dashboard.dto.DashboardSummaryResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {
    private final DashboardService service;
    private final edu.institution.ims.academicsession.AcademicSessionService sessions;
    public DashboardController(DashboardService service, edu.institution.ims.academicsession.AcademicSessionService sessions) { this.service = service; this.sessions=sessions; }
    @GetMapping("/summary") public DashboardSummaryResponse summary(@RequestParam(required=false) Long sessionId) { return service.summary(sessions.selectedId(sessionId)); }
}
