package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mentor/students/{studentId}/hybrid-schedule")
public class MentorHybridScheduleController {
    private final HybridScheduleService service;
    public MentorHybridScheduleController(HybridScheduleService service) { this.service = service; }

    @GetMapping public HybridScheduleResponse get(@PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.getForMentor(studentId, principal);
    }
}
