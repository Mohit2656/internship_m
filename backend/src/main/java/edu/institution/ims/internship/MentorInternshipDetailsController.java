package edu.institution.ims.internship;

import edu.institution.ims.internship.dto.InternshipDetailsResponse;
import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mentor/students/{studentId}/internships")
public class MentorInternshipDetailsController {
    private final InternshipDetailsRepository details;
    private final MentorStudentAssignmentRepository assignments;

    public MentorInternshipDetailsController(InternshipDetailsRepository details, MentorStudentAssignmentRepository assignments) {
        this.details = details;
        this.assignments = assignments;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping
    public List<InternshipDetailsResponse> getAllForAssignedStudent(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long studentId) {
        requireAssigned(principal.id(), studentId);
        return details.findAllByOnboardingStudentIdOrderByOnboardingInternshipNumber(studentId)
                .stream().map(InternshipDetailsService::response).toList();
    }

    private void requireAssigned(Long mentorId, Long studentId) {
        assignments.findByStudentIdAndActiveTrue(studentId)
                .filter(a -> a.getMentor().getId().equals(mentorId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Student is not assigned to you"));
    }
}
