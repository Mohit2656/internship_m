package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class AttendanceReviewController {
    private final AttendanceReviewService service;
    public AttendanceReviewController(AttendanceReviewService service) { this.service=service; }
    @PostMapping("/api/v1/mentor/attendance/{id}/review")
    public AttendanceResponse review(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal principal,
            @RequestBody AttendanceReviewService.Decision request) { return service.review(id,principal,request); }
    @GetMapping({"/api/v1/mentor/attendance/{id}/review/history","/api/v1/admin/attendance/{id}/review/history",
            "/api/v1/student/attendance/{id}/review/history"})
    public List<AttendanceReviewService.Audit> history(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal principal) { return service.history(id,principal); }
}
