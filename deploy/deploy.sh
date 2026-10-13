#!/usr/bin/env bash
# Script triển khai Campus Hub Guard, chạy trên máy chủ (được CD gọi qua SSH).
# Cách dùng: DEPLOY_PATH=/opt/campus-hub-guard bash deploy/deploy.sh
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/opt/campus-hub-guard}"
BRANCH="${DEPLOY_BRANCH:-main}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"

log() { printf '[deploy %s] %s\n' "$(date '+%H:%M:%S')" "$*"; }

cd "$DEPLOY_PATH"

if [ ! -f .env ]; then
  log "Không tìm thấy $DEPLOY_PATH/.env. Hãy chạy deploy/setup-server.sh hoặc tạo .env từ .env.example."
  exit 1
fi

# 1. Cập nhật mã nguồn về đúng origin/${BRANCH}.
log "Cập nhật mã nguồn từ origin/${BRANCH}"
git fetch --all --prune
git reset --hard "origin/${BRANCH}"

# 2. Ghi lại phiên bản đang triển khai và truyền cho Docker Compose.
GIT_SHA="$(git rev-parse --short HEAD)"
export APP_VERSION="$GIT_SHA"
{
  echo "GIT_SHA=${GIT_SHA}"
  echo "GIT_COMMIT=$(git rev-parse HEAD)"
  echo "DEPLOYED_AT=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
} > .deploy-info
log "Phiên bản triển khai: ${GIT_SHA}"

# 3. Build và khởi động lại các dịch vụ; dữ liệu MySQL nằm trong volume nên được giữ nguyên.
log "Build và khởi động bằng Docker Compose"
docker compose --env-file .env up -d --build --remove-orphans

# 4. Chờ ứng dụng sẵn sàng qua web (nginx) -> backend.
WEB_PORT_VALUE="$(grep -E '^WEB_PORT=' .env | tail -n 1 | cut -d= -f2- | tr -d '[:space:]' || true)"
WEB_PORT_VALUE="${WEB_PORT_VALUE:-${WEB_PORT:-80}}"
HEALTH_URL="http://localhost:${WEB_PORT_VALUE}/actuator/health"

log "Chờ health check tại ${HEALTH_URL} (tối đa ${HEALTH_TIMEOUT} giây)"
elapsed=0
until curl -fsS "$HEALTH_URL" > /dev/null 2>&1; do
  if [ "$elapsed" -ge "$HEALTH_TIMEOUT" ]; then
    log "Health check thất bại sau ${HEALTH_TIMEOUT} giây. Nhật ký gần nhất của backend:"
    docker compose --env-file .env logs --tail=80 backend || true
    docker compose --env-file .env ps || true
    exit 1
  fi
  sleep 3
  elapsed=$((elapsed + 3))
done
log "Ứng dụng đã sẵn sàng sau khoảng ${elapsed} giây"

# 5. Dọn image không còn dùng và hiển thị trạng thái.
docker image prune -f > /dev/null
docker compose --env-file .env ps
log "Triển khai hoàn tất, commit ${GIT_SHA}"
