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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import vn.edu.ptit.campushub.post.Post;
import vn.edu.ptit.campushub.user.User;

@Entity
@Table(name = "reports", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "reporter_id"}))
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(length = 500)
    private String resolutionNote;

    protected Report() {
    }

    public Report(Post post, User reporter, ReportReason reason, String description) {
        this.post = post;
        this.reporter = reporter;
        this.reason = reason;
        this.description = description;
        this.createdAt = Instant.now();
    }

    /** Đóng báo cáo với trạng thái RESOLVED hoặc DISMISSED. */
    public void close(ReportStatus newStatus, User admin, String note) {
        this.status = newStatus;
        this.resolvedBy = admin;
        this.resolvedAt = Instant.now();
        this.resolutionNote = note;
    }

    public Long getId() { return id; }
    public Post getPost() { return post; }
    public User getReporter() { return reporter; }
    public ReportReason getReason() { return reason; }
    public String getDescription() { return description; }
    public ReportStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public User getResolvedBy() { return resolvedBy; }
    public String getResolutionNote() { return resolutionNote; }
}
