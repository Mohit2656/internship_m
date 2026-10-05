package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/internship/hybrid-schedule")
public class StudentHybridScheduleController {
    private final HybridScheduleService service;
    public StudentHybridScheduleController(HybridScheduleService service) { this.service = service; }

    @GetMapping public HybridScheduleResponse get(@AuthenticationPrincipal UserPrincipal principal) {
        return service.getOwn(principal);
    }
}
