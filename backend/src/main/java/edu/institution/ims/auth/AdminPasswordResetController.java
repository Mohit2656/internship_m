package edu.institution.ims.auth;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminPasswordResetController {
    private final AdminPasswordResetService service;
    public AdminPasswordResetController(AdminPasswordResetService service) { this.service = service; }
    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminPasswordResetService.PasswordResetResponse> reset(@PathVariable Long userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(service.reset(userId));
    }
}
