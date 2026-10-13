package vn.edu.ptit.campushub.notification;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.campushub.notification.NotificationService.NotificationView;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    List<NotificationView> list(@RequestParam(defaultValue = "false") boolean unreadOnly, Principal principal) {
        return service.list(unreadOnly, principal);
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount(Principal principal) {
        return Map.of("count", service.unreadCount(principal));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable Long id, Principal principal) {
        service.markRead(id, principal);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead(Principal principal) {
        service.markAllRead(principal);
    }
}
