package vn.edu.ptit.campushub.moderation;

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
import vn.edu.ptit.campushub.post.Post;
import vn.edu.ptit.campushub.user.User;

/** Lịch sử kiểm duyệt của bài đăng. admin = null khi hệ thống tự động thực hiện. */
@Entity
@Table(name = "moderation_actions")
public class ModerationActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private User admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationAction action;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ModerationActionLog() {
    }

    public ModerationActionLog(Post post, User admin, ModerationAction action, String note) {
        this.post = post;
        this.admin = admin;
        this.action = action;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Post getPost() { return post; }
    public User getAdmin() { return admin; }
    public ModerationAction getAction() { return action; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
