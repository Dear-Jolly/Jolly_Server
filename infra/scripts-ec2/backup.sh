#!/usr/bin/env bash
# EC2 **서버 위에서** 실행되는 백업 스크립트. cron 이 매일 1회 호출한다.
# cron 등록과 디렉터리 생성은 deploy.sh 가 배포할 때마다 자동으로 처리하므로
# 사람이 서버에 들어가 손으로 할 일은 없다.
#
#   MySQL : mysqldump → ${BACKUP_PATH}/mysql/dearjolly-YYYYMMDD-HHMM.sql.gz
#   MinIO : 데이터 디렉터리 tar → ${BACKUP_PATH}/minio/minio-YYYYMMDD-HHMM.tar.gz
#
# 보관 개수는 BACKUP_RETENTION (기본 3) 이며, 초과분은 오래된 것부터 지운다.
# 수동 실행: cd ${DEPLOY_PATH} && ./backup.sh
set -euo pipefail

cd "$(dirname "$0")"

# 배포 시 생성된 .env 에서 설정을 읽는다
[ -f .env ] || { echo "[backup] .env 가 없습니다. 배포 후 실행하세요." >&2; exit 1; }
set -a
# shellcheck disable=SC1091
. ./.env
set +a

BACKUP_PATH="${BACKUP_PATH:-$HOME/backup}"
BACKUP_RETENTION="${BACKUP_RETENTION:-3}"
MINIO_DATA_PATH="${MINIO_DATA_PATH:-$HOME/mount/minio}"
STAMP=$(date +%Y%m%d-%H%M)

mkdir -p "${BACKUP_PATH}/mysql" "${BACKUP_PATH}/minio"

# ===== MySQL =====
# --single-transaction: InnoDB 를 락 없이 일관된 시점으로 덤프한다 (서비스 중단 없음)
MYSQL_FILE="${BACKUP_PATH}/mysql/${MYSQL_DATABASE}-${STAMP}.sql.gz"
if docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" dear-jolly-mysql \
     mysqldump -u root --single-transaction --routines --events --databases "$MYSQL_DATABASE" \
     2>/dev/null | gzip > "$MYSQL_FILE"; then
  echo "[backup] MySQL 완료: ${MYSQL_FILE} ($(du -h "$MYSQL_FILE" | cut -f1))"
else
  rm -f "$MYSQL_FILE"
  echo "[backup] MySQL 실패" >&2
  exit 1
fi

# ===== MinIO =====
# 호스트 마운트 디렉터리를 그대로 묶는다. 우표 이미지는 쓰기가 드물어 정지 없이 떠도 된다.
MINIO_FILE="${BACKUP_PATH}/minio/minio-${STAMP}.tar.gz"
if tar -czf "$MINIO_FILE" -C "$(dirname "$MINIO_DATA_PATH")" "$(basename "$MINIO_DATA_PATH")"; then
  echo "[backup] MinIO 완료: ${MINIO_FILE} ($(du -h "$MINIO_FILE" | cut -f1))"
else
  rm -f "$MINIO_FILE"
  echo "[backup] MinIO 실패" >&2
  exit 1
fi

# ===== 보관 개수 초과분 삭제 =====
prune() {
  local dir="$1" pattern="$2" old
  old=$(find "$dir" -maxdepth 1 -name "$pattern" -type f -print0 2>/dev/null \
        | xargs -0 -r ls -1t 2>/dev/null | tail -n +$((BACKUP_RETENTION + 1)))
  if [ -n "$old" ]; then
    echo "$old" | while read -r f; do
      rm -f "$f"
      echo "[backup] 오래된 백업 삭제: $(basename "$f")"
    done
  fi
}
prune "${BACKUP_PATH}/mysql" '*.sql.gz'
prune "${BACKUP_PATH}/minio" '*.tar.gz'

echo "[backup] 완료 ($(date '+%Y-%m-%d %H:%M:%S')). 보관 중: MySQL $(find "${BACKUP_PATH}/mysql" -name '*.sql.gz' | wc -l | tr -d ' ')개, MinIO $(find "${BACKUP_PATH}/minio" -name '*.tar.gz' | wc -l | tr -d ' ')개"
