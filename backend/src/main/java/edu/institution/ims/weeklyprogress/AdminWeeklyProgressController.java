package edu.institution.ims.weeklyprogress;

import edu.institution.ims.security.UserPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/weekly-progress")
public class AdminWeeklyProgressController {
    private final WeeklyProgressService service;
    public AdminWeeklyProgressController(WeeklyProgressService service) { this.service = service; }
    @GetMapping public WeeklyProgressService.Monitoring weeks(@AuthenticationPrincipal UserPrincipal principal) { return service.monitoring(principal, true); }
}
