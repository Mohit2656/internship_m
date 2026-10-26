package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.List;

@RestController
@RequestMapping("/api/v1/student/attendance")
public class StudentAttendanceController {
    private final AttendanceService service;
    private final PhysicalAttendanceService physicalService;
    private final RemoteAttendanceService remoteService;
    private final AttendancePolicyService policyService;
    private final AiVerificationService aiVerificationService;

    public StudentAttendanceController(AttendanceService service, PhysicalAttendanceService physicalService,
            RemoteAttendanceService remoteService, AttendancePolicyService policyService,
            AiVerificationService aiVerificationService) {
        this.service = service;
        this.physicalService = physicalService;
        this.remoteService = remoteService;
        this.policyService = policyService;
        this.aiVerificationService = aiVerificationService;
    }

    @PostMapping("/today")
    public ResponseEntity<AttendanceResponse> submitToday(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal accuracy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant locationCapturedAt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submitToday(principal, latitude, longitude, accuracy, locationCapturedAt));
    }

    @PostMapping(value = "/today/physical", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttendanceResponse> submitPhysicalToday(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "aiEvidence", required = false) @Valid AiVerificationEvidenceRequest aiEvidence,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal accuracy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant locationCapturedAt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(physicalService.submitToday(principal, photo, aiEvidence, latitude, longitude, accuracy, locationCapturedAt));
    }

    @PostMapping(value = "/today/remote", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttendanceResponse> submitRemoteToday(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "aiEvidence", required = false) @Valid AiVerificationEvidenceRequest aiEvidence,
            @RequestParam(required = false) String dailyWorkSummary,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal accuracy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant locationCapturedAt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(remoteService.submitToday(principal, photo, aiEvidence, dailyWorkSummary, latitude, longitude, accuracy, locationCapturedAt));
    }

    @PostMapping("/verification-session")
    public ResponseEntity<AiVerificationSessionResponse> createVerificationSession(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aiVerificationService.create(principal));
    }

    @GetMapping("/today/policy")
    public AttendancePolicyResponse policy(@AuthenticationPrincipal UserPrincipal principal) {
        return policyService.today(principal);
    }

    @GetMapping("/today")
    public TodayAttendanceResponse today(@AuthenticationPrincipal UserPrincipal principal) {
        return service.today(principal);
    }

    @GetMapping
    public List<AttendanceResponse> history(@AuthenticationPrincipal UserPrincipal principal) {
        return service.history(principal);
    }
}
