package edu.institution.ims.monthlyprogress;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/student/monthly-progress")
public class StudentMonthlyProgressController {
    private final MonthlyProgressService service;
    public StudentMonthlyProgressController(MonthlyProgressService service) { this.service = service; }

    @GetMapping public MonthlyProgressOverviewResponse overview(@AuthenticationPrincipal UserPrincipal principal) { return service.ownOverview(principal); }
    @PostMapping(value = "/{monthNumber}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MonthlyProgressReportResponse> submit(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable int monthNumber, @RequestPart("workDescription") String workDescription,
            @RequestPart("photo") MultipartFile photo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(principal, monthNumber, workDescription, photo));
    }
    @GetMapping("/{reportId}") public MonthlyProgressReportResponse report(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long reportId) { return service.ownReport(principal, reportId); }
    @GetMapping("/{reportId}/photo") public ResponseEntity<?> photo(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long reportId) { return MonthlyProgressDownloads.response(service.ownPhoto(principal, reportId), true); }
    @GetMapping("/{reportId}/pdf") public ResponseEntity<?> pdf(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long reportId) { return MonthlyProgressDownloads.response(service.ownPdf(principal, reportId), false); }
}
