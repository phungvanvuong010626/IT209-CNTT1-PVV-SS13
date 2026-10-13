# Campus Hub Guard - Đặc tả tính năng Báo cáo vi phạm và Kiểm duyệt bài đăng

Tài liệu này là hợp đồng chung giữa backend, frontend và vận hành. Mọi thay đổi API phải cập nhật tại đây.

## 1. Bài toán

Campus Hub Base cho phép sinh viên đăng bài, bình luận, bày tỏ quan tâm, nhưng không có cách nào để cộng đồng phản ánh nội dung xấu (tin rác, lừa đảo, quấy rối, không phù hợp) và quản trị viên không có nơi tập trung để xử lý. Campus Hub Guard bổ sung:

1. Người dùng đã đăng nhập báo cáo một bài đăng kèm lý do và mô tả.
2. Bài đăng bị báo cáo nhiều (ngưỡng 3 báo cáo đang chờ) tự chuyển sang trạng thái "đang kiểm duyệt" và được gắn nhãn.
3. Quản trị viên có trang Kiểm duyệt: xem tổng quan, danh sách bài chờ kiểm duyệt, danh sách báo cáo, xem chi tiết lý do, rồi quyết định ẩn bài, khóa bài, khôi phục hoặc bỏ qua báo cáo, kèm ghi chú. Mọi quyết định được lưu lịch sử.
4. Chủ bài đăng nhận thông báo trong ứng dụng khi bài bị kiểm duyệt, bị ẩn, bị khóa hoặc được khôi phục.

## 2. Mô hình dữ liệu (Flyway V2__moderation.sql)

Bảng `posts` thêm cột:
- `moderation_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL'` với CHECK IN ('NORMAL','UNDER_REVIEW','HIDDEN','LOCKED').

Bảng mới `reports`:
- `id` BIGINT PK, `post_id` FK posts (ON DELETE CASCADE), `reporter_id` FK users,
- `reason VARCHAR(30) NOT NULL` CHECK IN ('SPAM','INAPPROPRIATE','SCAM','HARASSMENT','OTHER'),
- `description VARCHAR(500) NULL`,
- `status VARCHAR(20) NOT NULL DEFAULT 'PENDING'` CHECK IN ('PENDING','RESOLVED','DISMISSED'),
- `created_at DATETIME(6) NOT NULL`, `resolved_at DATETIME(6) NULL`, `resolved_by BIGINT NULL` FK users, `resolution_note VARCHAR(500) NULL`,
- UNIQUE (`post_id`, `reporter_id`). Index theo `status`, theo `post_id`.

Bảng mới `moderation_actions`:
- `id`, `post_id` FK posts (ON DELETE CASCADE), `admin_id BIGINT NULL` FK users (NULL khi hệ thống tự động),
- `action VARCHAR(20) NOT NULL` CHECK IN ('HIDE','LOCK','RESTORE','DISMISS','AUTO_REVIEW'),
- `note VARCHAR(500) NULL`, `created_at DATETIME(6) NOT NULL`. Index theo `post_id`.

Bảng mới `notifications`:
- `id`, `user_id` FK users, `type VARCHAR(30) NOT NULL` (POST_UNDER_REVIEW, POST_HIDDEN, POST_LOCKED, POST_RESTORED, REPORT_DISMISSED),
- `title VARCHAR(160) NOT NULL`, `message VARCHAR(500) NOT NULL`, `post_id BIGINT NULL` (không FK để giữ thông báo khi bài bị xóa),
- `is_read BOOLEAN NOT NULL DEFAULT FALSE`, `created_at DATETIME(6) NOT NULL`. Index theo (`user_id`, `is_read`).

Entity JPA phải khớp để test H2 (`ddl-auto: create-drop`) chạy được. Enum Java: `ModerationStatus`, `ReportReason`, `ReportStatus`, `ModerationAction`, `NotificationType`.

## 3. Quy tắc nghiệp vụ

- Ngưỡng tự kiểm duyệt: `app.moderation.auto-review-threshold` (mặc định 3). Khi số báo cáo PENDING của một bài đạt ngưỡng và bài đang NORMAL: chuyển UNDER_REVIEW, ghi `moderation_actions` với action AUTO_REVIEW (admin_id NULL), gửi thông báo POST_UNDER_REVIEW cho chủ bài.
- Người dùng không được báo cáo bài của chính mình (400). Mỗi người chỉ báo cáo một bài một lần (409 nếu đã tồn tại bất kỳ report nào của người đó cho bài đó). Không báo cáo bài đang HIDDEN (409). Bài không tồn tại: 404. Chưa đăng nhập: 401. `reason` bắt buộc, `description` tối đa 500 ký tự (400 validation).
- Hiển thị: bài HIDDEN không xuất hiện trong danh sách công khai và trả 404 ở chi tiết, trừ khi người xem là chủ bài hoặc ADMIN. Bài UNDER_REVIEW và LOCKED vẫn hiển thị kèm nhãn.
- Bài LOCKED hoặc HIDDEN: bình luận, bật/tắt quan tâm, sửa bài, đổi trạng thái bởi chủ bài đều trả 409 "Bài đăng đã bị khóa bởi quản trị viên". ADMIN vẫn thao tác được.
- Hành động kiểm duyệt (ADMIN):
  - HIDE: moderation_status = HIDDEN; mọi report PENDING của bài -> RESOLVED (resolved_by, resolved_at, resolution_note = ghi chú); ghi lịch sử; thông báo POST_HIDDEN cho chủ bài kèm ghi chú. Ghi chú bắt buộc (400 nếu trống).
  - LOCK: moderation_status = LOCKED; report PENDING -> RESOLVED; lịch sử; thông báo POST_LOCKED. Ghi chú bắt buộc.
  - RESTORE: chỉ hợp lệ khi bài đang UNDER_REVIEW, HIDDEN hoặc LOCKED (409 nếu đang NORMAL); moderation_status = NORMAL; report PENDING còn lại -> DISMISSED; lịch sử; thông báo POST_RESTORED. Ghi chú tùy chọn.
  - DISMISS: report PENDING -> DISMISSED; nếu bài đang UNDER_REVIEW thì về NORMAL; không đổi nếu HIDDEN/LOCKED; lịch sử; thông báo REPORT_DISMISSED cho chủ bài. 409 nếu không có report PENDING nào.
- Phân trang mặc định size 10, tối đa 50.

## 4. API

Mọi lỗi trả JSON `{"message": "..."}` theo GlobalExceptionHandler hiện có. Xác thực HTTP Basic như dự án nền. Các đường dẫn `/api/admin/**` yêu cầu vai trò ADMIN (cấu hình trong SecurityConfig và `@PreAuthorize("hasRole('ADMIN')")`).

### 4.1. Mở rộng PostView (GET /api/posts, GET /api/posts/{id})
Thêm trường: `moderationStatus` (NORMAL|UNDER_REVIEW|HIDDEN|LOCKED), `pendingReportCount` (số báo cáo PENDING), `reportedByMe` (true/false, false khi chưa đăng nhập), `locked` (true khi HIDDEN hoặc LOCKED).

### 4.2. Báo cáo bài đăng (người dùng)
- `POST /api/posts/{id}/reports` body `{"reason":"SPAM","description":"..."}` -> 201 `ReportView`.
- `GET /api/posts/{id}/reports/me` -> `{"reported": true, "status": "PENDING"}` hoặc `{"reported": false, "status": null}`.
- `GET /api/report-reasons` (công khai) -> `[{"code":"SPAM","label":"Tin rác, quảng cáo"}, {"code":"INAPPROPRIATE","label":"Nội dung không phù hợp"}, {"code":"SCAM","label":"Lừa đảo, thông tin sai"}, {"code":"HARASSMENT","label":"Quấy rối, xúc phạm"}, {"code":"OTHER","label":"Lý do khác"}]`.

`ReportView`: `{id, postId, postTitle, reporter: {id, displayName}, reason, reasonLabel, description, status, createdAt, resolvedAt, resolutionNote}`.

### 4.3. Thông báo (người dùng đã đăng nhập)
- `GET /api/notifications?unreadOnly=false` -> danh sách tối đa 50, mới nhất trước: `{id, type, title, message, postId, read, createdAt}`.
- `GET /api/notifications/unread-count` -> `{"count": 2}`.
- `PATCH /api/notifications/{id}/read` -> 204 (404 nếu không phải của mình).
- `PATCH /api/notifications/read-all` -> 204.

### 4.4. Kiểm duyệt (ADMIN)
- `GET /api/admin/moderation/summary` -> `{pendingReports, underReviewPosts, hiddenPosts, lockedPosts}`.
- `GET /api/admin/moderation/posts?status=UNDER_REVIEW&page=0&size=10` -> `PageView<ModeratedPostView>`; `status` tùy chọn (mặc định: bài có moderationStatus khác NORMAL hoặc có report PENDING). `ModeratedPostView`: `{id, title, categoryName, author: {id, displayName}, moderationStatus, pendingReportCount, totalReportCount, lastReportedAt, createdAt}`.
- `GET /api/admin/reports?status=PENDING&page=0&size=10` -> `PageView<ReportView>`; `status` tùy chọn.
- `GET /api/admin/posts/{id}/moderation` -> `{post: PostView, reports: [ReportView], history: [ModerationActionView]}`; `ModerationActionView`: `{id, action, actionLabel, note, admin: {id, displayName} hoặc null, createdAt}`.
- `POST /api/admin/posts/{id}/moderation` body `{"action":"HIDE","note":"..."}` -> 200 `{post: PostView, resolvedReports: n}`. Lỗi: 400 thiếu ghi chú với HIDE/LOCK, 409 chuyển trạng thái không hợp lệ, 404 bài không tồn tại.

### 4.5. Tài khoản mẫu bổ sung (DemoDataSeeder)
Giữ `admin/admin123`, `student/student123`; thêm `student2/student123` (Trần Thu Hà) và `student3/student123` (Phạm Quốc Bảo) để demo ngưỡng 3 báo cáo. Seeder tạo thêm 2 bài mẫu nếu chưa có bài nào: một bài bình thường, một bài có dấu hiệu lừa đảo để demo.

## 5. Giao diện (React, không thêm thư viện)

- Thẻ bài đăng: nhãn "Đang kiểm duyệt" (UNDER_REVIEW), "Đã khóa" (LOCKED), "Đã ẩn" (HIDDEN, chỉ admin/chủ bài thấy).
- Chi tiết bài: nút "Báo cáo vi phạm" (ẩn khi chưa đăng nhập hoặc là chủ bài; vô hiệu kèm chữ "Bạn đã báo cáo" khi `reportedByMe`). Hộp thoại báo cáo: chọn lý do từ `/api/report-reasons`, mô tả tối đa 500 ký tự có bộ đếm, nút gửi, hiển thị lỗi từ API. Banner trạng thái kiểm duyệt cho chủ bài và admin. Khi `locked` thì ẩn form bình luận và nút quan tâm, hiện thông báo "Bài đăng đã bị khóa bởi quản trị viên".
- Thanh đầu trang: nút "Thông báo (n)" với số chưa đọc, mở danh sách, bấm để đánh dấu đã đọc, nút "Đánh dấu tất cả đã đọc". Cập nhật số chưa đọc sau mỗi hành động và định kỳ 30 giây.
- Admin: liên kết "Kiểm duyệt" trong thanh đầu trang (chỉ ADMIN). Trang Kiểm duyệt: 4 ô tổng quan; hai tab "Bài chờ xử lý" và "Báo cáo"; chọn một bài mở bảng chi tiết: danh sách báo cáo (lý do, người báo cáo, mô tả, thời gian), lịch sử kiểm duyệt, ô ghi chú và 4 nút hành động (Ẩn bài, Khóa bài, Khôi phục, Bỏ qua báo cáo) với xác nhận; nút vô hiệu theo trạng thái hiện tại; thông báo thành công/lỗi; trạng thái đang tải và rỗng.
- Điều hướng bằng state `view` ('home' | 'moderation'), không thêm react-router.

## 6. Vận hành

- Docker Compose 3 dịch vụ: `db` (mysql:8.4, volume `db-data`, healthcheck), `backend` (Spring Boot, multi-stage Dockerfile, chạy user không phải root, biến môi trường DB_URL/DB_USERNAME/DB_PASSWORD/CORS_ALLOWED_ORIGINS), `web` (frontend build tĩnh phục vụ bằng nginx:alpine, proxy `/api` và `/actuator/health` tới `backend:8080`, công khai cổng 80).
- Image phải chạy được trên linux/arm64 (máy chủ Oracle A1) và linux/amd64.
- Bí mật chỉ nằm trong `.env` trên máy chủ, repo có `.env.example`.
- CI: test backend, lint và build frontend, build image đa kiến trúc đẩy GHCR bằng GITHUB_TOKEN. CD: SSH vào máy chủ, `git pull`, `docker compose up -d --build`, kiểm tra health.
