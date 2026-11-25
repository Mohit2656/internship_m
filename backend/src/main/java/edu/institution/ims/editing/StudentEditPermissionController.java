package edu.institution.ims.editing;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StudentEditPermissionController {
    private final StudentEditPermissionService service;
    public StudentEditPermissionController(StudentEditPermissionService service) { this.service = service; }

    @GetMapping("/student/edit-permissions")
    public List<EditPermissionResponse> student(@AuthenticationPrincipal UserPrincipal principal) {
        return service.forStudent(principal);
    }

    @GetMapping("/mentor/students/{studentId}/edit-permissions")
    public List<EditPermissionResponse> mentor(@PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.forMentor(studentId, principal);
    }

    @PutMapping("/mentor/students/{studentId}/edit-permissions")
    public List<EditPermissionResponse> update(@PathVariable Long studentId,
            @Valid @RequestBody EditPermissionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.setForMentor(studentId, request, principal);
    }
}
