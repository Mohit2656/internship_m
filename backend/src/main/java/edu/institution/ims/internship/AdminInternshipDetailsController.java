package edu.institution.ims.internship;

import edu.institution.ims.internship.dto.InternshipDetailsResponse;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/students/{studentId}/internship")
public class AdminInternshipDetailsController {
    private final InternshipDetailsService service;
    public AdminInternshipDetailsController(InternshipDetailsService service) { this.service = service; }
    @GetMapping public InternshipDetailsResponse get(@PathVariable Long studentId) { return service.getForAdmin(studentId); }
}
