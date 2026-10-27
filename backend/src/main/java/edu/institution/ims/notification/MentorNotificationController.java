package edu.institution.ims.notification;

import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.common.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/mentor/notifications")
public class MentorNotificationController {
    private final MentorNotificationService service;

    public MentorNotificationController(MentorNotificationService service) { this.service = service; }

    @GetMapping
    public NotificationService.Inbox list(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") int page) { return service.list(user, page); }

    @GetMapping("/unread-count")
    public NotificationService.Count unread(@AuthenticationPrincipal UserPrincipal user) { return service.unread(user); }

    @PatchMapping("/{id}/read")
    public NotificationService.Count read(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        return service.markRead(user, id);
    }

    @PatchMapping("/read-all")
    public NotificationService.Count all(@AuthenticationPrincipal UserPrincipal user) { return service.markAllRead(user); }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> notificationError(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status,
                status == 404 ? "NOTIFICATION_NOT_FOUND" : "FORBIDDEN", exception.getReason(), null));
    }
}
