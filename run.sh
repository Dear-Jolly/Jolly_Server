#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE="docker compose"

usage() {
  cat <<'USAGE'
사용법: ./run.sh [명령]

  (없음) | up   앱 + MySQL + MinIO 기동 (변경 시 이미지 재빌드)
  down          컨테이너 종료 (데이터 볼륨은 유지)
  clean         컨테이너 + 볼륨까지 전부 삭제 (DB·이미지 데이터 초기화)
  restart       앱 컨테이너만 재빌드 후 재기동
  logs          앱 로그 실시간 확인
  ps            컨테이너 상태 확인
USAGE
}

require_env() {
  if [ ! -f .env ]; then
    echo "[run] .env 파일이 없습니다. .env.example 을 복사합니다."
    cp .env.example .env
  fi
}

case "${1:-up}" in
  up)
    require_env
    echo "[run] 컨테이너 기동 (앱 / MySQL / MinIO)"
    $COMPOSE up -d --build
    $COMPOSE ps
    echo "[run] 앱      : http://localhost:$(grep -E '^APP_PORT=' .env | cut -d= -f2)"
    echo "[run] MinIO   : http://localhost:$(grep -E '^MINIO_CONSOLE_PORT=' .env | cut -d= -f2) (콘솔)"
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
  -h|--help|help)
    usage
    ;;
  *)
    echo "[run] 알 수 없는 명령: $1" >&2
    usage
    exit 1
    ;;
esac
