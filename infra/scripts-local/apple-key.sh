#!/usr/bin/env bash
# Apple 로그인 키(.p8)를 infra/env/.env.prod 에 반영한다.
#
# Key ID 는 파일명(AuthKey_<KeyID>.p8)에 들어 있고, 개인키는 여러 줄 PEM 이라
# .env 에 그대로 넣을 수 없다. 손으로 옮기다 보면 개행이 섞이거나 Key ID 를
# 잘못 적기 쉬워서 스크립트로 처리한다.
#
# 사용법:
#   ./infra/scripts-local/apple-key.sh                       # ~/Downloads 에서 가장 최근 .p8 자동 탐색
#   ./infra/scripts-local/apple-key.sh ~/keys/AuthKey_XXX.p8 # 경로 직접 지정
set -euo pipefail

SCRIPT_NAME="apple-key"
# shellcheck source=./common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

require_cmd openssl
require_env_file

# ── 1. .p8 찾기 ────────────────────────────────────────────────
KEY_FILE="${1:-}"
if [ -z "$KEY_FILE" ]; then
  KEY_FILE=$(ls -1t "$HOME"/Downloads/AuthKey_*.p8 2>/dev/null | head -1 || true)
  [ -n "$KEY_FILE" ] || die "~/Downloads 에서 AuthKey_*.p8 를 찾지 못했습니다. 경로를 인자로 넘기세요."
  log "자동 탐색: ${KEY_FILE}"
fi
[ -f "$KEY_FILE" ] || die "파일이 없습니다: ${KEY_FILE}"

# ── 2. 파일명에서 Key ID 추출 ──────────────────────────────────
BASE=$(basename "$KEY_FILE")
if [[ "$BASE" =~ ^AuthKey_([A-Z0-9]{10})\.p8$ ]]; then
  KEY_ID="${BASH_REMATCH[1]}"
else
  die "파일명이 AuthKey_<10자 KeyID>.p8 형식이 아닙니다: ${BASE}"
fi

# ── 3. 유효한 EC P-256 키인지 검증 (Apple 은 ES256 서명을 요구한다) ──
CURVE=$(openssl pkey -in "$KEY_FILE" -noout -text 2>/dev/null | grep -o 'NIST CURVE: .*' | cut -d' ' -f3 || true)
[ "$CURVE" = "P-256" ] || die "P-256 개인키가 아닙니다 (검출: ${CURVE:-파싱 실패}). Sign in with Apple 키가 맞는지 확인하세요."

# ── 4. 한 줄로 만들어 .env.prod 에 반영 ────────────────────────
# BEGIN/END 헤더에는 공백이 들어 있어 .env 파서에 따라 값이 잘릴 수 있다.
# 서버가 어차피 헤더와 공백을 제거하므로(AppleOauthClient.createClientSecret)
# 처음부터 base64 본문만 저장한다.
ONE_LINE=$(grep -v -- '-----' "$KEY_FILE" | tr -d '\n\r[:space:]')
[ -n "$ONE_LINE" ] || die "키 본문을 읽지 못했습니다: ${KEY_FILE}"

upsert_env() {
  local key="$1" value="$2"
  if grep -qE "^${key}=" "$ENV_FILE"; then
    # 값에 / 가 들어가므로 구분자를 | 로 쓰고, & 는 이스케이프한다
    local escaped=${value//&/\\&}
    sed -i.bak -E "s|^${key}=.*|${key}=${escaped}|" "$ENV_FILE" && rm -f "${ENV_FILE}.bak"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

upsert_env APPLE_KEY_ID "$KEY_ID"
upsert_env APPLE_PRIVATE_KEY "$ONE_LINE"

log "APPLE_KEY_ID      = ${KEY_ID}"
log "APPLE_PRIVATE_KEY = ${ONE_LINE:0:20}…(${#ONE_LINE}자)"
log "${ENV_FILE} 반영 완료"

# ── 5. 남은 값 안내 ────────────────────────────────────────────
MISSING=()
for k in APPLE_CLIENT_ID APPLE_TEAM_ID APPLE_REDIRECT_URI; do
  [ -n "$(read_env "$k")" ] || MISSING+=("$k")
done
if [ ${#MISSING[@]} -gt 0 ]; then
  log "아직 비어 있는 값: ${MISSING[*]} (docs/애플회원가입.md 참고)"
else
  log "애플 로그인 설정값이 모두 채워졌습니다."
fi

log "키 파일은 안전한 곳에 보관하세요. .p8 은 재다운로드가 불가능합니다."
