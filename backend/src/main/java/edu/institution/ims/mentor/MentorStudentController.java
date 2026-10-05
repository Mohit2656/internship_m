package edu.institution.ims.mentor;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/mentor/students")
public class MentorStudentController {
    private final MentorAssignmentService service;
    private final MentorStudentProfileService profiles;

    public MentorStudentController(MentorAssignmentService service, MentorStudentProfileService profiles) { this.service = service; this.profiles = profiles; }

    @GetMapping
    public List<AssignmentStudentResponse> assignedStudents(@AuthenticationPrincipal UserPrincipal principal) {
        return service.assignedTo(principal);
    }

    @GetMapping("/{studentId}/profile")
    public MentorStudentProfileResponse profile(@PathVariable Long studentId, @AuthenticationPrincipal UserPrincipal principal) {
        return profiles.getAssignedStudent(studentId, principal);
    }
}
