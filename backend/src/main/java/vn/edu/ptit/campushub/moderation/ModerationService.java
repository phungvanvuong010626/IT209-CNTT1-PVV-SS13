package vn.edu.ptit.campushub.moderation;

import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.campushub.common.ApiException;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ModeratedPostView;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ModerationActionView;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ModerationDetailView;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ModerationRequest;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ModerationResult;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ModerationSummary;
import vn.edu.ptit.campushub.moderation.ModerationDtos.MyReportView;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ReasonView;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ReportRequest;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ReportView;
import vn.edu.ptit.campushub.moderation.ModerationDtos.UserRef;
import vn.edu.ptit.campushub.notification.NotificationService;
import vn.edu.ptit.campushub.notification.NotificationType;
import vn.edu.ptit.campushub.post.Post;
import vn.edu.ptit.campushub.post.PostRepository;
import vn.edu.ptit.campushub.post.PostService;
import vn.edu.ptit.campushub.post.PostService.PageView;
import vn.edu.ptit.campushub.user.User;
import vn.edu.ptit.campushub.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class ModerationService {
    private final PostRepository posts;
    private final ReportRepository reports;
    private final ModerationActionLogRepository actionLogs;
    private final UserRepository users;
    private final NotificationService notifications;
    private final PostService postService;
    private final int autoReviewThreshold;

    public ModerationService(PostRepository posts, ReportRepository reports,
                             ModerationActionLogRepository actionLogs, UserRepository users,
                             NotificationService notifications, PostService postService,
                             @Value("${app.moderation.auto-review-threshold:3}") int autoReviewThreshold) {
        this.posts = posts;
        this.reports = reports;
        this.actionLogs = actionLogs;
        this.users = users;
        this.notifications = notifications;
        this.postService = postService;
        this.autoReviewThreshold = autoReviewThreshold;
    }

    // ---------- Người dùng ----------

    public List<ReasonView> reasons() {
        return Arrays.stream(ReportReason.values()).map(ReasonView::from).toList();
    }

    @Transactional
    public ReportView report(Long postId, ReportRequest request, Principal principal) {
        User reporter = currentUser(principal);
        Post post = findPost(postId);
        if (post.getAuthor().getId().equals(reporter.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bạn không thể báo cáo bài đăng của chính mình");
        }
        if (post.getModerationStatus() == ModerationStatus.HIDDEN) {
            throw new ApiException(HttpStatus.CONFLICT, "Bài đăng đã bị ẩn, không thể báo cáo");
        }
        if (reports.existsByPostIdAndReporterId(postId, reporter.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Bạn đã báo cáo bài đăng này");
        }
        Report report;
        try {
            report = reports.saveAndFlush(new Report(post, reporter, request.reason(),
                    blankToNull(request.description())));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Bạn đã báo cáo bài đăng này");
        }

        long pending = reports.countByPostIdAndStatus(postId, ReportStatus.PENDING);
        if (pending >= autoReviewThreshold && post.getModerationStatus() == ModerationStatus.NORMAL) {
            post.changeModerationStatus(ModerationStatus.UNDER_REVIEW);
            actionLogs.save(new ModerationActionLog(post, null, ModerationAction.AUTO_REVIEW,
                    "Bài đăng đạt ngưỡng " + autoReviewThreshold + " báo cáo đang chờ xử lý"));
            notifications.notify(post.getAuthor(), NotificationType.POST_UNDER_REVIEW,
                    "Bài đăng của bạn đang được kiểm duyệt",
                    "Bài đăng \"" + post.getTitle() + "\" đã nhận nhiều báo cáo và đang được quản trị viên xem xét.",
                    post.getId());
        }
        return ReportView.from(report);
    }

    public MyReportView myReport(Long postId, Principal principal) {
        User user = currentUser(principal);
        findPost(postId);
        return reports.findByPostIdAndReporterId(postId, user.getId())
                .map(report -> new MyReportView(true, report.getStatus()))
                .orElseGet(() -> new MyReportView(false, null));
    }

    // ---------- Quản trị viên ----------

    public ModerationSummary summary() {
        return new ModerationSummary(
                reports.countByStatus(ReportStatus.PENDING),
                posts.countByModerationStatus(ModerationStatus.UNDER_REVIEW),
                posts.countByModerationStatus(ModerationStatus.HIDDEN),
                posts.countByModerationStatus(ModerationStatus.LOCKED));
    }

    public PageView<ModeratedPostView> listPosts(ModerationStatus status, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> result = status == null
                ? posts.findNeedingModeration(pageable)
                : posts.findByModerationStatus(status, pageable);

        Map<Long, Object[]> stats = new HashMap<>();
        if (!result.isEmpty()) {
            List<Long> ids = result.getContent().stream().map(Post::getId).toList();
            for (Object[] row : reports.aggregateByPostIds(ids)) {
                stats.put((Long) row[0], row);
            }
        }
        List<ModeratedPostView> content = result.getContent().stream().map(post -> {
            Object[] row = stats.get(post.getId());
            long total = row == null ? 0 : ((Number) row[1]).longValue();
            long pending = row == null || row[2] == null ? 0 : ((Number) row[2]).longValue();
            Instant last = row == null ? null : (Instant) row[3];
            return new ModeratedPostView(post.getId(), post.getTitle(), post.getCategory().getName(),
                    UserRef.from(post.getAuthor()), post.getModerationStatus(), pending, total, last,
                    post.getCreatedAt());
        }).toList();
        return new PageView<>(content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    public PageView<ReportView> listReports(ReportStatus status, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Report> result = status == null
                ? reports.findAllReports(pageable)
                : reports.findByStatus(status, pageable);
        return new PageView<>(result.getContent().stream().map(ReportView::from).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public ModerationDetailView detail(Long postId, Principal principal) {
        findPost(postId);
        return new ModerationDetailView(
                postService.findOne(postId, principal),
                reports.findByPostIdOrderByCreatedAtDescIdDesc(postId).stream().map(ReportView::from).toList(),
                actionLogs.findByPostIdOrderByCreatedAtDescIdDesc(postId).stream()
                        .map(ModerationActionView::from).toList());
    }

    @Transactional
    public ModerationResult moderate(Long postId, ModerationRequest request, Principal principal) {
        User admin = currentUser(principal);
        Post post = findPost(postId);
        ModerationAction action = request.action();
        String note = blankToNull(request.note());
        ModerationStatus current = post.getModerationStatus();
        List<Report> pendingReports = reports.findByPostIdAndStatus(postId, ReportStatus.PENDING);

        int resolved = pendingReports.size();
        NotificationType type;
        String title;
        String message;
        switch (action) {
            case HIDE -> {
                requireNote(note, "Vui lòng nhập ghi chú khi ẩn bài đăng");
                if (current == ModerationStatus.HIDDEN) {
                    throw new ApiException(HttpStatus.CONFLICT, "Bài đăng đã ở trạng thái bị ẩn");
                }
                post.changeModerationStatus(ModerationStatus.HIDDEN);
                closeAll(pendingReports, ReportStatus.RESOLVED, admin, note);
                type = NotificationType.POST_HIDDEN;
                title = "Bài đăng của bạn đã bị ẩn";
                message = "Bài đăng \"" + post.getTitle() + "\" đã bị quản trị viên ẩn. Ghi chú: " + note;
            }
            case LOCK -> {
                requireNote(note, "Vui lòng nhập ghi chú khi khóa bài đăng");
                if (current == ModerationStatus.LOCKED) {
                    throw new ApiException(HttpStatus.CONFLICT, "Bài đăng đã ở trạng thái bị khóa");
                }
                post.changeModerationStatus(ModerationStatus.LOCKED);
                closeAll(pendingReports, ReportStatus.RESOLVED, admin, note);
                type = NotificationType.POST_LOCKED;
                title = "Bài đăng của bạn đã bị khóa";
                message = "Bài đăng \"" + post.getTitle() + "\" đã bị quản trị viên khóa. Ghi chú: " + note;
            }
            case RESTORE -> {
                if (current == ModerationStatus.NORMAL) {
                    throw new ApiException(HttpStatus.CONFLICT, "Bài đăng đang ở trạng thái bình thường");
                }
                post.changeModerationStatus(ModerationStatus.NORMAL);
                closeAll(pendingReports, ReportStatus.DISMISSED, admin, note);
                type = NotificationType.POST_RESTORED;
                title = "Bài đăng của bạn đã được khôi phục";
                message = "Bài đăng \"" + post.getTitle() + "\" đã được quản trị viên khôi phục."
                        + (note == null ? "" : " Ghi chú: " + note);
            }
            case DISMISS -> {
                if (pendingReports.isEmpty()) {
                    throw new ApiException(HttpStatus.CONFLICT, "Không có báo cáo nào đang chờ xử lý");
                }
                closeAll(pendingReports, ReportStatus.DISMISSED, admin, note);
                if (current == ModerationStatus.UNDER_REVIEW) {
                    post.changeModerationStatus(ModerationStatus.NORMAL);
                }
                type = NotificationType.REPORT_DISMISSED;
                title = "Báo cáo về bài đăng của bạn đã được bỏ qua";
                message = "Quản trị viên đã xem xét và bỏ qua các báo cáo về bài đăng \"" + post.getTitle() + "\"."
                        + (note == null ? "" : " Ghi chú: " + note);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Hành động kiểm duyệt không hợp lệ");
        }

        actionLogs.save(new ModerationActionLog(post, admin, action, note));
        notifications.notify(post.getAuthor(), type, title, message, post.getId());
        return new ModerationResult(postService.findOne(postId, principal), resolved);
    }

    // ---------- Hàm hỗ trợ ----------

    private void closeAll(List<Report> pendingReports, ReportStatus status, User admin, String note) {
        pendingReports.forEach(report -> report.close(status, admin, note));
    }

    private void requireNote(String note, String message) {
        if (note == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), sort);
    }

    private Post findPost(Long id) {
        return posts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng"));
    }

    private User currentUser(Principal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        }
        return users.findByUsername(principal.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
