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
            @RequestParam(required=false) Long internshipId,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal accuracy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant locationCapturedAt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submitToday(principal,internshipId, latitude, longitude, accuracy, locationCapturedAt));
    }

    @PostMapping(value = "/today/physical", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttendanceResponse> submitPhysicalToday(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "aiEvidence", required = false) @Valid AiVerificationEvidenceRequest aiEvidence,
            jakarta.servlet.http.HttpServletRequest request) {
        Long resolvedId = parseId(firstParam(request, "internshipId"));
        BigDecimal resolvedLat = parseBigDecimal(firstParam(request, "latitude"), "latitude");
        BigDecimal resolvedLon = parseBigDecimal(firstParam(request, "longitude"), "longitude");
        BigDecimal resolvedAcc = parseBigDecimal(firstParam(request, "accuracy"), "accuracy");
        Instant resolvedLocTime = parseInstant(firstParam(request, "locationCapturedAt"), "locationCapturedAt");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(physicalService.submitToday(principal, resolvedId, photo, aiEvidence, resolvedLat, resolvedLon, resolvedAcc, resolvedLocTime));
    }

    @PostMapping(value = "/today/remote", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttendanceResponse> submitRemoteToday(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "aiEvidence", required = false) @Valid AiVerificationEvidenceRequest aiEvidence,
            jakarta.servlet.http.HttpServletRequest request) {
        Long resolvedId = parseId(firstParam(request, "internshipId"));
        String resolvedSummary = firstParam(request, "dailyWorkSummary");
        BigDecimal resolvedLat = parseBigDecimal(firstParam(request, "latitude"), "latitude");
        BigDecimal resolvedLon = parseBigDecimal(firstParam(request, "longitude"), "longitude");
        BigDecimal resolvedAcc = parseBigDecimal(firstParam(request, "accuracy"), "accuracy");
        Instant resolvedLocTime = parseInstant(firstParam(request, "locationCapturedAt"), "locationCapturedAt");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(remoteService.submitToday(principal, resolvedId, photo, aiEvidence, resolvedSummary, resolvedLat, resolvedLon, resolvedAcc, resolvedLocTime));
    }

    private static String firstParam(jakarta.servlet.http.HttpServletRequest request, String name) {
        if (request == null) return null;
        String val = request.getParameter(name);
        if (val != null && !val.isBlank()) return val;

        jakarta.servlet.http.HttpServletRequest current = request;
        while (current instanceof jakarta.servlet.http.HttpServletRequestWrapper wrapper) {
            String wrappedVal = current.getParameter(name);
            if (wrappedVal != null && !wrappedVal.isBlank()) return wrappedVal;
            if (current instanceof org.springframework.web.multipart.MultipartHttpServletRequest mReq) {
                MultipartFile file = mReq.getFile(name);
                if (file != null && !file.isEmpty()) {
                    try { return new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8).trim(); } catch (Exception ignored) {}
                }
            }
            if (wrapper.getRequest() instanceof jakarta.servlet.http.HttpServletRequest next) {
                current = next;
            } else {
                break;
            }
        }

        if (current instanceof org.springframework.web.multipart.MultipartHttpServletRequest multipartRequest) {
            MultipartFile file = multipartRequest.getFile(name);
            if (file != null && !file.isEmpty()) {
                try { return new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8).trim(); } catch (Exception ignored) {}
            }
        }

        try {
            jakarta.servlet.http.Part part = request.getPart(name);
            if (part != null) {
                try (var is = part.getInputStream()) {
                    byte[] bytes = is.readAllBytes();
                    if (bytes.length > 0) {
                        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static Long parseId(String val) {
        if (val == null || val.isBlank()) return null;
        try { return Long.valueOf(val.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal parseBigDecimal(String val, String paramName) {
        if (val == null || val.isBlank()) return null;
        try { return new BigDecimal(val.trim()); } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid number format for parameter " + paramName);
        }
    }

    private static Instant parseInstant(String val, String paramName) {
        if (val == null || val.isBlank()) return null;
        try { return Instant.parse(val.trim()); } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO date-time format for parameter " + paramName);
        }
    }

    @PostMapping("/verification-session")
    public ResponseEntity<AiVerificationSessionResponse> createVerificationSession(
            @AuthenticationPrincipal UserPrincipal principal,@RequestParam(required=false) Long internshipId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aiVerificationService.create(principal,internshipId));
    }

    @GetMapping("/today/policy")
    public AttendancePolicyResponse policy(@AuthenticationPrincipal UserPrincipal principal,@RequestParam(required=false) Long internshipId) {
        return policyService.today(principal,internshipId);
    }

    @GetMapping("/today")
    public TodayAttendanceResponse today(@AuthenticationPrincipal UserPrincipal principal,@RequestParam(required=false) Long internshipId) {
        return service.today(principal,internshipId);
    }

    @GetMapping
    public List<AttendanceResponse> history(@AuthenticationPrincipal UserPrincipal principal,@RequestParam(required=false) Long internshipId) {
        return service.history(principal,internshipId);
    }
}
