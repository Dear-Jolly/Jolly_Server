#!/usr/bin/env bash
# 목 사용자(MockUserSeeder 가 넣은 계정)의 Access·Refresh 토큰을 로컬에서 직접 발급한다.
#
# 소셜 로그인을 거치지 않고 인증이 필요한 API 를 두드리기 위한 것이다. 서버에 토큰 발급용
# 엔드포인트를 열면 그 구멍이 운영까지 따라가므로, 대신 .env.local 의 JWT_SECRET 으로
# 서버와 똑같은 HS256 토큰을 여기서 서명한다.
#
# 발급한 Refresh Token 은 USERS.refresh_token 에 함께 기록한다. 그래야 /api/v1/auth/reissue 가
# 저장된 값과 대조해 재발급을 내준다.
#
# 사용법:
#   ./infra/scripts-local/seed/mock-token.sh              # 사람이 읽는 형식으로 출력
#   ./infra/scripts-local/seed/mock-token.sh --export     # eval $(...) 로 셸에 넣을 형식
#   ./infra/scripts-local/seed/mock-token.sh --json       # jq 로 파싱할 형식
#   ./infra/scripts-local/seed/mock-token.sh --user-id 3  # 목 사용자 말고 특정 user_id 로 발급
set -euo pipefail

SCRIPT_NAME="mock-token"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$REPO_ROOT"

ENV_FILE="infra/env/.env.local"
MYSQL_CONTAINER="dear-jolly-mysql"

die() { echo "[${SCRIPT_NAME}] $*" >&2; exit 1; }
log() { echo "[${SCRIPT_NAME}] $*" >&2; }

# KEY=VALUE 에서 값만 추출 (인라인 주석·후행 공백 제거)
read_env() {
  grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- \
    | sed -E 's/[[:space:]]+#.*$//; s/[[:space:]]+$//'
}

# ── 1. 인자 ────────────────────────────────────────────────────
FORMAT="human"
USER_ID=""
while [ $# -gt 0 ]; do
  case "$1" in
    --export) FORMAT="export" ;;
    --json)   FORMAT="json" ;;
    --user-id) shift; USER_ID="${1:-}"; [ -n "$USER_ID" ] || die "--user-id 뒤에 값이 필요합니다." ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) die "알 수 없는 옵션: $1" ;;
  esac
  shift
done

# ── 2. 전제 확인 ───────────────────────────────────────────────
command -v openssl >/dev/null || die "openssl 이 필요합니다."
command -v docker  >/dev/null || die "docker 가 필요합니다."
[ -f "$ENV_FILE" ] || die "${ENV_FILE} 가 없습니다. ./run.sh 를 한 번 실행해 만드세요."
docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER" \
  || die "${MYSQL_CONTAINER} 가 떠 있지 않습니다. ./run.sh 를 먼저 실행하세요."

JWT_SECRET=$(read_env JWT_SECRET)
[ -n "$JWT_SECRET" ] || die "${ENV_FILE} 에 JWT_SECRET 이 없습니다."
ACCESS_EXPIRE_MS=$(read_env JWT_ACCESS_EXPIRE);   ACCESS_EXPIRE_MS="${ACCESS_EXPIRE_MS:-1800000}"
REFRESH_EXPIRE_MS=$(read_env JWT_REFRESH_EXPIRE); REFRESH_EXPIRE_MS="${REFRESH_EXPIRE_MS:-1209600000}"

MYSQL_DATABASE=$(read_env MYSQL_DATABASE)
MYSQL_USER=$(read_env MYSQL_USER)
MYSQL_PASSWORD=$(read_env MYSQL_PASSWORD)
OAUTH_PROVIDER=$(read_env MOCK_USER_OAUTH_PROVIDER); OAUTH_PROVIDER="${OAUTH_PROVIDER:-KAKAO}"
OAUTH_ID=$(read_env MOCK_USER_OAUTH_ID);             OAUTH_ID="${OAUTH_ID:-mock-user}"

# 컨테이너 안에서 실행한다. -N 은 헤더 없이, 경고는 stderr 로 흘려보낸다.
mysql_query() {
  docker exec -i "$MYSQL_CONTAINER" \
    mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N -B -e "$1" "$MYSQL_DATABASE" \
    2>/dev/null
}

sql_quote() { printf '%s' "$1" | sed "s/'/''/g"; }

# ── 3. 대상 사용자 찾기 ────────────────────────────────────────
if [ -n "$USER_ID" ]; then
  ROW=$(mysql_query "SELECT user_id, role, status FROM USERS WHERE user_id = ${USER_ID}") \
    || die "DB 조회에 실패했습니다. ${ENV_FILE} 의 MySQL 접속 정보를 확인하세요."
  [ -n "$ROW" ] || die "user_id=${USER_ID} 인 사용자가 없습니다."
else
  ROW=$(mysql_query "SELECT user_id, role, status FROM USERS
                     WHERE oauth_provider = '$(sql_quote "$OAUTH_PROVIDER")'
                       AND oauth_id = '$(sql_quote "$OAUTH_ID")'") \
    || die "DB 조회에 실패했습니다. ${ENV_FILE} 의 MySQL 접속 정보를 확인하세요."
  [ -n "$ROW" ] || die "목 사용자(${OAUTH_PROVIDER}/${OAUTH_ID})가 없습니다.
       ${ENV_FILE} 에 MOCK_USER_SEED_ENABLED=true 를 넣고 ./run.sh restart 로 시드를 돌리세요."
fi

USER_ID=$(printf '%s' "$ROW" | cut -f1)
ROLE=$(printf '%s' "$ROW" | cut -f2)
STATUS=$(printf '%s' "$ROW" | cut -f3)
[ "$STATUS" = "WITHDRAWN" ] && log "경고: 탈퇴 상태(WITHDRAWN)인 계정입니다. 인증 필터가 401 로 막습니다."

# ── 4. JWT 서명 (서버 JwtProvider 와 같은 클레임 구성) ─────────
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# jjwt 의 Keys.hmacShaKeyFor 는 시크릿 길이로 알고리즘을 고른다. 서버가 발급한 토큰과
# 헤더까지 같아지도록 여기서도 같은 기준을 쓴다.
SECRET_BYTES=$(printf '%s' "$JWT_SECRET" | wc -c | tr -d ' ')
if   [ "$SECRET_BYTES" -ge 64 ]; then ALG="HS512"; DIGEST="-sha512"
elif [ "$SECRET_BYTES" -ge 48 ]; then ALG="HS384"; DIGEST="-sha384"
elif [ "$SECRET_BYTES" -ge 32 ]; then ALG="HS256"; DIGEST="-sha256"
else die "JWT_SECRET 이 ${SECRET_BYTES}바이트입니다. HS256 최소 32바이트가 필요합니다."
fi

uuid() {
  if command -v uuidgen >/dev/null; then
    uuidgen
  else
    openssl rand -hex 16 | sed -E 's/(.{8})(.{4})(.{4})(.{4})(.{12})/\1-\2-\3-\4-\5/'
  fi
}

sign() {
  local payload="$1" header signing_input signature
  header=$(printf '{"alg":"%s"}' "$ALG" | b64url)
  signing_input="${header}.$(printf '%s' "$payload" | b64url)"
  signature=$(printf '%s' "$signing_input" \
    | openssl dgst -binary "$DIGEST" -hmac "$JWT_SECRET" | b64url)
  printf '%s.%s' "$signing_input" "$signature"
}

NOW=$(date +%s)
ACCESS_EXP=$(( NOW + ACCESS_EXPIRE_MS / 1000 ))
REFRESH_EXP=$(( NOW + REFRESH_EXPIRE_MS / 1000 ))

ACCESS_TOKEN=$(sign "{\"jti\":\"$(uuid)\",\"sub\":\"${USER_ID}\",\"role\":\"${ROLE}\",\"iat\":${NOW},\"exp\":${ACCESS_EXP}}")
REFRESH_TOKEN=$(sign "{\"jti\":\"$(uuid)\",\"sub\":\"${USER_ID}\",\"role\":\"${ROLE}\",\"iat\":${NOW},\"exp\":${REFRESH_EXP}}")

# ── 5. Refresh Token 을 DB 에 반영 (reissue 대조용) ────────────
mysql_query "UPDATE USERS SET refresh_token = '${REFRESH_TOKEN}' WHERE user_id = ${USER_ID}" >/dev/null \
  || die "USERS.refresh_token 갱신에 실패했습니다."

# ── 6. 출력 ────────────────────────────────────────────────────
case "$FORMAT" in
  export)
    echo "export ACCESS_TOKEN='${ACCESS_TOKEN}'"
    echo "export REFRESH_TOKEN='${REFRESH_TOKEN}'"
    ;;
  json)
    printf '{"userId":%s,"role":"%s","accessToken":"%s","refreshToken":"%s"}\n' \
      "$USER_ID" "$ROLE" "$ACCESS_TOKEN" "$REFRESH_TOKEN"
    ;;
  *)
    APP_PORT=$(read_env APP_PORT); APP_PORT="${APP_PORT:-8080}"
    cat <<EOF
[${SCRIPT_NAME}] userId=${USER_ID}, role=${ROLE}, status=${STATUS}, alg=${ALG}
[${SCRIPT_NAME}] Access  (만료 $(( ACCESS_EXPIRE_MS / 60000 ))분)
${ACCESS_TOKEN}

[${SCRIPT_NAME}] Refresh (만료 $(( REFRESH_EXPIRE_MS / 86400000 ))일, USERS.refresh_token 에 반영됨)
${REFRESH_TOKEN}

[${SCRIPT_NAME}] 바로 써 보기
  eval \$(./infra/scripts-local/seed/mock-token.sh --export)
  curl -k -H "Authorization: Bearer \$ACCESS_TOKEN" https://localhost:${APP_PORT}/api/v1/home
  curl -k -H "Authorization: Bearer \$ACCESS_TOKEN" https://localhost:${APP_PORT}/api/v1/letters
EOF
    ;;
esac
