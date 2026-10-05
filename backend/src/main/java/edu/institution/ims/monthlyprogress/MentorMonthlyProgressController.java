package edu.institution.ims.monthlyprogress;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/mentor/monthly-progress")
public class MentorMonthlyProgressController {
    private final MonthlyProgressService service;
    public MentorMonthlyProgressController(MonthlyProgressService service) { this.service = service; }

    @GetMapping public List<MonthlyProgressReportResponse> reports(@AuthenticationPrincipal UserPrincipal principal) { return service.mentorReports(principal); }
    @GetMapping("/{reportId}") public MonthlyProgressReportResponse report(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long reportId) { return service.mentorReport(principal, reportId); }
    @GetMapping("/{reportId}/photo") public ResponseEntity<?> photo(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long reportId) { return MonthlyProgressDownloads.response(service.mentorPhoto(principal, reportId), true); }
    @GetMapping("/{reportId}/pdf") public ResponseEntity<?> pdf(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long reportId) { return MonthlyProgressDownloads.response(service.mentorPdf(principal, reportId), false); }
}
