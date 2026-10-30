package edu.institution.ims.notification;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {
    private final NotificationService service;
    public AdminNotificationController(NotificationService service) { this.service=service; }
    @GetMapping public NotificationService.Inbox list(@AuthenticationPrincipal UserPrincipal user,@RequestParam(defaultValue="0") int page) { return service.list(user,page); }
    @GetMapping("/unread-count") public NotificationService.Count count(@AuthenticationPrincipal UserPrincipal user) { return service.unread(user); }
    @PatchMapping("/{id}/read") public NotificationService.Count read(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long id) { return service.markRead(user,id); }
    @PatchMapping("/read-all") public NotificationService.Count readAll(@AuthenticationPrincipal UserPrincipal user) { return service.markAllRead(user); }
}
