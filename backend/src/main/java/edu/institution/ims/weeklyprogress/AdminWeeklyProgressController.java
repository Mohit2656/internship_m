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
    @GetMapping("/{periodId}/pdf") public org.springframework.http.ResponseEntity<?> pdf(@PathVariable Long periodId) {
        return edu.institution.ims.monthlyprogress.MonthlyProgressDownloads.response(service.adminPdf(periodId), false);
    }
    @GetMapping("/{periodId}/attachment") public org.springframework.http.ResponseEntity<?> attachment(@PathVariable Long periodId,
            @RequestParam(defaultValue="false") boolean download) {
        return edu.institution.ims.monthlyprogress.MonthlyProgressDownloads.response(service.adminAttachment(periodId), !download);
    }
}
