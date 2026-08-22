#!/usr/bin/env bash
# infra/env/.env.prod 의 값을 GitHub Actions Secrets 로 등록한다.
# 사전 조건: infra/scripts-local/aws-setup.sh 로 인프라 구축 완료 + gh CLI 로그인 (gh auth login)
#
# infra/env/.env.prod 의 값을 세 갈래로 나눠 올린다.
#   - 워크플로가 개별 참조하는 값 → 각각 Secret (EC2_HOST, AWS_ACCESS_KEY_ID 등)
#   - 비밀이 아닌 설정값          → Variable (AWS_REGION, ECR_REPOSITORY)
#   - 앱 실행 환경변수           → PROD_ENV_FILE 하나로 묶어서
#   - 인프라 관리 전용 값        → 올리지 않음 (STACK_ECR, EC2_INSTANCE_TYPE 등)
set -euo pipefail

# shellcheck disable=SC2034  # common.sh 의 log/die 가 사용한다
SCRIPT_NAME="github-secrets"
# shellcheck source=common.sh disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

load_env

SECRETS_DUMP_FILE="infra/env/.env.githubsecrets"
VARIABLES_DUMP_FILE="infra/env/.env.githubvariables"

# 토큰 권한이 없거나 손으로 넣고 싶을 때: 등록할 값을 파일로 떨군다.
# Secret 과 Variable 은 GitHub 등록 화면의 탭이 다르므로 파일도 나눈다.
if [ "${1:-}" = "dump" ]; then
  [ -f "$KEY_PATH" ] || die "키페어 파일이 없습니다: ${KEY_PATH}"

  # ===== Secrets =====
  {
    echo "# GitHub Actions Secrets 등록용 목록 (자동 생성 — 직접 수정하지 말 것)"
    echo "# 생성 명령: ./infra/scripts-local/github-secrets.sh dump"
    echo "# 등록 위치: GitHub > Settings > Secrets and variables > Actions > [Secrets] 탭"
    echo "#"
    echo "# 값이 바뀌면 이 명령을 다시 실행해 갱신한다."
    echo "# 이 파일은 gitignore 대상이다 (실제 비밀값 포함)."
    echo "# 비밀이 아닌 설정값은 ${VARIABLES_DUMP_FILE} 에 따로 있다."
    echo
    echo "# ===== 1. 단일 행 ${#SECRET_KEYS[@]}개 (이름 = 값) ====="
    for key in "${SECRET_KEYS[@]}"; do
      printf '%s=%s\n' "$key" "$(read_env "$key")"
    done
    echo
    echo "# ===== 2. EC2_SSH_KEY (아래 BEGIN~END 줄 전체를 값으로) ====="
    echo "# 클립보드 복사 (리포지터리 루트에서 실행):"
    echo "#   cat ${KEY_PATH} | pbcopy"
    echo "# 또는 이 파일에서 바로 추출:"
    echo "#   sed -n '/^-----BEGIN/,/^-----END/p' ${SECRETS_DUMP_FILE} | pbcopy"
    cat "$KEY_PATH"
    echo
    echo "# ===== 3. PROD_ENV_FILE (아래 전체를 값으로) ====="
    echo "# 클립보드 복사 (리포지터리 루트에서 실행):"
    echo "#   bash -c 'source infra/scripts-local/common.sh && load_env && app_env_lines' | pbcopy"
    echo "# 또는 이 파일에서 바로 추출:"
    echo "#   sed -n '/^# ===== 3\./,\$p' ${SECRETS_DUMP_FILE} | grep -vE '^#' | pbcopy"
    app_env_lines
  } > "$SECRETS_DUMP_FILE"
  chmod 600 "$SECRETS_DUMP_FILE"
  log "${SECRETS_DUMP_FILE} 생성 완료 (Secret ${#SECRET_KEYS[@]}개 + EC2_SSH_KEY + PROD_ENV_FILE)"

  # ===== Variables =====
  {
    echo "# GitHub Actions Variables 등록용 목록 (자동 생성 — 직접 수정하지 말 것)"
    echo "# 생성 명령: ./infra/scripts-local/github-secrets.sh dump"
    echo "# 등록 위치: GitHub > Settings > Secrets and variables > Actions > [Variables] 탭"
    echo "#"
    echo "# 비밀이 아니다. 오히려 Secret 으로 등록하면 배포가 깨진다."
    echo "# 이미지 주소(<계정>.dkr.ecr.<리전>.amazonaws.com/<리포지터리>:<태그>)에 이 값들이 들어가는데,"
    echo "# 잡 출력에 Secret 문자열이 섞이면 GitHub 이 출력을 통째로 버려"
    echo "# needs.build.outputs.image 가 빈 값이 되고 APP_IMAGE 검사에서 실패한다."
    echo
    echo "# ===== 단일 행 ${#VARIABLE_KEYS[@]}개 (이름 = 값) ====="
    for key in "${VARIABLE_KEYS[@]}"; do
      printf '%s=%s\n' "$key" "$(read_env "$key")"
    done
  } > "$VARIABLES_DUMP_FILE"
  chmod 600 "$VARIABLES_DUMP_FILE"
  log "${VARIABLES_DUMP_FILE} 생성 완료 (Variable ${#VARIABLE_KEYS[@]}개)"
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

# ===== 1-2. 비밀이 아닌 설정값 (Variable) =====
# Secret 으로 두면 이 문자열이 섞인 잡 출력이 통째로 마스킹돼
# needs.build.outputs.image 가 빈 값이 되고 배포가 깨진다.
for key in "${VARIABLE_KEYS[@]}"; do
  value=$(read_env "$key")
  [ -n "$value" ] || die "${key} 값이 비어 있습니다."
  gh variable set "$key" --body "$value"
  log "${key} 등록 완료 (Variable)"
  # 과거에 Secret 으로 올라가 있었다면 내린다. 남아 있어도 vars 가 우선하지만 혼동을 막는다.
  gh secret delete "$key" >/dev/null 2>&1 && log "${key} 는 Secret 에서 제거했습니다"
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
echo
log "등록된 Variables 목록:"
gh variable list
