package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/mentor/attendance")
public class MentorAttendanceController {
    private final MentorAttendanceService service;

    public MentorAttendanceController(MentorAttendanceService service) { this.service = service; }

    @GetMapping("/today")
    public List<MentorStudentAttendanceResponse> today(@AuthenticationPrincipal UserPrincipal principal) {
        return service.today(principal);
    }
}
