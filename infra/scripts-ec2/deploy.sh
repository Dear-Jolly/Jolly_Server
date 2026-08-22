#!/usr/bin/env bash
# EC2 **서버 위에서** 실행되는 블루그린 무중단 배포 스크립트.
# 로컬에서 직접 돌리는 스크립트가 아니라, GitHub Actions 또는 infra/scripts-local/aws-manage.sh 가
# 이 파일을 EC2 의 ${DEPLOY_PATH} 로 전송한 뒤 원격 실행한다.
#
# 트래픽 경로: 프론트 → Caddy(80) → 활성 컨테이너(blue:8081 / green:8082)
# 데이터는 컨테이너가 아니라 EC2 호스트(${MYSQL_DATA_PATH} · ${MINIO_DATA_PATH})에 저장되며,
# 매일 1회 도는 백업 cron 도 이 스크립트가 배포 때마다 자동으로 등록/갱신한다.
# 필요한 환경변수: APP_IMAGE (필수), ECR_REGISTRY·AWS_REGION (ECR pull 시)
#
# ECR 인증 순서:
#   1) EC2 인스턴스 역할(AmazonEC2ContainerRegistryReadOnly)로 서버가 직접 토큰 발급  ← 기본
#   2) ECR_PASSWORD 가 주어지면 그 토큰 사용 (인스턴스 역할이 없는 서버용)
#   3) 둘 다 없으면 이미 받아둔 로컬 이미지로 진행 (수동 재배포)
set -euo pipefail

cd "$(dirname "$0")"

: "${APP_IMAGE:?APP_IMAGE 가 필요합니다}"
ECR_REGISTRY="${ECR_REGISTRY:-}"
ECR_PASSWORD="${ECR_PASSWORD:-}"
AWS_REGION="${AWS_REGION:-}"

STATE_FILE=".deploy_color"
CADDY_DIR="caddy"                       # Caddy 컨테이너에 마운트되는 설정 디렉터리
CADDY_TEMPLATE="Caddyfile.template"     # 전송받은 원본 (deploy.sh 와 같은 위치)

# Caddyfile 생성: 활성 색상 + 서비스 주소 + TLS 방식을 치환한다.
# 주소가 IP 면 공인 인증서를 받을 수 없으므로 Caddy 내장 CA(tls internal)를 쓰고,
# 도메인이면 지시어를 비워 Let's Encrypt 자동 발급에 맡긴다.
render_caddyfile() {
  local color="$1" port="$2" tls_directive=""
  if [[ "$SITE_ADDRESS" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    tls_directive="tls internal"
  fi
  sed -e "s|__SITE_ADDRESS__|${SITE_ADDRESS}|g" \
      -e "s|__TLS_DIRECTIVE__|${tls_directive}|" \
      -e "s|__ACTIVE_UPSTREAM__|dear-jolly-app-${color}:${port}|" \
      "$CADDY_TEMPLATE" > "${CADDY_DIR}/Caddyfile"
}

# 포트는 compose 와 동일한 값을 써야 하므로 .env 를 그대로 참조한다
read_env() {
  [ -f .env ] || return 0
  grep -E "^$1=" .env | head -1 | cut -d= -f2- | sed -E 's/[[:space:]]+#.*$//; s/[[:space:]]+$//'
}
BLUE_PORT="${APP_BLUE_PORT:-$(read_env APP_BLUE_PORT)}"
GREEN_PORT="${APP_GREEN_PORT:-$(read_env APP_GREEN_PORT)}"
BLUE_PORT="${BLUE_PORT:-8081}"
GREEN_PORT="${GREEN_PORT:-8082}"
SITE_ADDRESS="${SITE_ADDRESS:-$(read_env SITE_ADDRESS)}"
SITE_ADDRESS="${SITE_ADDRESS:-$(read_env EC2_HOST)}"
HEALTH_TIMEOUT=30   # 5초 간격 × 30회 = 최대 150초
KEEP_IMAGES=2       # 현재 + 직전 이미지 1개만 보관

# ===== 1. 전환 대상 색상·포트 결정 =====
CURRENT=$(cat "$STATE_FILE" 2>/dev/null || echo "")
if [ "$CURRENT" = "blue" ]; then
  TARGET="green"
  TARGET_PORT="$GREEN_PORT"
else
  TARGET="blue"
  TARGET_PORT="$BLUE_PORT"
fi
echo "[deploy] 현재: ${CURRENT:-없음} → 신규: ${TARGET} (:${TARGET_PORT})"
echo "[deploy] 이미지: ${APP_IMAGE}"

# ===== 2. ECR 로그인 및 이미지 pull =====
ecr_login() {
  if [ -n "$ECR_PASSWORD" ]; then
    echo "[deploy] 전달받은 토큰으로 ECR 로그인"
    echo "$ECR_PASSWORD" | docker login --username AWS --password-stdin "$ECR_REGISTRY"
    return 0
  fi
  if [ -n "$ECR_REGISTRY" ] && command -v aws >/dev/null; then
    echo "[deploy] EC2 인스턴스 역할로 ECR 로그인"
    aws ecr get-login-password ${AWS_REGION:+--region "$AWS_REGION"} \
      | docker login --username AWS --password-stdin "$ECR_REGISTRY"
    return 0
  fi
  return 1
}

if ecr_login; then
  docker pull "$APP_IMAGE"
else
  echo "[deploy] ECR 인증 불가 → 이미 받아둔 이미지로 진행: ${APP_IMAGE}"
  docker image inspect "$APP_IMAGE" >/dev/null
fi

# ===== 3. 데이터·백업 디렉터리 준비 (호스트 바인드 마운트 대상) =====
set -a
# shellcheck disable=SC1091
. ./.env
set +a
MYSQL_DATA_PATH="${MYSQL_DATA_PATH:-$HOME/mount/mysql}"
MINIO_DATA_PATH="${MINIO_DATA_PATH:-$HOME/mount/minio}"
BACKUP_PATH="${BACKUP_PATH:-$HOME/backup}"
mkdir -p "$MYSQL_DATA_PATH" "$MINIO_DATA_PATH" "${BACKUP_PATH}/mysql" "${BACKUP_PATH}/minio"
echo "[deploy] 데이터 경로: ${MYSQL_DATA_PATH} · ${MINIO_DATA_PATH}"

# ===== 4. 백업 cron 등록 (매 배포마다 최신 설정으로 덮어쓴다) =====
if [ -f backup.sh ]; then
  chmod +x backup.sh
  CRON_MARK="# dear-jolly backup"
  CRON_LINE="${BACKUP_SCHEDULE:-30 3 * * *} cd $(pwd) && ./backup.sh >> ${BACKUP_PATH}/backup.log 2>&1 ${CRON_MARK}"
  { crontab -l 2>/dev/null | grep -vF "$CRON_MARK" || true; echo "$CRON_LINE"; } | crontab -
  echo "[deploy] 백업 cron 등록: ${BACKUP_SCHEDULE:-30 3 * * *} (보관 ${BACKUP_RETENTION:-3}개)"
fi

# ===== 5. 인프라 컨테이너 기동 (이미 떠 있으면 그대로 유지) =====
docker compose up -d mysql minio minio-init

# ===== 6. Caddy 설정 준비 후 기동 (최초 배포면 신규 색상으로 생성) =====
mkdir -p "$CADDY_DIR"
if [ ! -f "${CADDY_DIR}/Caddyfile" ]; then
  render_caddyfile "$TARGET" "$TARGET_PORT"
fi
docker compose up -d caddy

# ===== 7. 신규 색상 컨테이너 기동 =====
echo "[deploy] ${TARGET} 컨테이너 기동 (:${TARGET_PORT})"
docker compose up -d --no-deps "app-${TARGET}"

# ===== 8. 신규 컨테이너 헬스체크 (구버전이 계속 트래픽 처리 중) =====
for i in $(seq 1 "$HEALTH_TIMEOUT"); do
  STATUS=$(docker inspect -f '{{.State.Health.Status}}' "dear-jolly-app-${TARGET}" 2>/dev/null || echo starting)
  if [ "$STATUS" = "healthy" ]; then
    echo "[deploy] ${TARGET} healthy (${i}회차)"
    break
  fi
  if [ "$i" -eq "$HEALTH_TIMEOUT" ]; then
    echo "[deploy] ${TARGET} 헬스체크 실패. 트래픽 전환 없이 롤백합니다." >&2
    docker compose logs --tail 100 "app-${TARGET}" >&2
    docker rm -f "dear-jolly-app-${TARGET}" >/dev/null 2>&1 || true
    exit 1
  fi
  echo "[deploy] ${TARGET} 기동 대기... (${i}/${HEALTH_TIMEOUT}, 상태: ${STATUS})"
  sleep 5
done

# ===== 9. 트래픽 전환 (Caddy graceful reload, 연결 끊김 없음) =====
render_caddyfile "$TARGET" "$TARGET_PORT"
docker exec dear-jolly-caddy caddy validate --config /etc/caddy/Caddyfile
docker exec dear-jolly-caddy caddy reload --config /etc/caddy/Caddyfile
echo "$TARGET" > "$STATE_FILE"
echo "[deploy] 트래픽 전환 완료 → ${TARGET} (:${TARGET_PORT}), 진입점 https://${SITE_ADDRESS}"

# ===== 10. 구버전 컨테이너 정리 =====
if [ -n "$CURRENT" ] && [ "$CURRENT" != "$TARGET" ]; then
  echo "[deploy] 구버전(${CURRENT}) 컨테이너 종료"
  docker rm -f "dear-jolly-app-${CURRENT}" >/dev/null 2>&1 || true
fi

# ===== 11. 이미지 정리 (현재 + 직전 1개만 보관) =====
IMAGE_REPO="${APP_IMAGE%:*}"
# 컨테이너가 물고 있는 이미지는 대상에서 제외한다 (현재 색상·롤백용 구버전 보호)
IN_USE=$(docker ps -aq | xargs -r docker inspect -f '{{.Image}}' 2>/dev/null | cut -c8-19 | sort -u)
CANDIDATES=$(docker images "$IMAGE_REPO" --format '{{.ID}}' | awk '!seen[$0]++' | tail -n +$((KEEP_IMAGES + 1)))
if [ -n "$IN_USE" ] && [ -n "$CANDIDATES" ]; then
  OLD_IMAGES=$(echo "$CANDIDATES" | grep -vxF -f <(echo "$IN_USE") || true)
else
  OLD_IMAGES="$CANDIDATES"
fi
if [ -n "$OLD_IMAGES" ]; then
  echo "[deploy] 오래된 이미지 삭제:"
  echo "$OLD_IMAGES" | xargs -r docker rmi -f || true
else
  echo "[deploy] 삭제할 오래된 이미지 없음"
fi
docker image prune -f >/dev/null
[ -n "$ECR_REGISTRY" ] && docker logout "$ECR_REGISTRY" >/dev/null 2>&1 || true

echo "[deploy] 배포 완료 (활성: ${TARGET}, 포트 ${TARGET_PORT})"
docker compose ps
