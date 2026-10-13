package vn.edu.ptit.campushub;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Kiểm thử tích hợp tính năng báo cáo vi phạm và kiểm duyệt. Mỗi ca tự đăng ký tài khoản
 * và tạo bài đăng riêng nên không phụ thuộc thứ tự chạy hoặc dữ liệu của ca khác.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModerationApiIntegrationTest {
    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mockMvc;

    // ---------- Báo cáo của người dùng ----------

    @Test
    void reportReasonsArePublic() throws Exception {
        mockMvc.perform(get("/api/report-reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].code").value("SPAM"))
                .andExpect(jsonPath("$[0].label").value("Tin rác, quảng cáo"))
                .andExpect(jsonPath("$[2].label").value("Lừa đảo, thông tin sai"));
    }

    @Test
    void userCanReportPost() throws Exception {
        String owner = register("owner");
        String reporter = register("rep");
        long postId = createPost(owner);

        report(postId, reporter, "SPAM", "Quảng cáo lặp lại")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.reason").value("SPAM"))
                .andExpect(jsonPath("$.reasonLabel").value("Tin rác, quảng cáo"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reporter.displayName").value("Người dùng " + reporter));

        mockMvc.perform(get("/api/posts/{id}/reports/me", postId).with(httpBasic(reporter, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(true))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get("/api/posts/{id}/reports/me", postId).with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$.reported").value(false))
                .andExpect(jsonPath("$.status").value(nullValue()));

        mockMvc.perform(get("/api/posts/{id}", postId).with(httpBasic(reporter, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingReportCount").value(1))
                .andExpect(jsonPath("$.reportedByMe").value(true))
                .andExpect(jsonPath("$.moderationStatus").value("NORMAL"))
                .andExpect(jsonPath("$.locked").value(false));
        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(jsonPath("$.reportedByMe").value(false));
    }

    @Test
    void reportListViewCarriesModerationFields() throws Exception {
        String owner = register("owner");
        String reporter = register("rep");
        String keyword = UUID.randomUUID().toString();
        long postId = createPost(owner, "Bài " + keyword);
        report(postId, reporter, "SCAM", null).andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts").param("keyword", keyword).with(httpBasic(reporter, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].pendingReportCount").value(1))
                .andExpect(jsonPath("$.content[0].reportedByMe").value(true))
                .andExpect(jsonPath("$.content[0].moderationStatus").value("NORMAL"));
    }

    @Test
    void cannotReportOwnPost() throws Exception {
        String owner = register("owner");
        long postId = createPost(owner);

        report(postId, owner, "SPAM", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bạn không thể báo cáo bài đăng của chính mình"));
    }

    @Test
    void duplicateReportIsRejected() throws Exception {
        String owner = register("owner");
        String reporter = register("rep");
        long postId = createPost(owner);

        report(postId, reporter, "SPAM", null).andExpect(status().isCreated());
        report(postId, reporter, "SCAM", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bạn đã báo cáo bài đăng này"));
    }

    @Test
    void reportValidationAndAuthErrors() throws Exception {
        String owner = register("owner");
        String reporter = register("rep");
        long postId = createPost(owner);

        mockMvc.perform(post("/api/posts/{id}/reports", postId)
                        .with(httpBasic(reporter, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"thiếu lý do\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vui lòng chọn lý do báo cáo"));

        report(postId, reporter, "OTHER", "x".repeat(501)).andExpect(status().isBadRequest());

        report(999_999L, reporter, "SPAM", null).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/posts/{id}/reports", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/posts/{id}/reports/me", postId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotReportHiddenPost() throws Exception {
        String owner = register("owner");
        String reporter = register("rep");
        long postId = createPost(owner);
        moderate(postId, "HIDE", "Vi phạm nội quy").andExpect(status().isOk());

        report(postId, reporter, "SPAM", null).andExpect(status().isConflict());
    }

    // ---------- Tự động chuyển kiểm duyệt ----------

    @Test
    void threeReportsPutPostUnderReviewAndNotifyOwner() throws Exception {
        String owner = register("owner");
        long postId = createPost(owner);

        for (int i = 0; i < 2; i++) {
            report(postId, register("rep"), "SPAM", null).andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(jsonPath("$.moderationStatus").value("NORMAL"))
                .andExpect(jsonPath("$.pendingReportCount").value(2));
        mockMvc.perform(get("/api/notifications/unread-count").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$.count").value(0));

        report(postId, register("rep"), "HARASSMENT", "Xúc phạm").andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(jsonPath("$.moderationStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.pendingReportCount").value(3))
                .andExpect(jsonPath("$.locked").value(false));
        mockMvc.perform(get("/api/notifications").with(httpBasic(owner, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("POST_UNDER_REVIEW"))
                .andExpect(jsonPath("$[0].postId").value(postId))
                .andExpect(jsonPath("$[0].read").value(false));
        mockMvc.perform(get("/api/admin/posts/{id}/moderation", postId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history", hasSize(1)))
                .andExpect(jsonPath("$.history[0].action").value("AUTO_REVIEW"))
                .andExpect(jsonPath("$.history[0].admin").value(nullValue()))
                .andExpect(jsonPath("$.reports", hasSize(3)));

        // Báo cáo thứ tư không tạo thêm thông báo hay lịch sử tự động
        report(postId, register("rep"), "SPAM", null).andExpect(status().isCreated());
        mockMvc.perform(get("/api/notifications").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // ---------- Phân quyền quản trị ----------

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        String user = register("user");
        long postId = createPost(user);

        mockMvc.perform(get("/api/admin/moderation/summary").with(httpBasic(user, PASSWORD)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports").with(httpBasic(user, PASSWORD)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/posts/{id}/moderation", postId)
                        .with(httpBasic(user, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"LOCK\",\"note\":\"thử\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/moderation/summary"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/moderation/summary").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingReports").isNumber())
                .andExpect(jsonPath("$.underReviewPosts").isNumber())
                .andExpect(jsonPath("$.hiddenPosts").isNumber())
                .andExpect(jsonPath("$.lockedPosts").isNumber());
    }

    // ---------- Hành động kiểm duyệt ----------

    @Test
    void hideAndLockRequireNote() throws Exception {
        String owner = register("owner");
        long postId = createPost(owner);

        mockMvc.perform(post("/api/admin/posts/{id}/moderation", postId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"HIDE\"}"))
                .andExpect(status().isBadRequest());
        moderate(postId, "HIDE", "   ").andExpect(status().isBadRequest());
        moderate(postId, "LOCK", null).andExpect(status().isBadRequest());
        moderate(postId, "AUTO_REVIEW", "không hợp lệ").andExpect(status().isBadRequest());
        moderate(999_999L, "HIDE", "ghi chú").andExpect(status().isNotFound());
    }

    @Test
    void hidePostRemovesItFromPublicViewAndResolvesReports() throws Exception {
        String owner = register("owner");
        String reporter = register("rep");
        String other = register("other");
        String keyword = UUID.randomUUID().toString();
        long postId = createPost(owner, "Bài " + keyword);
        String reportId = JsonPath.read(
                report(postId, reporter, "SCAM", "Dấu hiệu lừa đảo").andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id").toString();

        moderate(postId, "HIDE", "Nội dung lừa đảo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedReports").value(1))
                .andExpect(jsonPath("$.post.moderationStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.post.locked").value(true))
                .andExpect(jsonPath("$.post.pendingReportCount").value(0));

        // Danh sách công khai không còn bài, admin vẫn thấy
        mockMvc.perform(get("/api/posts").param("keyword", keyword))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/posts").param("keyword", keyword).with(httpBasic(other, PASSWORD)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/posts").param("keyword", keyword).with(admin()))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Chi tiết: 404 với người khác, 200 với chủ bài và admin
        mockMvc.perform(get("/api/posts/{id}", postId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/{id}", postId).with(httpBasic(other, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/{id}", postId).with(httpBasic(owner, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.locked").value(true));
        mockMvc.perform(get("/api/posts/{id}", postId).with(admin())).andExpect(status().isOk());

        // Báo cáo chuyển RESOLVED kèm ghi chú
        mockMvc.perform(get("/api/posts/{id}/reports/me", postId).with(httpBasic(reporter, PASSWORD)))
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        mockMvc.perform(get("/api/admin/reports").param("status", "RESOLVED").param("size", "50").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + reportId + ")].resolutionNote")
                        .value(hasItem("Nội dung lừa đảo")));

        // Chủ bài nhận thông báo POST_HIDDEN
        mockMvc.perform(get("/api/notifications").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$[0].type").value("POST_HIDDEN"))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("Nội dung lừa đảo")));

        // Lịch sử ghi nhận hành động
        mockMvc.perform(get("/api/admin/posts/{id}/moderation", postId).with(admin()))
                .andExpect(jsonPath("$.history[0].action").value("HIDE"))
                .andExpect(jsonPath("$.history[0].actionLabel").value("Ẩn bài"))
                .andExpect(jsonPath("$.history[0].admin.displayName").value("Quản trị viên"));

        // Hành động ẩn lặp lại bị từ chối
        moderate(postId, "HIDE", "lần hai").andExpect(status().isConflict());
    }

    @Test
    void lockedPostRejectsInteractionsUntilRestored() throws Exception {
        String owner = register("owner");
        String reader = register("reader");
        long postId = createPost(owner);

        moderate(postId, "LOCK", "Cần làm rõ thông tin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.moderationStatus").value("LOCKED"))
                .andExpect(jsonPath("$.post.locked").value(true));
        mockMvc.perform(get("/api/notifications").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$[0].type").value("POST_LOCKED"));

        // Bài khóa vẫn hiển thị công khai
        mockMvc.perform(get("/api/posts/{id}", postId)).andExpect(status().isOk());

        comment(postId, reader).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bài đăng đã bị khóa bởi quản trị viên"));
        mockMvc.perform(post("/api/posts/{id}/reaction", postId).with(httpBasic(reader, PASSWORD)))
                .andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/posts/{id}", postId)
                        .with(httpBasic(owner, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sửa lại\",\"content\":\"Nội dung mới\",\"categoryId\":1}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/posts/{id}/status", postId)
                        .with(httpBasic(owner, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());

        // Quản trị viên vẫn bình luận được
        comment(postId, "admin", "admin123").andExpect(status().isCreated());

        moderate(postId, "RESTORE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.moderationStatus").value("NORMAL"))
                .andExpect(jsonPath("$.post.locked").value(false));
        mockMvc.perform(get("/api/notifications").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$[0].type").value("POST_RESTORED"));
        comment(postId, reader).andExpect(status().isCreated());
    }

    @Test
    void restoreRequiresNonNormalPost() throws Exception {
        String owner = register("owner");
        long postId = createPost(owner);

        moderate(postId, "RESTORE", "không cần").andExpect(status().isConflict());
    }

    @Test
    void dismissWithoutPendingReportsIsConflict() throws Exception {
        String owner = register("owner");
        long postId = createPost(owner);

        moderate(postId, "DISMISS", "không có gì").andExpect(status().isConflict());
    }

    @Test
    void dismissClosesReportsAndReturnsUnderReviewPostToNormal() throws Exception {
        String owner = register("owner");
        long postId = createPost(owner);
        String firstReporter = register("rep");
        report(postId, firstReporter, "SPAM", null).andExpect(status().isCreated());
        report(postId, register("rep"), "SPAM", null).andExpect(status().isCreated());
        report(postId, register("rep"), "SPAM", null).andExpect(status().isCreated());
        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(jsonPath("$.moderationStatus").value("UNDER_REVIEW"));

        moderate(postId, "DISMISS", "Báo cáo không có cơ sở")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedReports").value(3))
                .andExpect(jsonPath("$.post.moderationStatus").value("NORMAL"))
                .andExpect(jsonPath("$.post.pendingReportCount").value(0));
        mockMvc.perform(get("/api/posts/{id}/reports/me", postId).with(httpBasic(firstReporter, PASSWORD)))
                .andExpect(jsonPath("$.status").value("DISMISSED"));
        mockMvc.perform(get("/api/notifications").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$[0].type").value("REPORT_DISMISSED"));

        // Đã bỏ qua hết, không còn báo cáo chờ nên bỏ qua lần nữa là xung đột
        moderate(postId, "DISMISS", null).andExpect(status().isConflict());
    }

    @Test
    void dismissKeepsLockedPostLocked() throws Exception {
        String owner = register("owner");
        long postId = createPost(owner);
        moderate(postId, "LOCK", "Khóa để xem xét").andExpect(status().isOk());
        report(postId, register("rep"), "OTHER", null).andExpect(status().isCreated());

        moderate(postId, "DISMISS", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.moderationStatus").value("LOCKED"));
    }

    @Test
    void adminListsModerationQueue() throws Exception {
        String owner = register("owner");
        String keyword = UUID.randomUUID().toString();
        long postId = createPost(owner, "Bài " + keyword);
        report(postId, register("rep"), "SPAM", null).andExpect(status().isCreated());
        report(postId, register("rep"), "SCAM", null).andExpect(status().isCreated());
        report(postId, register("rep"), "OTHER", null).andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/moderation/posts").param("status", "UNDER_REVIEW")
                        .param("size", "50").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + postId + ")].totalReportCount").value(hasItem(3)))
                .andExpect(jsonPath("$.content[?(@.id == " + postId + ")].pendingReportCount").value(hasItem(3)))
                .andExpect(jsonPath("$.content[?(@.id == " + postId + ")].moderationStatus")
                        .value(hasItem("UNDER_REVIEW")));
        mockMvc.perform(get("/api/admin/moderation/posts").param("size", "50").with(admin()))
                .andExpect(jsonPath("$.content[*].id").value(hasItem((int) postId)));
        mockMvc.perform(get("/api/admin/reports").param("status", "PENDING").param("size", "50").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].postId").value(hasItem((int) postId)));
        mockMvc.perform(get("/api/admin/reports").param("size", "500").with(admin()))
                .andExpect(jsonPath("$.size").value(50));
    }

    // ---------- Thông báo ----------

    @Test
    void notificationsCanBeMarkedAsRead() throws Exception {
        String owner = register("owner");
        String stranger = register("stranger");
        long firstPost = createPost(owner);
        long secondPost = createPost(owner);
        moderate(firstPost, "LOCK", "Khóa lần một").andExpect(status().isOk());
        moderate(secondPost, "HIDE", "Ẩn lần hai").andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/unread-count").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(get("/api/notifications/unread-count").with(httpBasic(stranger, PASSWORD)))
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());

        String body = mockMvc.perform(get("/api/notifications").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        // Mới nhất trước
        mockMvc.perform(get("/api/notifications").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$[0].type").value("POST_HIDDEN"))
                .andExpect(jsonPath("$[1].type").value("POST_LOCKED"));
        Number newestId = JsonPath.read(body, "$[0].id");

        // Không đọc được thông báo của người khác
        mockMvc.perform(patch("/api/notifications/{id}/read", newestId).with(httpBasic(stranger, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/notifications/{id}/read", newestId).with(httpBasic(owner, PASSWORD)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/unread-count").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/notifications").param("unreadOnly", "true").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("POST_LOCKED"));

        mockMvc.perform(patch("/api/notifications/read-all").with(httpBasic(owner, PASSWORD)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/unread-count").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$.count").value(is(0)));
        mockMvc.perform(get("/api/notifications").param("unreadOnly", "true").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/notifications").with(httpBasic(owner, PASSWORD)))
                .andExpect(jsonPath("$[*].read", not(hasItem(false))));
    }

    @Test
    void demoAccountsAreSeeded() throws Exception {
        for (String username : new String[] {"student2", "student3"}) {
            mockMvc.perform(get("/api/auth/me").with(httpBasic(username, "student123")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("USER"));
        }
    }

    // ---------- Hàm hỗ trợ ----------

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return httpBasic("admin", "admin123");
    }

    /** Đăng ký tài khoản mới với tên duy nhất, trả về username. Tên hiển thị là "Người dùng " + username. */
    private String register(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"displayName\":\"Người dùng %s\",\"password\":\"%s\"}"
                                .formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated());
        return username;
    }

    private long createPost(String username) throws Exception {
        return createPost(username, "Bài kiểm duyệt " + UUID.randomUUID());
    }

    private long createPost(String username, String title) throws Exception {
        String body = mockMvc.perform(post("/api/posts")
                        .with(httpBasic(username, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"content\":\"Nội dung bài đăng để kiểm thử\",\"categoryId\":1}"
                                .formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions report(long postId, String username, String reason, String description)
            throws Exception {
        String descriptionJson = description == null ? "null" : "\"" + description + "\"";
        return mockMvc.perform(post("/api/posts/{id}/reports", postId)
                .with(httpBasic(username, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"%s\",\"description\":%s}".formatted(reason, descriptionJson)));
    }

    private ResultActions moderate(long postId, String action, String note) throws Exception {
        String noteJson = note == null ? "null" : "\"" + note + "\"";
        return mockMvc.perform(post("/api/admin/posts/{id}/moderation", postId)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"%s\",\"note\":%s}".formatted(action, noteJson)));
    }

    private ResultActions comment(long postId, String username) throws Exception {
        return comment(postId, username, PASSWORD);
    }

    private ResultActions comment(long postId, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/posts/{id}/comments", postId)
                .with(httpBasic(username, password))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Bình luận thử nghiệm\"}"));
    }
}
