package vn.edu.ptit.campushub.notification;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.campushub.common.ApiException;
import vn.edu.ptit.campushub.user.User;
import vn.edu.ptit.campushub.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    /** Tạo thông báo cho người dùng, tham gia giao dịch của nơi gọi. */
    @Transactional
    public void notify(User user, NotificationType type, String title, String message, Long postId) {
        notifications.save(new Notification(user, type, truncate(title, 160), truncate(message, 500), postId));
    }

    public List<NotificationView> list(boolean unreadOnly, Principal principal) {
        User user = currentUser(principal);
        List<Notification> items = unreadOnly
                ? notifications.findTop50ByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(user.getId())
                : notifications.findTop50ByUserIdOrderByCreatedAtDescIdDesc(user.getId());
        return items.stream().map(NotificationView::from).toList();
    }

    public long unreadCount(Principal principal) {
        return notifications.countByUserIdAndReadFalse(currentUser(principal).getId());
    }

    @Transactional
    public void markRead(Long id, Principal principal) {
        Notification notification = notifications.findByIdAndUserId(id, currentUser(principal).getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        notification.markRead();
    }

    @Transactional
    public void markAllRead(Principal principal) {
        notifications.markAllRead(currentUser(principal).getId());
    }

    private User currentUser(Principal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        }
        return users.findByUsername(principal.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập"));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    public record NotificationView(Long id, NotificationType type, String title, String message, Long postId,
                                   boolean read, Instant createdAt) {
        static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getPostId(),
                    n.isRead(), n.getCreatedAt());
        }
    }
}
