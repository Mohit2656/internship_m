package edu.institution.ims.studentaccounts;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/student-accounts")
public class StudentAccountImportController {
    private final StudentAccountImportService service;
    public StudentAccountImportController(StudentAccountImportService service) { this.service = service; }
    @GetMapping("/template") public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student-account-import-template.xlsx").body(service.template());
    }
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkImportResponse upload(@RequestPart("file") MultipartFile file) { return service.importWorkbook(file); }
}
