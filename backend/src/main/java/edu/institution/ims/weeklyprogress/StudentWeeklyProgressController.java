package edu.institution.ims.weeklyprogress;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import edu.institution.ims.monthlyprogress.MonthlyProgressDownloads;

@RestController
@RequestMapping("/api/v1/student/weekly-progress")
public class StudentWeeklyProgressController {
    private final WeeklyProgressService service;
    public StudentWeeklyProgressController(WeeklyProgressService service) { this.service = service; }
    public record SubmitRequest(@NotBlank @Size(max=3000) String workDescription, @Positive Integer revision) {}
    @GetMapping public WeeklyProgressService.Overview overview(@AuthenticationPrincipal UserPrincipal principal,@RequestParam(required=false) Long internshipId) { return service.ownOverview(principal,internshipId); }
    @PostMapping("/{periodId}") public WeeklyProgressService.Week submit(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long periodId, @Valid @RequestBody SubmitRequest request) {
        return service.submit(principal, periodId, request.revision(), request.workDescription());
    }
    @PostMapping(value="/{periodId}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public WeeklyProgressService.Week submitWithPdf(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long periodId,
            @RequestPart("workDescription") String workDescription,@RequestParam(required=false) Integer revision,
            @RequestPart(value="reportPdf",required=false) MultipartFile reportPdf) {
        return service.submit(principal,periodId,revision,workDescription,reportPdf);
    }
    @GetMapping("/{periodId}/attachment") public ResponseEntity<?> attachment(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long periodId,@RequestParam(defaultValue="false") boolean download) {
        return MonthlyProgressDownloads.response(service.ownAttachment(principal,periodId),!download);
    }
    @GetMapping("/{periodId}/pdf") public ResponseEntity<?> pdf(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long periodId) {
        return MonthlyProgressDownloads.response(service.ownPdf(principal,periodId),false);
    }
}
