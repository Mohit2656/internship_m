package edu.institution.ims.attendance;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/students/{studentId}/internship/hybrid-schedule")
public class AdminHybridScheduleController {
    private final HybridScheduleService service;
    public AdminHybridScheduleController(HybridScheduleService service) { this.service = service; }

    @GetMapping public HybridScheduleResponse get(@PathVariable Long studentId) {
        return service.getForAdmin(studentId);
    }

    @PutMapping public HybridScheduleResponse update(@PathVariable Long studentId,
            @Valid @RequestBody HybridScheduleRequest request) {
        return service.updateForAdmin(studentId, request);
    }
}
