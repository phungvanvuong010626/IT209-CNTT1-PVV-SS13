# Kết quả kiểm thử backend

Chạy bằng Docker vì máy chưa cài Gradle/JDK 21.

## Lệnh đã chạy

```bash
cd backend
MSYS_NO_PATHCONV=1 docker run --rm -v "$PWD:/home/gradle/project" -w /home/gradle/project gradle:8.14-jdk21 gradle test --no-daemon
```

## Kết quả

```
> Task :compileJava
> Task :processResources
> Task :classes
> Task :compileTestJava
> Task :processTestResources
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 3m 57s
5 actionable tasks: 5 executed
```

| Lớp kiểm thử | Số ca | Thất bại | Lỗi | Bỏ qua |
|---|---|---|---|---|
| `PostApiIntegrationTest` (dự án nền) | 4 | 0 | 0 | 0 |
| `ModerationApiIntegrationTest` (mới) | 19 | 0 | 0 | 0 |
| Tổng | 23 | 0 | 0 | 0 |

## Các ca của `ModerationApiIntegrationTest`

- Danh sách lý do báo cáo công khai (`GET /api/report-reasons`).
- Báo cáo thành công trả 201; `reports/me`, `reportedByMe`, `pendingReportCount` đúng.
- Danh sách bài (`GET /api/posts`) mang đủ trường kiểm duyệt.
- Báo cáo bài của chính mình trả 400; báo cáo trùng trả 409; báo cáo bài đã bị ẩn trả 409.
- Thiếu `reason`, mô tả quá 500 ký tự trả 400; bài không tồn tại trả 404; chưa đăng nhập trả 401.
- Ba người báo cáo: bài chuyển UNDER_REVIEW, ghi lịch sử AUTO_REVIEW (admin null), chủ bài nhận thông báo POST_UNDER_REVIEW; báo cáo thứ tư không tạo thêm thông báo.
- USER gọi `/api/admin/**` trả 403; chưa đăng nhập trả 401; ADMIN xem được tổng quan.
- HIDE/LOCK thiếu hoặc trống ghi chú trả 400; hành động AUTO_REVIEW trả 400; bài không tồn tại trả 404.
- HIDE thành công: bài biến mất khỏi danh sách công khai, chi tiết 404 với người khác và khách, 200 với chủ bài và ADMIN; báo cáo chuyển RESOLVED kèm ghi chú; chủ bài nhận POST_HIDDEN; lịch sử ghi nhận; HIDE lần hai trả 409.
- LOCK rồi bình luận, quan tâm, sửa bài, đổi trạng thái bởi chủ bài trả 409; ADMIN vẫn bình luận được; RESTORE đưa về NORMAL và bình luận lại được.
- RESTORE khi bài NORMAL trả 409; DISMISS khi không có báo cáo PENDING trả 409.
- DISMISS đóng báo cáo, đưa bài UNDER_REVIEW về NORMAL, giữ nguyên bài LOCKED, gửi REPORT_DISMISSED.
- Hàng đợi kiểm duyệt của admin (bài và báo cáo), giới hạn size tối đa 50.
- Thông báo: `unread-count`, đánh dấu một thông báo, không đọc được thông báo của người khác (404), `read-all`, lọc `unreadOnly`.
- Tài khoản mẫu `student2`, `student3` đăng nhập được.

## Kiểm tra bổ sung

Migration `V1__create_schema.sql` rồi `V2__moderation.sql` được áp dụng thành công lên MySQL 8.4 thật (container tạm, đã xóa), bảng `reports`, `moderation_actions`, `notifications` và cột `posts.moderation_status` được tạo đúng.
