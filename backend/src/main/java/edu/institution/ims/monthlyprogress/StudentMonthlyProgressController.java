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

    @GetMapping public MonthlyProgressOverviewResponse overview(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long internshipId) { return service.ownOverview(principal, internshipId); }
    @PostMapping(value = "/{monthNumber}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MonthlyProgressReportResponse> submit(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long internshipId,
            @PathVariable int monthNumber, @RequestPart("workDescription") String workDescription,
            @RequestPart("photo") MultipartFile photo,
            @RequestPart(value="reportPdf",required=false) MultipartFile reportPdf) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(principal, internshipId, monthNumber, workDescription, photo, reportPdf));
    }
    @PostMapping(value="/{reportId}/resubmit",consumes=org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MonthlyProgressReportResponse> resubmit(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long reportId,@RequestParam int revision,@RequestPart("workDescription") String workDescription,
            @RequestPart("photo") org.springframework.web.multipart.MultipartFile photo,
            @RequestPart(value="reportPdf",required=false) MultipartFile reportPdf) {
        return ResponseEntity.ok(service.resubmit(principal,reportId,revision,workDescription,photo,reportPdf));
    }
    @GetMapping("/{reportId}") public MonthlyProgressReportResponse report(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long reportId) { return service.ownReport(principal, reportId); }
    @GetMapping("/{reportId}/photo") public ResponseEntity<?> photo(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long reportId) { return MonthlyProgressDownloads.response(service.ownPhoto(principal, reportId), true); }
    @GetMapping("/{reportId}/pdf") public ResponseEntity<?> pdf(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long reportId) { return MonthlyProgressDownloads.response(service.ownPdf(principal, reportId), false); }
    @GetMapping("/{reportId}/attachment") public ResponseEntity<?> attachment(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long reportId,
            @RequestParam(defaultValue="false") boolean download) { return MonthlyProgressDownloads.response(service.ownAttachment(principal,reportId),!download); }
}
