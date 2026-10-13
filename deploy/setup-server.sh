#!/usr/bin/env bash
# Thiết lập máy chủ mới (Ubuntu) cho Campus Hub Guard. Chạy MỘT lần bằng user thường có sudo, ví dụ ubuntu.
# Script có tính idempotent: chạy lại nhiều lần không gây hại.
#
# Cách dùng:
#   bash setup-server.sh
# Biến tùy chọn:
#   REPO_URL     (mặc định https://github.com/letoan0902/campus-hub-guard.git)
#   DEPLOY_PATH  (mặc định /opt/campus-hub-guard)
#   DEPLOY_BRANCH (mặc định main)
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/letoan0902/campus-hub-guard.git}"
DEPLOY_PATH="${DEPLOY_PATH:-/opt/campus-hub-guard}"
BRANCH="${DEPLOY_BRANCH:-main}"
APP_USER="${SUDO_USER:-$(id -un)}"

log() { printf '[setup] %s\n' "$*"; }

if [ "$(id -u)" -eq 0 ]; then
  SUDO=""
else
  SUDO="sudo"
fi

# 1. Cài Docker (kèm plugin compose) nếu chưa có.
if ! command -v docker > /dev/null 2>&1; then
  log "Cài Docker bằng script chính thức get.docker.com"
  curl -fsSL https://get.docker.com | $SUDO sh
else
  log "Docker đã có: $(docker --version)"
fi
$SUDO systemctl enable --now docker > /dev/null 2>&1 || true

if ! docker compose version > /dev/null 2>&1 && ! $SUDO docker compose version > /dev/null 2>&1; then
  log "Cài plugin docker-compose-plugin"
  $SUDO apt-get update -y
  $SUDO apt-get install -y docker-compose-plugin
fi

# 2. Thêm user vào nhóm docker để chạy docker không cần sudo.
if ! id -nG "$APP_USER" | tr ' ' '\n' | grep -qx docker; then
  log "Thêm user ${APP_USER} vào nhóm docker (cần đăng nhập lại để có hiệu lực)"
  $SUDO usermod -aG docker "$APP_USER"
fi

# 3. Mở cổng 80 trong iptables (Oracle Cloud Ubuntu chặn mặc định) và ufw, lưu bền.
if ! $SUDO iptables -C INPUT -p tcp --dport 80 -j ACCEPT > /dev/null 2>&1; then
  log "Mở cổng 80 trong iptables"
  # Chèn trước quy tắc REJECT mặc định của image Oracle (vị trí 5 nếu có, ngược lại đầu chuỗi).
  $SUDO iptables -I INPUT 5 -p tcp --dport 80 -m conntrack --ctstate NEW -j ACCEPT 2> /dev/null \
    || $SUDO iptables -I INPUT -p tcp --dport 80 -j ACCEPT
fi
if command -v ufw > /dev/null 2>&1; then
  if $SUDO ufw status | grep -q "Status: active"; then
    log "Mở cổng 80 trong ufw"
    $SUDO ufw allow 80/tcp > /dev/null
  fi
fi
if ! dpkg -s iptables-persistent > /dev/null 2>&1; then
  log "Cài iptables-persistent để lưu quy tắc"
  echo iptables-persistent iptables-persistent/autosave_v4 boolean true | $SUDO debconf-set-selections
  echo iptables-persistent iptables-persistent/autosave_v6 boolean true | $SUDO debconf-set-selections
  $SUDO env DEBIAN_FRONTEND=noninteractive apt-get install -y iptables-persistent
fi
$SUDO sh -c 'iptables-save > /etc/iptables/rules.v4'

# 4. Tạo thư mục triển khai và clone mã nguồn nếu chưa có.
if [ ! -d "$DEPLOY_PATH/.git" ]; then
  log "Clone ${REPO_URL} vào ${DEPLOY_PATH}"
  $SUDO mkdir -p "$DEPLOY_PATH"
  $SUDO chown "$APP_USER":"$APP_USER" "$DEPLOY_PATH"
  git clone --branch "$BRANCH" "$REPO_URL" "$DEPLOY_PATH"
else
  log "Mã nguồn đã có tại ${DEPLOY_PATH}"
fi

# 5. Tạo .env từ .env.example với mật khẩu ngẫu nhiên nếu chưa có.
cd "$DEPLOY_PATH"
if [ ! -f .env ]; then
  log "Tạo .env với mật khẩu ngẫu nhiên"
  DB_PASS="$(openssl rand -hex 16)"
  DB_ROOT_PASS="$(openssl rand -hex 16)"
  sed -e "s/^DB_PASSWORD=.*/DB_PASSWORD=${DB_PASS}/" \
      -e "s/^DB_ROOT_PASSWORD=.*/DB_ROOT_PASSWORD=${DB_ROOT_PASS}/" \
      .env.example > .env
  chmod 600 .env
else
  log ".env đã tồn tại, giữ nguyên"
fi

cat <<EOF

Thiết lập máy chủ hoàn tất. Các bước tiếp theo:
  1. Đăng xuất rồi đăng nhập SSH lại để quyền nhóm docker có hiệu lực.
  2. Kiểm tra ${DEPLOY_PATH}/.env, chỉnh CORS_ALLOWED_ORIGINS theo địa chỉ công khai nếu cần.
  3. Thêm khóa công khai của CD vào ~/.ssh/authorized_keys của user ${APP_USER}.
  4. Trên GitHub, cấu hình secret DEPLOY_SSH_KEY và variables DEPLOY_HOST, DEPLOY_USER, DEPLOY_PATH.
  5. Chạy lần đầu: bash ${DEPLOY_PATH}/deploy/deploy.sh
  6. Kiểm tra: curl http://localhost/actuator/health
  7. Nhớ mở cổng 80 (Ingress Rule TCP) trong Security List / NSG của Oracle Cloud nếu truy cập từ ngoài không được.
EOF
