package vn.edu.ptit.campushub.moderation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import vn.edu.ptit.campushub.post.PostDtos.PostView;
import vn.edu.ptit.campushub.user.User;

public final class ModerationDtos {
    private ModerationDtos() {
    }

    public record ReportRequest(
            @NotNull(message = "Vui lòng chọn lý do báo cáo") ReportReason reason,
            @Size(max = 500, message = "Mô tả tối đa 500 ký tự") String description) {
    }

    public record ModerationRequest(
            @NotNull(message = "Vui lòng chọn hành động kiểm duyệt") ModerationAction action,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
    }

    public record UserRef(Long id, String displayName) {
        static UserRef from(User user) {
            return user == null ? null : new UserRef(user.getId(), user.getDisplayName());
        }
    }

    public record ReasonView(String code, String label) {
        static ReasonView from(ReportReason reason) {
            return new ReasonView(reason.name(), reason.getLabel());
        }
    }

    public record MyReportView(boolean reported, ReportStatus status) {
    }

    public record ReportView(Long id, Long postId, String postTitle, UserRef reporter, ReportReason reason,
                             String reasonLabel, String description, ReportStatus status, Instant createdAt,
                             Instant resolvedAt, String resolutionNote) {
        static ReportView from(Report report) {
            return new ReportView(report.getId(), report.getPost().getId(), report.getPost().getTitle(),
                    UserRef.from(report.getReporter()), report.getReason(), report.getReason().getLabel(),
                    report.getDescription(), report.getStatus(), report.getCreatedAt(), report.getResolvedAt(),
                    report.getResolutionNote());
        }
    }

    public record ModerationActionView(Long id, ModerationAction action, String actionLabel, String note,
                                       UserRef admin, Instant createdAt) {
        static ModerationActionView from(ModerationActionLog log) {
            return new ModerationActionView(log.getId(), log.getAction(), log.getAction().getLabel(),
                    log.getNote(), UserRef.from(log.getAdmin()), log.getCreatedAt());
        }
    }

    public record ModeratedPostView(Long id, String title, String categoryName, UserRef author,
                                    ModerationStatus moderationStatus, long pendingReportCount,
                                    long totalReportCount, Instant lastReportedAt, Instant createdAt) {
    }

    public record ModerationSummary(long pendingReports, long underReviewPosts, long hiddenPosts,
                                    long lockedPosts) {
    }

    public record ModerationDetailView(PostView post, List<ReportView> reports,
                                       List<ModerationActionView> history) {
    }

    public record ModerationResult(PostView post, int resolvedReports) {
    }
}
