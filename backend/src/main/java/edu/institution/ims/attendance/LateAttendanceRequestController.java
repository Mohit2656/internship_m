package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class LateAttendanceRequestController {
    private final LateAttendanceRequestService service;
    public LateAttendanceRequestController(LateAttendanceRequestService service) { this.service=service; }
    @PostMapping("/api/v1/student/attendance/late-requests") @ResponseStatus(HttpStatus.CREATED)
    public LateAttendanceRequestService.Request submit(@AuthenticationPrincipal UserPrincipal principal,
            @RequestBody LateAttendanceRequestService.Submission request) { return service.submit(principal,request); }
    @GetMapping("/api/v1/student/attendance/late-requests")
    public List<LateAttendanceRequestService.Request> own(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required=false) Long internshipId) { return service.studentRequests(principal,internshipId); }
    @GetMapping({"/api/v1/mentor/attendance/late-requests","/api/v1/admin/attendance/late-requests"})
    public List<LateAttendanceRequestService.Request> queue(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue="pending") String view) { return service.reviewRequests(principal,view); }
    @PostMapping({"/api/v1/mentor/attendance/late-requests/{id}/decision","/api/v1/admin/attendance/late-requests/{id}/decision"})
    public LateAttendanceRequestService.Request decide(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal principal,
            @RequestBody LateAttendanceRequestService.Decision request) { return service.decide(id,principal,request); }
}
