package vn.edu.ptit.campushub.post;

import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.campushub.category.Category;
import vn.edu.ptit.campushub.category.CategoryRepository;
import vn.edu.ptit.campushub.comment.Comment;
import vn.edu.ptit.campushub.comment.CommentRepository;
import vn.edu.ptit.campushub.common.ApiException;
import vn.edu.ptit.campushub.moderation.ModerationActionLogRepository;
import vn.edu.ptit.campushub.moderation.ModerationStatus;
import vn.edu.ptit.campushub.moderation.ReportRepository;
import vn.edu.ptit.campushub.post.PostDtos.AuthorView;
import vn.edu.ptit.campushub.post.PostDtos.CategoryView;
import vn.edu.ptit.campushub.post.PostDtos.CommentRequest;
import vn.edu.ptit.campushub.post.PostDtos.CommentView;
import vn.edu.ptit.campushub.post.PostDtos.PostRequest;
import vn.edu.ptit.campushub.post.PostDtos.PostView;
import vn.edu.ptit.campushub.reaction.Reaction;
import vn.edu.ptit.campushub.reaction.ReactionRepository;
import vn.edu.ptit.campushub.user.Role;
import vn.edu.ptit.campushub.user.User;
import vn.edu.ptit.campushub.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class PostService {
    static final String LOCKED_MESSAGE = "Bài đăng đã bị khóa bởi quản trị viên";

    private final PostRepository posts;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final CommentRepository comments;
    private final ReactionRepository reactions;
    private final ReportRepository reports;
    private final ModerationActionLogRepository moderationLogs;

    public PostService(PostRepository posts, CategoryRepository categories, UserRepository users,
                       CommentRepository comments, ReactionRepository reactions, ReportRepository reports,
                       ModerationActionLogRepository moderationLogs) {
        this.posts = posts;
        this.categories = categories;
        this.users = users;
        this.comments = comments;
        this.reactions = reactions;
        this.reports = reports;
        this.moderationLogs = moderationLogs;
    }

    public PageView<PostView> search(String keyword, Long categoryId, int page, int size, Principal principal) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        User current = findCurrentUser(principal);
        Page<Post> result = posts.searchPublished(keyword == null ? "" : keyword.trim(), categoryId,
                isAdmin(current),
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<Post> content = result.getContent();
        ReportStats stats = loadReportStats(content, current);
        List<PostView> views = content.stream()
                .map(post -> toView(post, current, false, stats))
                .toList();
        return new PageView<>(views, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    public PostView findOne(Long id, Principal principal) {
        User current = findCurrentUser(principal);
        return toView(findVisiblePost(id, current), current, true);
    }

    @Transactional
    public PostView create(PostRequest request, Principal principal) {
        User author = currentUser(principal);
        Category category = findCategory(request.categoryId());
        Post post = posts.save(new Post(request.title().trim(), request.content().trim(), category, author));
        return toView(post, author, true);
    }

    @Transactional
    public PostView update(Long id, PostRequest request, Principal principal) {
        Post post = findPost(id);
        User user = currentUser(principal);
        requireOwnerOrAdmin(post, user);
        requireNotLocked(post, user);
        post.update(request.title().trim(), request.content().trim(), findCategory(request.categoryId()));
        return toView(post, user, true);
    }

    @Transactional
    public PostView changeStatus(Long id, PostStatus status, Principal principal) {
        Post post = findPost(id);
        User user = currentUser(principal);
        if (status == PostStatus.HIDDEN && user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ quản trị viên được ẩn bài đăng");
        }
        requireOwnerOrAdmin(post, user);
        requireNotLocked(post, user);
        post.changeStatus(status);
        return toView(post, user, true);
    }

    @Transactional
    public void delete(Long id, Principal principal) {
        Post post = findPost(id);
        requireOwnerOrAdmin(post, currentUser(principal));
        comments.deleteByPostId(id);
        reactions.deleteByPostId(id);
        reports.deleteByPostId(id);
        moderationLogs.deleteByPostId(id);
        posts.delete(post);
    }

    @Transactional
    public CommentView addComment(Long postId, CommentRequest request, Principal principal) {
        User user = currentUser(principal);
        Post post = findVisiblePost(postId, user);
        requireNotLocked(post, user);
        Comment comment = comments.save(new Comment(request.content().trim(), post, user));
        return CommentView.from(comment);
    }

    @Transactional
    public boolean toggleReaction(Long postId, Principal principal) {
        User user = currentUser(principal);
        Post post = findVisiblePost(postId, user);
        requireNotLocked(post, user);
        return reactions.findByPostIdAndUserId(postId, user.getId())
                .map(reaction -> {
                    reactions.delete(reaction);
                    return false;
                })
                .orElseGet(() -> {
                    reactions.save(new Reaction(post, user));
                    return true;
                });
    }

    private PostView toView(Post post, User current, boolean includeComments) {
        return toView(post, current, includeComments, loadReportStats(List.of(post), current));
    }

    private PostView toView(Post post, User current, boolean includeComments, ReportStats stats) {
        List<CommentView> commentViews = includeComments
                ? comments.findByPostIdOrderByCreatedAtAsc(post.getId()).stream().map(CommentView::from).toList()
                : List.of();
        return new PostView(post.getId(), post.getTitle(), post.getContent(), post.getStatus(),
                new CategoryView(post.getCategory().getId(), post.getCategory().getName(), post.getCategory().getSlug()),
                new AuthorView(post.getAuthor().getId(), post.getAuthor().getUsername(),
                        post.getAuthor().getDisplayName()),
                post.getCreatedAt(), post.getUpdatedAt(), reactions.countByPostId(post.getId()),
                comments.countByPostId(post.getId()),
                current != null && reactions.findByPostIdAndUserId(post.getId(), current.getId()).isPresent(),
                post.getModerationStatus(), stats.pendingCount(post.getId()), stats.reportedByMe(post.getId()),
                post.isLocked(), commentViews);
    }

    /** Tính số báo cáo chờ xử lý và trạng thái đã báo cáo của cả trang bằng hai truy vấn gộp. */
    private ReportStats loadReportStats(List<Post> list, User current) {
        if (list.isEmpty()) {
            return new ReportStats(Map.of(), Set.of());
        }
        List<Long> ids = list.stream().map(Post::getId).toList();
        Map<Long, Long> pending = new HashMap<>();
        for (Object[] row : reports.countPendingByPostIds(ids)) {
            pending.put((Long) row[0], ((Number) row[1]).longValue());
        }
        Set<Long> reported = current == null
                ? Set.of()
                : new HashSet<>(reports.findReportedPostIds(current.getId(), ids));
        return new ReportStats(pending, reported);
    }

    private Post findVisiblePost(Long id, User current) {
        Post post = findPost(id);
        boolean admin = isAdmin(current);
        boolean owner = current != null && post.getAuthor().getId().equals(current.getId());
        if ((post.getStatus() == PostStatus.HIDDEN && !admin)
                || (post.getModerationStatus() == ModerationStatus.HIDDEN && !admin && !owner)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng");
        }
        return post;
    }

    private Post findPost(Long id) {
        return posts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng"));
    }

    private Category findCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Chuyên mục không hợp lệ"));
    }

    private User currentUser(Principal principal) {
        User user = findCurrentUser(principal);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        }
        return user;
    }

    private User findCurrentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return users.findByUsername(principal.getName()).orElse(null);
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    private void requireOwnerOrAdmin(Post post, User user) {
        if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền thay đổi bài đăng này");
        }
    }

    /** Bài bị ẩn hoặc khóa bởi quản trị viên: chỉ ADMIN còn được thao tác. */
    private void requireNotLocked(Post post, User user) {
        if (post.isLocked() && user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.CONFLICT, LOCKED_MESSAGE);
        }
    }

    private record ReportStats(Map<Long, Long> pending, Set<Long> reported) {
        long pendingCount(Long postId) {
            return pending.getOrDefault(postId, 0L);
        }

        boolean reportedByMe(Long postId) {
            return reported.contains(postId);
        }
    }

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
