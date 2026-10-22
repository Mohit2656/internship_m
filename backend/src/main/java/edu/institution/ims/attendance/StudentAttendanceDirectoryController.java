package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/admin/attendance/students", "/api/v1/mentor/attendance/students"})
public class StudentAttendanceDirectoryController {
    private final StudentAttendanceDirectoryService service;
    public StudentAttendanceDirectoryController(StudentAttendanceDirectoryService service) { this.service = service; }
    @GetMapping
    public List<StudentAttendanceDirectoryService.StudentRow> students(@AuthenticationPrincipal UserPrincipal principal) {
        return service.students(principal);
    }
    @GetMapping("/{studentId}")
    public StudentAttendanceDirectoryService.History history(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long studentId) {
        return service.history(principal, studentId);
    }
}
