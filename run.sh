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
  down          컨테이너 종료 (데이터는 유지)
  reset-db      DB 를 비우고 스키마를 처음부터 다시 생성 (MinIO 는 그대로)
  clean         컨테이너 + MySQL·MinIO 데이터까지 전부 삭제
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
  reset-db)
    # 스키마를 통째로 비우고 Flyway 로 다시 만든다. V1__init.sql 을 고쳤을 때 쓴다.
    # checksum 이 달라지면 Flyway 가 기동을 거부한다.
    #
    # DROP DATABASE 가 아니라 테이블만 지우는 이유는, 스키마를 지우면 그 스키마에
    # 걸린 사용자 권한까지 함께 사라져 앱이 접속하지 못하기 때문이다.
    require_env
    DB=$(grep -E '^MYSQL_DATABASE=' "$ENV_FILE" | cut -d= -f2-)
    ROOT_PW=$(grep -E '^MYSQL_ROOT_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)

    printf '[run] %s 의 테이블을 전부 삭제하고 마이그레이션을 다시 적용합니다. 계속할까요? [y/N] ' "$DB"
    read -r answer
    [ "$answer" = "y" ] || [ "$answer" = "Y" ] || { echo "[run] 취소했습니다."; exit 0; }

    docker exec dear-jolly-mysql mysql -uroot -p"$ROOT_PW" -N -e "
      SET FOREIGN_KEY_CHECKS = 0;
      SET @sql = (SELECT IFNULL(CONCAT('DROP TABLE ', GROUP_CONCAT('\`', table_name, '\`')), 'SELECT 1')
                  FROM information_schema.tables WHERE table_schema = '${DB}');
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
      SET FOREIGN_KEY_CHECKS = 1;
    " "$DB" 2>&1 | grep -v 'Using a password' || true

    echo "[run] 테이블 삭제 완료. 앱을 재기동해 스키마를 다시 만듭니다."
    # --force-recreate 가 없으면 컨테이너 스펙이 그대로일 때 Compose 가 재생성을 건너뛴다.
    # 그러면 앱이 계속 떠 있어 Flyway 가 다시 돌지 않고, DB 만 빈 채로 남는다.
    $COMPOSE up -d --build --force-recreate app
    echo "[run] 로그 확인: ./run.sh logs"
    ;;
  clean)
    # MySQL·MinIO 는 named volume 이 아니라 호스트 바인드 마운트다.
    # down -v 는 named volume 만 지우므로 그것만으로는 데이터가 남는다.
    require_env
    COMPOSE_DIR="infra/docker"
    MYSQL_PATH=$(grep -E '^MYSQL_DATA_PATH=' "$ENV_FILE" | cut -d= -f2-)
    MINIO_PATH=$(grep -E '^MINIO_DATA_PATH=' "$ENV_FILE" | cut -d= -f2-)
    MYSQL_DIR=$(cd "$COMPOSE_DIR" && cd "$(dirname "${MYSQL_PATH:-../../../mount/mysql}")" 2>/dev/null && pwd)/$(basename "${MYSQL_PATH:-mysql}")
    MINIO_DIR=$(cd "$COMPOSE_DIR" && cd "$(dirname "${MINIO_PATH:-../../../mount/minio}")" 2>/dev/null && pwd)/$(basename "${MINIO_PATH:-minio}")

    echo "[run] 아래 디렉터리를 통째로 삭제합니다. 되돌릴 수 없습니다."
    echo "        MySQL : ${MYSQL_DIR}"
    echo "        MinIO : ${MINIO_DIR}"
    printf '[run] 계속할까요? [y/N] '
    read -r answer
    [ "$answer" = "y" ] || [ "$answer" = "Y" ] || { echo "[run] 취소했습니다."; exit 0; }

    $COMPOSE down -v
    # 컨테이너가 root 로 쓴 파일이 섞여 있어 호스트 권한으로는 지우지 못할 수 있다.
    docker run --rm -v "$(dirname "$MYSQL_DIR")":/target alpine:3 \
      sh -c "rm -rf /target/$(basename "$MYSQL_DIR") /target/$(basename "$MINIO_DIR")"
    echo "[run] 삭제 완료. ./run.sh 로 다시 기동하면 빈 상태에서 시작한다."
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
