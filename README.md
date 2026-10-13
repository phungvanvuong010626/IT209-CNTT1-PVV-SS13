# Campus Hub Guard

Campus Hub Guard là phiên bản phát triển thêm từ dự án nền Campus Hub (Mini Project Session 13). Hệ thống bổ sung quy trình báo cáo vi phạm và kiểm duyệt bài đăng cho không gian chung của sinh viên, đồng thời được đóng gói bằng Docker, kiểm thử và triển khai tự động bằng CI/CD lên máy chủ Oracle Cloud.

Đường dẫn công khai: http://161.118.239.231

## Bài toán

Campus Hub cho phép sinh viên đăng sự kiện, tìm đồ thất lạc, hỏi đáp, trao đổi đồ dùng và tìm nhóm. Tuy nhiên bản nền không có cách nào để cộng đồng phản ánh nội dung xấu (tin rác, lừa đảo, quấy rối, không phù hợp), và quản trị viên không có nơi tập trung để xử lý. Campus Hub Guard giải quyết bằng quy trình:

1. Người dùng đã đăng nhập báo cáo một bài đăng kèm lý do và mô tả.
2. Bài đăng bị nhiều báo cáo (ngưỡng mặc định 3 báo cáo đang chờ) tự chuyển sang trạng thái "đang kiểm duyệt" và được gắn nhãn.
3. Quản trị viên xem tổng quan, danh sách bài chờ xử lý, chi tiết báo cáo, rồi quyết định ẩn bài, khóa bài, khôi phục hoặc bỏ qua báo cáo, kèm ghi chú. Mọi quyết định được lưu lịch sử.
4. Chủ bài đăng nhận thông báo trong ứng dụng khi bài bị kiểm duyệt, bị ẩn, bị khóa hoặc được khôi phục.

Đặc tả đầy đủ (mô hình dữ liệu, quy tắc nghiệp vụ, API, giao diện) nằm tại [docs/SPEC.md](docs/SPEC.md).

## Thành viên và phân công

| Thành viên | Vai trò | Phụ trách |
|---|---|---|
| Lê Phú Toàn | Nhóm trưởng | Phân công, review, kiểm thử, merge |
| Trần Đăng Việt | Backend | API báo cáo vi phạm, migration cơ sở dữ liệu |
| Lê Phương Linh | Backend | API kiểm duyệt của quản trị viên, thông báo |
| Lê Duy Minh | Frontend | Giao diện báo cáo vi phạm, thông báo |
| Phùng Văn Vượng | Frontend | Trang kiểm duyệt, hoàn thiện giao diện |
| Phạm Phương Anh | DevOps | Docker, CI/CD, triển khai |

## Chức năng

Từ dự án nền:

- Xem, tìm kiếm và lọc bài đăng theo chuyên mục.
- Đăng ký, đăng nhập bằng HTTP Basic.
- Tạo bài đăng, bình luận, bày tỏ quan tâm, chủ bài đánh dấu hoàn tất.
- Quản trị viên quản lý trạng thái bài đăng.
- Health check tại `/actuator/health`.

Phát triển thêm trong Campus Hub Guard:

- Báo cáo bài đăng với 5 lý do (tin rác, nội dung không phù hợp, lừa đảo, quấy rối, lý do khác); mỗi người chỉ báo cáo một bài một lần, không báo cáo bài của chính mình.
- Tự động chuyển bài sang "đang kiểm duyệt" khi đạt ngưỡng báo cáo (cấu hình bằng `MODERATION_AUTO_REVIEW_THRESHOLD`).
- Trang Kiểm duyệt cho quản trị viên: tổng quan, bài chờ xử lý, danh sách báo cáo, chi tiết, lịch sử và 4 hành động (ẩn, khóa, khôi phục, bỏ qua).
- Bài bị khóa hoặc bị ẩn không cho bình luận, quan tâm, chỉnh sửa; bài bị ẩn không hiển thị công khai.
- Thông báo trong ứng dụng cho chủ bài, có số chưa đọc và cập nhật định kỳ.
- Đóng gói Docker (backend, frontend, MySQL), CI/CD GitHub Actions, triển khai tự động lên Oracle Cloud ARM64.

## Kiến trúc

```text
                Trình duyệt
                     |
                     |  HTTP :80
                     v
  +------------------------------------------+
  |  Docker Compose trên máy chủ Oracle Cloud |
  |                                          |
  |   web (nginx:alpine)                     |
  |     - phục vụ bản build React            |
  |     - proxy /api và /actuator/health     |
  |           |                              |
  |           v  http://backend:8080         |
  |   backend (Spring Boot 3.5, Java 21)     |
  |           |                              |
  |           v  JDBC                        |
  |   db (MySQL 8.4) -- volume db-data       |
  +------------------------------------------+
```

Chỉ dịch vụ `web` công khai cổng ra ngoài; `backend` và `db` chỉ truy cập được trong mạng nội bộ `app-net`.

Cấu trúc thư mục:

```text
backend/        Spring Boot (Gradle), Flyway migration, kiểm thử JUnit
frontend/       React + Vite, nginx.conf, Dockerfile
deploy/         setup-server.sh (thiết lập lần đầu), deploy.sh (triển khai)
docs/           SPEC.md (đặc tả), DEPLOYMENT.md (triển khai)
.github/        Workflow CI/CD
docker-compose.yml, .env.example
```

## Chạy khi phát triển

Yêu cầu: JDK 21, Node.js 22, MySQL 8.

Tạo cơ sở dữ liệu `campus_hub` và đặt biến môi trường nếu thông tin kết nối khác mặc định:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/campus_hub"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "mat-khau-cua-ban"
```

Chạy backend:

```powershell
cd backend
./gradlew.bat bootRun
```

Chạy frontend ở terminal khác:

```powershell
cd frontend
npm install
npm run dev
```

Frontend chạy tại `http://localhost:5173` và chuyển tiếp `/api` tới backend ở cổng `8080`.

Kiểm tra trước khi đẩy mã:

```powershell
cd backend; ./gradlew.bat test
cd ../frontend; npm ci; npm run lint; npm run build
```

## Chạy bằng Docker Compose

Yêu cầu: Docker và Docker Compose v2.

```bash
cp .env.example .env
# Sửa DB_PASSWORD, DB_ROOT_PASSWORD trong .env
docker compose up -d --build
docker compose ps
```

Ứng dụng chạy tại `http://localhost` (hoặc cổng `WEB_PORT` trong `.env`). Kiểm tra: `curl http://localhost/actuator/health`.

Dừng và giữ dữ liệu: `docker compose down`. Xóa cả dữ liệu MySQL: `docker compose down -v`.

Các biến cấu hình (xem `.env.example`):

| Biến | Ý nghĩa | Mặc định |
|---|---|---|
| `DB_NAME` | Tên cơ sở dữ liệu | `campus_hub` |
| `DB_USER` | Tài khoản ứng dụng MySQL | `campus` |
| `DB_PASSWORD` | Mật khẩu tài khoản ứng dụng | cần đổi |
| `DB_ROOT_PASSWORD` | Mật khẩu root MySQL | cần đổi |
| `CORS_ALLOWED_ORIGINS` | Các origin được gọi API | `http://161.118.239.231` |
| `WEB_PORT` | Cổng công khai của web | `80` |
| `MODERATION_AUTO_REVIEW_THRESHOLD` | Ngưỡng báo cáo tự kiểm duyệt | `3` |

## Tài khoản demo

Được tạo khi ứng dụng khởi động lần đầu. Chỉ dùng để trình diễn.

| Vai trò | Tên đăng nhập | Mật khẩu |
|---|---|---|
| Quản trị viên | `admin` | `admin123` |
| Sinh viên | `student` | `student123` |
| Sinh viên | `student2` | `student123` |
| Sinh viên | `student3` | `student123` |

Kịch bản demo ngưỡng kiểm duyệt: đăng nhập lần lượt `student`, `student2`, `student3` và cùng báo cáo một bài của người khác (không phải bài của chính mình); bài sẽ chuyển sang "đang kiểm duyệt", sau đó đăng nhập `admin` để xử lý tại trang Kiểm duyệt.

## API mới

Chi tiết request, response và mã lỗi xem tại [docs/SPEC.md](docs/SPEC.md), mục 4.

| Phương thức | Đường dẫn | Mục đích | Xác thực |
|---|---|---|---|
| `POST` | `/api/posts/{id}/reports` | Báo cáo bài đăng | Có |
| `GET` | `/api/posts/{id}/reports/me` | Trạng thái báo cáo của tôi | Có |
| `GET` | `/api/report-reasons` | Danh sách lý do báo cáo | Không |
| `GET` | `/api/notifications` | Danh sách thông báo | Có |
| `GET` | `/api/notifications/unread-count` | Số thông báo chưa đọc | Có |
| `PATCH` | `/api/notifications/{id}/read` | Đánh dấu đã đọc | Có |
| `PATCH` | `/api/notifications/read-all` | Đánh dấu tất cả đã đọc | Có |
| `GET` | `/api/admin/moderation/summary` | Tổng quan kiểm duyệt | ADMIN |
| `GET` | `/api/admin/moderation/posts` | Bài chờ xử lý | ADMIN |
| `GET` | `/api/admin/reports` | Danh sách báo cáo | ADMIN |
| `GET` | `/api/admin/posts/{id}/moderation` | Chi tiết và lịch sử kiểm duyệt | ADMIN |
| `POST` | `/api/admin/posts/{id}/moderation` | Ẩn, khóa, khôi phục, bỏ qua | ADMIN |

## CI/CD và triển khai

Workflow `.github/workflows/ci-cd.yml` gồm bốn job:

1. `backend-test`: chạy kiểm thử backend, lưu báo cáo.
2. `frontend-build`: lint và build frontend.
3. `docker-images`: (chỉ khi push vào `main`) build image cho linux/amd64 `linux/amd64`, `linux/arm64` và đẩy lên GHCR.
4. `deploy`: (chỉ khi push vào `main`) SSH vào máy chủ, chạy `deploy/deploy.sh`, kiểm tra health và trang chủ.

Hướng dẫn thiết lập secrets, setup máy chủ lần đầu, rollback, xem log và backup xem tại [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Phiên bản bảo vệ

Phiên bản bảo vệ: tag `v1.0.1` trên nhánh `main` của https://github.com/letoan0902/campus-hub-guard (v1.0.0 cộng bản vá bỏ hộp thoại đăng nhập của trình duyệt khi chưa đăng nhập), đang chạy tại http://161.118.239.231 (mã commit ghi trong tệp `.deploy-info` trên máy chủ).

## Lưu ý bảo mật

Tài khoản demo chỉ phục vụ trình diễn; cần đổi hoặc loại bỏ trước khi dùng thật. HTTP Basic chỉ an toàn khi đặt sau HTTPS. Bí mật (mật khẩu cơ sở dữ liệu, khóa SSH) không được commit; chỉ nằm trong `.env` trên máy chủ và GitHub Secrets.
