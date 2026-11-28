package edu.institution.ims.internship;

import edu.institution.ims.attendance.AttendanceAbsenceService;
import edu.institution.ims.internship.dto.*;
import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/student/internship")
public class InternshipDetailsController {
    private final InternshipDetailsService service;
    private final AttendanceAbsenceService absenceService;

    public InternshipDetailsController(InternshipDetailsService service) {
        this(service, null);
    }

    @Autowired
    public InternshipDetailsController(InternshipDetailsService service,
            @Autowired(required = false) AttendanceAbsenceService absenceService) {
        this.service = service;
        this.absenceService = absenceService;
    }

    @GetMapping public InternshipDetailsResponse get(@AuthenticationPrincipal UserPrincipal principal) { return service.getOwn(principal); }
    @GetMapping("/all") public java.util.List<InternshipDetailsResponse> all(@AuthenticationPrincipal UserPrincipal principal) { return service.getAllOwn(principal); }
    @PostMapping public ResponseEntity<InternshipDetailsResponse> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InternshipDetailsRequest request) {
        InternshipDetailsResponse saved = service.createOwn(principal, request);
        syncAbsence(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
    @PostMapping("/second") public ResponseEntity<InternshipDetailsResponse> createSecond(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InternshipDetailsRequest request) {
        InternshipDetailsResponse saved = service.createSecond(principal, request);
        syncAbsence(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
    @PutMapping public InternshipDetailsResponse update(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long internshipId,
            @Valid @RequestBody InternshipDetailsRequest request) {
        InternshipDetailsResponse saved = internshipId != null ? service.updateOwn(principal, internshipId, request) : service.updateOwn(principal, request);
        syncAbsence(saved);
        return saved;
    }
    @PutMapping("/second") public InternshipDetailsResponse updateSecond(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InternshipDetailsRequest request) {
        InternshipDetailsResponse saved = service.updateSecond(principal, request);
        syncAbsence(saved);
        return saved;
    }

    private void syncAbsence(InternshipDetailsResponse saved) {
        if (absenceService != null && saved != null && saved.id() != null) {
            absenceService.processOverdueForInternship(saved.id());
        }
    }
}
