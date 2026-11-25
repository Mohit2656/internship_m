package edu.institution.ims.weeklyprogress;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import edu.institution.ims.monthlyprogress.MonthlyProgressDownloads;

@RestController
@RequestMapping("/api/v1/mentor/weekly-progress")
public class MentorWeeklyProgressController {
    private final WeeklyProgressService service;
    public MentorWeeklyProgressController(WeeklyProgressService service) { this.service = service; }
    public record ReviewRequest(@Min(1) int revision, @Size(max=1000) String reason) {}
    @GetMapping public WeeklyProgressService.Monitoring weeks(@AuthenticationPrincipal UserPrincipal principal) { return service.monitoring(principal, false); }
    @PostMapping("/{periodId}/approve") public WeeklyProgressService.Week approve(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long periodId, @Valid @RequestBody ReviewRequest request) { return service.review(principal, periodId, request.revision(), true, null); }
    @PostMapping("/{periodId}/reject") public WeeklyProgressService.Week reject(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long periodId, @Valid @RequestBody ReviewRequest request) { return service.review(principal, periodId, request.revision(), false, request.reason()); }
    @GetMapping("/{periodId}/attachment") public ResponseEntity<?> attachment(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long periodId,@RequestParam(defaultValue="false") boolean download) {
        return MonthlyProgressDownloads.response(service.mentorAttachment(principal,periodId),!download);
    }
    @GetMapping("/{periodId}/pdf") public ResponseEntity<?> pdf(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long periodId) {
        return MonthlyProgressDownloads.response(service.mentorPdf(principal,periodId),false);
    }
}
