#!/usr/bin/env bash
# infra/env/.env.prod 의 값을 GitHub Actions Secrets 로 등록한다.
# 사전 조건: infra/scripts-local/aws-setup.sh 로 인프라 구축 완료 + gh CLI 로그인 (gh auth login)
#
# infra/env/.env.prod 의 값을 세 갈래로 나눠 올린다.
#   - 워크플로가 개별 참조하는 값 → 각각 Secret (EC2_HOST, AWS_ACCESS_KEY_ID 등)
#   - 앱 실행 환경변수           → PROD_ENV_FILE 하나로 묶어서
#   - 인프라 관리 전용 값        → 올리지 않음 (STACK_ECR, EC2_INSTANCE_TYPE 등)
set -euo pipefail

# shellcheck disable=SC2034  # common.sh 의 log/die 가 사용한다
SCRIPT_NAME="github-secrets"
# shellcheck source=common.sh disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

load_env

DUMP_FILE="infra/env/.env.githubsecrets"

# 토큰 권한이 없거나 손으로 넣고 싶을 때: 등록할 값 10개를 파일로 떨군다
if [ "${1:-}" = "dump" ]; then
  [ -f "$KEY_PATH" ] || die "키페어 파일이 없습니다: ${KEY_PATH}"
  {
    echo "# GitHub Actions Secrets 등록용 목록 (자동 생성 — 직접 수정하지 말 것)"
    echo "# 생성 명령: ./infra/scripts-local/github-secrets.sh dump"
    echo "# 등록 위치: GitHub > Settings > Secrets and variables > Actions > New repository secret"
    echo "#"
    echo "# 아래 10개를 각각 Secret 으로 등록한다. 값이 바뀌면 이 명령을 다시 실행해 갱신한다."
    echo "# 이 파일은 gitignore 대상이다 (실제 비밀값 포함)."
    echo
    echo "# ===== 1. 단일 행 8개 (이름 = 값) ====="
    for key in "${SECRET_KEYS[@]}"; do
      printf '%s=%s\n' "$key" "$(read_env "$key")"
    done
    echo
    echo "# ===== 2. EC2_SSH_KEY (아래 BEGIN~END 줄 전체를 값으로) ====="
    echo "# 클립보드 복사 (리포지터리 루트에서 실행):"
    echo "#   cat ${KEY_PATH} | pbcopy"
    echo "# 또는 이 파일에서 바로 추출:"
    echo "#   sed -n '/^-----BEGIN/,/^-----END/p' ${DUMP_FILE} | pbcopy"
    cat "$KEY_PATH"
    echo
    echo "# ===== 3. PROD_ENV_FILE (아래 전체를 값으로) ====="
    echo "# 클립보드 복사 (리포지터리 루트에서 실행):"
    echo "#   bash -c 'source infra/scripts-local/common.sh && load_env && app_env_lines' | pbcopy"
    echo "# 또는 이 파일에서 바로 추출:"
    echo "#   sed -n '/^# ===== 3\./,\$p' ${DUMP_FILE} | grep -vE '^#' | pbcopy"
    app_env_lines
  } > "$DUMP_FILE"
  chmod 600 "$DUMP_FILE"
  log "${DUMP_FILE} 생성 완료 (Secret ${#SECRET_KEYS[@]}개 + EC2_SSH_KEY + PROD_ENV_FILE)"
  exit 0
fi

require_cmd gh "https://cli.github.com"

# GH_TOKEN 이 있으면 그것으로, 없으면 gh 로그인 세션으로 동작한다
if [ -n "${GH_TOKEN:-}" ]; then
  log "GH_TOKEN 으로 인증합니다"
elif ! gh auth status >/dev/null 2>&1; then
  die "GitHub 인증이 없습니다. .env.prod 에 GH_TOKEN 을 넣거나 gh auth login 을 실행하세요."
fi

if grep -q "CHANGE_ME" "$ENV_FILE"; then
  echo "[sync] ${ENV_FILE} 에 CHANGE_ME 가 남아 있습니다. 실제 값으로 채운 뒤 다시 실행하세요." >&2
  grep -n "CHANGE_ME" "$ENV_FILE" >&2
  exit 1
fi

# ===== 1. EC2 접속 정보 + AWS 자격증명 =====
for key in "${SECRET_KEYS[@]}"; do
  value=$(read_env "$key")
  [ -n "$value" ] || die "${key} 값이 비어 있습니다."
  gh secret set "$key" --body "$value"
  log "${key} 등록 완료"
done

# ===== 2. 키페어(.pem) 파일 내용 =====
# 키페어 생성/저장은 infra/scripts-local/aws-setup.sh 담당. 여기서는 이미 있는 파일을 읽기만 한다.
[ -f "$KEY_PATH" ] || die "키페어 파일이 없습니다: ${KEY_PATH} (infra/scripts-local/aws-setup.sh 실행 필요)"
gh secret set EC2_SSH_KEY < "$KEY_PATH"
log "EC2_SSH_KEY 등록 완료 (${KEY_PATH})"

# ===== 3. 앱 실행 환경변수 (배포·인프라 설정 제외 전체) =====
app_env_lines | gh secret set PROD_ENV_FILE
log "PROD_ENV_FILE 등록 완료 (앱 실행 환경변수 일괄)"

echo
log "등록된 Secrets 목록:"
gh secret list
