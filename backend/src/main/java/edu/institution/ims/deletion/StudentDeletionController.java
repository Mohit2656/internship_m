package edu.institution.ims.deletion;

import edu.institution.ims.security.UserPrincipal;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/students") @PreAuthorize("hasRole('ADMIN')")
public class StudentDeletionController {
    private final StudentDeletionService service;
    public StudentDeletionController(StudentDeletionService service) { this.service=service; }
    @GetMapping("/{id}/deletion-preview") public StudentDeletionService.Preview preview(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user) { return service.preview(id,user); }
    @DeleteMapping("/{id}") public StudentDeletionService.Result delete(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user,@RequestBody StudentDeletionService.Request request) { return service.deactivate(id,user,request); }
    @GetMapping("/{id}/deletion-audit") public Map<String,Object> audit(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user) { return service.audit(id,user); }
}
