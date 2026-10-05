package edu.institution.ims.monthlyprogress;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/monthly-progress")
public class AdminMonthlyProgressController {
    private final MonthlyProgressService service;
    public AdminMonthlyProgressController(MonthlyProgressService service) { this.service = service; }

    @GetMapping public List<MonthlyProgressReportResponse> reports() { return service.adminReports(); }
    @GetMapping("/{reportId}") public MonthlyProgressReportResponse report(@PathVariable Long reportId) { return service.adminReport(reportId); }
    @GetMapping("/{reportId}/photo") public ResponseEntity<?> photo(@PathVariable Long reportId) { return MonthlyProgressDownloads.response(service.adminPhoto(reportId), true); }
    @GetMapping("/{reportId}/pdf") public ResponseEntity<?> pdf(@PathVariable Long reportId) { return MonthlyProgressDownloads.response(service.adminPdf(reportId), false); }
}
