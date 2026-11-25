package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1", "/api"})
public class AttendanceExceptionController {

    private final AttendanceExceptionService exceptionService;

    public AttendanceExceptionController(AttendanceExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    @PostMapping("/student/attendance/exceptions/holiday")
    @PreAuthorize("hasRole('STUDENT')")
    public AttendanceExceptionResponse requestHoliday(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AttendanceExceptionRequest request) {
        return exceptionService.requestHoliday(principal.id(), request.internshipId(), request.date(), request.reason());
    }

    @GetMapping("/student/attendance/exceptions")
    @PreAuthorize("hasRole('STUDENT')")
    public List<AttendanceExceptionResponse> getStudentExceptions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long internshipId) {
        return exceptionService.getExceptionsForStudent(principal.id(), internshipId);
    }

    @GetMapping("/mentor/attendance/exceptions")
    @PreAuthorize("hasRole('MENTOR')")
    public List<AttendanceExceptionResponse> getMentorExceptions(
            @AuthenticationPrincipal UserPrincipal principal) {
        return exceptionService.getExceptionsForMentor(principal.id());
    }

    @PostMapping("/mentor/attendance/exceptions/{id}/review")
    @PreAuthorize("hasRole('MENTOR')")
    public AttendanceExceptionResponse reviewMentor(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AttendanceExceptionReviewRequest request) {
        return exceptionService.review(principal.id(), id, request.status(), request.remarks());
    }

    @GetMapping("/admin/attendance/exceptions")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AttendanceExceptionResponse> getAdminExceptions() {
        return exceptionService.getAllExceptionsForAdmin();
    }

    @PostMapping("/admin/attendance/exceptions/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public AttendanceExceptionResponse reviewAdmin(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AttendanceExceptionReviewRequest request) {
        return exceptionService.review(principal.id(), id, request.status(), request.remarks());
    }
}
