package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mentor/attendance/late-permissions")
public class MentorLateAttendancePermissionController {
    private final LateAttendancePermissionService service;

    public MentorLateAttendancePermissionController(LateAttendancePermissionService service) {
        this.service = service;
    }

    @GetMapping
    public List<LateAttendancePermissionResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return service.forMentor(principal);
    }

    @PutMapping("/{studentUserId}")
    public LateAttendancePermissionResponse update(@PathVariable Long studentUserId,
            @Valid @RequestBody LateAttendancePermissionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.setForMentor(studentUserId, request.enabled(), principal);
    }
}
