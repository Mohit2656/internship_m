package edu.institution.ims.mentor;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/mentors")
public class AdminMentorAssignmentController {
    private final MentorAssignmentService service;

    public AdminMentorAssignmentController(MentorAssignmentService service) { this.service = service; }

    @GetMapping("/{mentorUserId}/students")
    public MentorAssignmentWorkspaceResponse workspace(@PathVariable Long mentorUserId) {
        return service.workspace(mentorUserId);
    }

    @PostMapping("/{mentorUserId}/students")
    public MentorAssignmentWorkspaceResponse assign(@PathVariable Long mentorUserId,
            @Valid @RequestBody AssignStudentsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.assign(mentorUserId, request, principal);
    }

    @DeleteMapping("/{mentorUserId}/students/{studentUserId}")
    public MentorAssignmentWorkspaceResponse remove(@PathVariable Long mentorUserId,
            @PathVariable Long studentUserId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.remove(mentorUserId, studentUserId, principal);
    }
}
