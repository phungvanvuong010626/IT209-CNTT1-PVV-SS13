# Hướng dẫn triển khai Campus Hub Guard

Tài liệu mô tả quy trình đưa mã nguồn từ GitHub lên máy chủ Oracle Cloud, cách thiết lập lần đầu, kiểm tra, rollback, xem log và sao lưu dữ liệu.

## 1. Tổng quan quy trình

```text
 Lập trình viên
      | git push / merge pull request vào main
      v
 +-------------------------- GitHub Actions --------------------------+
 |  backend-test ----+                                                |
 |                   +--> docker-images --> deploy                    |
 |  frontend-build --+    (QEMU + Buildx,   (SSH vào máy chủ)         |
 |                         amd64 + arm64,                             |
 |                         đẩy lên GHCR)                              |
 +--------------------------------------------------|-----------------+
                                                    | ssh ubuntu@161.118.239.231
                                                    v
 +------------- Máy chủ Oracle Cloud (ARM64, Ubuntu 24.04) -----------+
 |  /opt/campus-hub-guard                                             |
 |    deploy/deploy.sh:                                               |
 |      git fetch + reset --hard origin/main                          |
 |      docker compose up -d --build   (build trực tiếp từ mã nguồn)  |
 |      chờ health  http://localhost/actuator/health                  |
 |                                                                    |
 |  [web :80] --> [backend :8080] --> [db :3306, volume db-data]      |
 +--------------------------------------------------------------------+
                                                    |
                                                    v
                              http://161.118.239.231  (công khai)
```

Ghi chú:

- Trên máy chủ, image được build trực tiếp từ mã nguồn (`docker compose up -d --build`) nên không phụ thuộc quyền kéo image từ GHCR. Image trên GHCR (`ghcr.io/letoan0902/campus-hub-guard-backend`, `ghcr.io/letoan0902/campus-hub-guard-web`) vẫn được CI đẩy lên để kiểm chứng build cho linux/amd64 và dùng khi cần.
- Job `docker-images` và `deploy` chỉ chạy khi push vào nhánh `main`. Các nhánh khác và pull request chỉ chạy kiểm thử, lint, build.
- Job `deploy` dùng `environment: production`; có thể bật quy tắc phê duyệt thủ công trong Settings > Environments.

## 2. Secrets và variables trên GitHub

Vào repo `letoan0902/campus-hub-guard` > Settings > Secrets and variables > Actions.

| Loại | Tên | Giá trị |
|---|---|---|
| Secret | `DEPLOY_SSH_KEY` | Private key SSH dành riêng cho CD (toàn bộ nội dung tệp khóa) |
| Variable | `DEPLOY_HOST` | `161.118.239.231` |
| Variable | `DEPLOY_USER` | `ubuntu` |
| Variable | `DEPLOY_PATH` | `/opt/campus-hub-guard` |

Job đẩy image dùng `GITHUB_TOKEN` có sẵn với quyền `packages: write`, không cần thêm secret.

Tạo cặp khóa riêng cho CD (không dùng khóa cá nhân), trên máy cá nhân:

```bash
ssh-keygen -t ed25519 -C "campus-hub-guard-cd" -f ./cd_key -N ""
# Nội dung cd_key (private)     -> secret DEPLOY_SSH_KEY
# Nội dung cd_key.pub (public)  -> thêm vào ~/.ssh/authorized_keys trên máy chủ
```

Sau khi nạp xong, xóa tệp khóa private khỏi máy cá nhân và không commit khóa vào repo.

## 3. Thiết lập máy chủ lần đầu

Điều kiện: máy chủ Ubuntu 24.04, đã mở Ingress TCP cổng 80 trong Security List / Network Security Group của Oracle Cloud.

1. SSH vào máy chủ bằng user `ubuntu`.
2. Tải và chạy script thiết lập (chạy một lần, có thể chạy lại an toàn):

   ```bash
   curl -fsSL https://raw.githubusercontent.com/letoan0902/campus-hub-guard/main/deploy/setup-server.sh -o setup-server.sh
   bash setup-server.sh
   ```

   Script cài Docker nếu thiếu, thêm user vào nhóm `docker`, mở cổng 80 trong iptables và ufw (lưu bền), clone repo vào `/opt/campus-hub-guard`, tạo `.env` với mật khẩu ngẫu nhiên.
3. Đăng xuất và đăng nhập lại để quyền nhóm `docker` có hiệu lực.
4. Kiểm tra `/opt/campus-hub-guard/.env` (không in nội dung lên nơi công khai). Đảm bảo `CORS_ALLOWED_ORIGINS=http://161.118.239.231`.
5. Thêm khóa công khai của CD vào `~/.ssh/authorized_keys`.
6. Triển khai thử thủ công lần đầu:

   ```bash
   bash /opt/campus-hub-guard/deploy/deploy.sh
   ```

7. Cấu hình secrets và variables trên GitHub (mục 2), sau đó push vào `main` hoặc chạy thủ công workflow bằng `workflow_dispatch`.

Lưu ý: trên máy chủ còn có một nginx khác dùng cổng 8085, hệ thống này không được đụng tới; Campus Hub Guard chỉ dùng cổng 80.

## 4. Kiểm tra sức khỏe

```bash
# Trên máy chủ hoặc từ bên ngoài
curl -fsS http://161.118.239.231/actuator/health        # kỳ vọng {"status":"UP",...}
curl -fsS -o /dev/null -w '%{http_code}\n' http://161.118.239.231/   # kỳ vọng 200

# Trạng thái container và healthcheck
cd /opt/campus-hub-guard
docker compose ps
cat .deploy-info        # commit đang chạy
```

CD tự thực hiện hai lệnh `curl` trên (health thử lại 10 lần, mỗi lần cách 6 giây) và ghi commit đang chạy vào phần tóm tắt của lượt chạy (Job summary).

## 5. Xem log

```bash
cd /opt/campus-hub-guard
docker compose logs -f --tail=100 backend
docker compose logs -f --tail=100 web
docker compose logs --tail=100 db
```

## 6. Rollback

Khi bản mới gặp lỗi, quay về commit tốt đã biết:

```bash
cd /opt/campus-hub-guard
git fetch --all --prune
git log --oneline -n 10              # tìm commit cần quay lại
git checkout <sha>
APP_VERSION=$(git rev-parse --short HEAD) docker compose --env-file .env up -d --build
curl -fsS http://localhost/actuator/health
```

Sau khi sửa lỗi trên `main`, quay lại nhánh để các lần triển khai sau hoạt động bình thường (`deploy.sh` luôn `reset --hard origin/main`, nên lần deploy kế tiếp cũng tự đưa máy chủ về `main`):

```bash
git checkout main
```

Cách khác: tạo commit `git revert` trên `main` rồi để CD triển khai lại, cách này giữ lịch sử rõ ràng và là cách ưu tiên.

Migration cơ sở dữ liệu (Flyway) chỉ chạy tiến. Nếu bản lỗi đã chạy migration mới, cần khôi phục dữ liệu từ bản sao lưu (mục 7) hoặc viết migration sửa lỗi.

## 7. Sao lưu và khôi phục MySQL

Sao lưu (xuất tệp SQL, khuyến nghị):

```bash
cd /opt/campus-hub-guard
set -a; . ./.env; set +a
mkdir -p ~/backups
docker compose exec -T db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines "$MYSQL_DATABASE"' \
  | gzip > ~/backups/campus_hub_$(date +%Y%m%d_%H%M%S).sql.gz
```

Khôi phục:

```bash
gunzip -c ~/backups/<tep>.sql.gz | docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
```

Sao lưu nguyên volume (cần dừng db để nhất quán):

```bash
docker compose stop db
docker run --rm -v campus-hub-guard_db-data:/data -v "$HOME/backups":/backup alpine \
  tar czf /backup/db-data_$(date +%Y%m%d_%H%M%S).tar.gz -C /data .
docker compose start db
```

Tên volume thực tế kiểm tra bằng `docker volume ls`.

## 8. Sự cố thường gặp

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Truy cập từ ngoài bị treo | Chưa mở cổng 80 ở Security List hoặc iptables | Mở Ingress TCP 80 trên Oracle Cloud, chạy lại `setup-server.sh` |
| `deploy` lỗi `Permission denied (publickey)` | Khóa công khai chưa có trong `authorized_keys` hoặc sai secret | Kiểm tra `DEPLOY_SSH_KEY` và `authorized_keys` |
| `deploy` lỗi `permission denied` với docker | User chưa thuộc nhóm `docker` hoặc chưa đăng nhập lại | `sudo usermod -aG docker ubuntu`, đăng nhập lại |
| Backend không lên healthy | Sai mật khẩu DB trong `.env` sau khi volume đã khởi tạo | Xem `docker compose logs backend db`; mật khẩu MySQL chỉ áp dụng khi volume còn trống |
| Lỗi CORS trên trình duyệt | `CORS_ALLOWED_ORIGINS` không khớp địa chỉ truy cập | Sửa `.env`, chạy lại `deploy.sh` |

## Lưu ý khi chạy trên Oracle Cloud và về CORS

- Máy ảo Oracle Cloud có hai lớp tường lửa: iptables/UFW trong máy (script `deploy/setup-server.sh` đã mở cổng 80) và Security List hoặc Network Security Group của VCN trên giao diện Oracle Cloud. Nếu truy cập từ Internet bị treo (timeout) trong khi `curl http://localhost/actuator/health` trên máy chủ trả 200, hãy thêm Ingress Rule TCP cổng 80 (và 443) với Source CIDR `0.0.0.0/0` trong Security List của subnet.
- Bước "Kiểm tra sức khỏe sau triển khai" trong workflow gọi tới địa chỉ công khai, nên bước này chỉ xanh khi cổng 80 đã mở ở cả hai lớp. Việc triển khai trên máy chủ vẫn hoàn tất độc lập với bước kiểm tra này (xem `.deploy-info` và `docker compose ps` trên máy chủ).
- `CORS_ALLOWED_ORIGINS` trong `.env` phải đúng origin mà trình duyệt dùng để mở ứng dụng (ví dụ `http://161.118.239.231`). Trình duyệt gửi header Origin với mọi yêu cầu POST/PATCH, nên nếu origin không khớp thì backend trả 403 dù frontend và backend cùng được phục vụ qua Nginx.
