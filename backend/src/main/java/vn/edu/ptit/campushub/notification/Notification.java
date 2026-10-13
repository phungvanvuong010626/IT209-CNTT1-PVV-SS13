package vn.edu.ptit.campushub.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import vn.edu.ptit.campushub.user.User;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    /** Không có khóa ngoại để giữ thông báo khi bài đăng bị xóa. */
    private Long postId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(User user, NotificationType type, String title, String message, Long postId) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.message = message;
        this.postId = postId;
        this.createdAt = Instant.now();
    }

    public void markRead() {
        this.read = true;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Long getPostId() { return postId; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }
}
