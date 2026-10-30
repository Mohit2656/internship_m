package edu.institution.ims.weeklyprogress;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/weekly-progress")
public class StudentWeeklyProgressController {
    private final WeeklyProgressService service;
    public StudentWeeklyProgressController(WeeklyProgressService service) { this.service = service; }
    public record SubmitRequest(@NotBlank @Size(max=3000) String workDescription, @Positive Integer revision) {}
    @GetMapping public WeeklyProgressService.Overview overview(@AuthenticationPrincipal UserPrincipal principal) { return service.ownOverview(principal); }
    @PostMapping("/{periodId}") public WeeklyProgressService.Week submit(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long periodId, @Valid @RequestBody SubmitRequest request) {
        return service.submit(principal, periodId, request.revision(), request.workDescription());
    }
}
