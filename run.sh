#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE_FILE="infra/docker/compose.yaml"
ENV_FILE="infra/env/.env.local"
COMPOSE="docker compose -f ${COMPOSE_FILE} --env-file ${ENV_FILE}"

usage() {
  cat <<'USAGE'
사용법: ./run.sh [명령]

  (없음) | up   앱 + MySQL + MinIO 기동 (변경 시 이미지 재빌드)
  down          컨테이너 종료 (데이터 볼륨은 유지)
  clean         컨테이너 + 볼륨까지 전부 삭제 (DB·이미지 데이터 초기화)
  restart       앱 컨테이너만 재빌드 후 재기동
  logs          앱 로그 실시간 확인
  ps            컨테이너 상태 확인
  trust         Caddy 로컬 CA 를 시스템에 신뢰 등록 (브라우저 인증서 경고 제거, sudo 필요)
USAGE
}

require_env() {
  if [ ! -f "$ENV_FILE" ]; then
    echo "[run] ${ENV_FILE} 파일이 없습니다. infra/env/.env.local.example 을 복사합니다."
    cp infra/env/.env.local.example "$ENV_FILE"
  fi
}

CA_FILE="/tmp/dear-jolly-caddy-root.crt"

# Caddy 내장 CA 의 루트 인증서를 꺼낸다 (컨테이너가 떠 있어야 한다)
extract_ca() {
  docker exec dear-jolly-caddy cat /data/caddy/pki/authorities/local/root.crt > "$CA_FILE" 2>/dev/null \
    && [ -s "$CA_FILE" ]
}

# 이미 신뢰되고 있는지 확인
ca_trusted() {
  extract_ca || return 1
  security verify-cert -c "$CA_FILE" >/dev/null 2>&1
}

case "${1:-up}" in
  up)
    require_env
    echo "[run] 컨테이너 기동 (앱 / MySQL / MinIO)"
    $COMPOSE up -d --build
    $COMPOSE ps
    echo "[run] 앱      : https://localhost:$(grep -E '^APP_PORT=' "$ENV_FILE" | cut -d= -f2)  (Caddy HTTPS)"
    echo "[run] HTTP    : http://localhost:$(grep -E '^APP_HTTP_PORT=' "$ENV_FILE" | cut -d= -f2) → HTTPS 자동 리다이렉트"
    echo "[run] MinIO   : http://localhost:$(grep -E '^MINIO_CONSOLE_PORT=' "$ENV_FILE" | cut -d= -f2) (콘솔)"
    if [ "$(uname)" = "Darwin" ] && ! ca_trusted; then
      echo "[run] 브라우저 인증서 경고를 없애려면: ./run.sh trust"
    fi
    echo "[run] 로그 확인: ./run.sh logs"
    ;;
  down)
    $COMPOSE down
    ;;
  clean)
    echo "[run] 볼륨까지 삭제합니다 (DB·MinIO 데이터 초기화)"
    $COMPOSE down -v
    ;;
  restart)
    require_env
    $COMPOSE up -d --build app
    ;;
  logs)
    $COMPOSE logs -f app
    ;;
  ps)
    $COMPOSE ps
    ;;
  trust)
    [ "$(uname)" = "Darwin" ] || { echo "[run] macOS 전용 명령입니다." >&2; exit 1; }
    extract_ca || { echo "[run] Caddy 컨테이너가 떠 있어야 합니다. 먼저 ./run.sh 를 실행하세요." >&2; exit 1; }
    if ca_trusted; then
      echo "[run] 이미 신뢰 등록돼 있습니다: $(openssl x509 -in "$CA_FILE" -noout -subject | sed 's/subject=//')"
      exit 0
    fi
    echo "[run] Caddy 로컬 CA 를 시스템 키체인에 등록합니다 (관리자 비밀번호 입력 필요)"
    sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain "$CA_FILE"
    if ca_trusted; then
      echo "[run] 등록 완료. 브라우저를 새로 열면 https://localhost:$(grep -E '^APP_PORT=' "$ENV_FILE" | cut -d= -f2) 가 경고 없이 열린다"
    else
      echo "[run] 등록에 실패했습니다." >&2
      exit 1
    fi
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "[run] 알 수 없는 명령: $1" >&2
    usage
    exit 1
    ;;
esac
