#!/usr/bin/env bash
# aws-setup.sh · aws-manage.sh · github-secrets.sh 가 함께 쓰는 공통 로직.
# 단독 실행용이 아니라 source 로 불러 쓴다.

# 호출한 스크립트가 어디서 실행되든 리포지터리 루트 기준으로 동작한다
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT" || exit 1

# shellcheck disable=SC2034  # source 하는 스크립트에서 사용한다
ENV_FILE="infra/env/.env.prod"
# shellcheck disable=SC2034
TEMPLATE_DIR="infra/cloudformation"

# GitHub Secrets 에 개별 등록되는 값 (PROD_ENV_FILE 에는 넣지 않는다)
SECRET_KEYS=(EC2_HOST EC2_USER EC2_PORT DEPLOY_PATH AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_REGION ECR_REPOSITORY)
# 로컬 인프라 관리 전용 값 (GitHub 에 올릴 필요 없음)
LOCAL_ONLY_KEYS=(EC2_SSH_KEY_PATH EC2_KEY_PAIR_NAME STACK_ECR STACK_EC2 EC2_INSTANCE_TYPE EC2_VOLUME_SIZE ALLOWED_SSH_CIDR GH_TOKEN)

die() { echo "[${SCRIPT_NAME:-infra}] $*" >&2; exit 1; }
log() { echo "[${SCRIPT_NAME:-infra}] $*"; }

require_env_file() {
  [ -f "$ENV_FILE" ] || die "${ENV_FILE} 가 없습니다. infra/env/.env.prod.example 을 복사해 채우세요."
}

require_cmd() {
  command -v "$1" >/dev/null || die "$1 가 필요합니다. ${2:-}"
}

# KEY=VALUE 에서 값만 추출 (인라인 주석·후행 공백 제거)
read_env() {
  grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- \
    | sed -E 's/[[:space:]]+#.*$//; s/[[:space:]]+$//'
}

# infra/env/.env.prod 의 값을 모두 읽어 변수로 세팅한다
# shellcheck disable=SC2034  # 전부 source 하는 스크립트에서 사용한다
load_env() {
  require_env_file

  AWS_ACCESS_KEY_ID=$(read_env AWS_ACCESS_KEY_ID)
  AWS_SECRET_ACCESS_KEY=$(read_env AWS_SECRET_ACCESS_KEY)
  AWS_REGION=$(read_env AWS_REGION)
  AWS_REGION="${AWS_REGION:-ap-northeast-2}"   # 모든 리소스는 서울 리전에 만든다
  export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_REGION
  export AWS_DEFAULT_REGION="$AWS_REGION"

  STACK_ECR=$(read_env STACK_ECR);  STACK_ECR="${STACK_ECR:-dear-jolly-ecr}"
  STACK_EC2=$(read_env STACK_EC2);  STACK_EC2="${STACK_EC2:-dear-jolly-ec2}"
  ECR_REPOSITORY=$(read_env ECR_REPOSITORY)
  KEY_PAIR_NAME=$(read_env EC2_KEY_PAIR_NAME)
  KEY_PATH=$(read_env EC2_SSH_KEY_PATH); KEY_PATH="${KEY_PATH/#\~/$HOME}"
  DEPLOY_PATH=$(read_env DEPLOY_PATH)
  GH_TOKEN=$(read_env GH_TOKEN)
  [ -n "$GH_TOKEN" ] && export GH_TOKEN   # gh CLI 가 이 값으로 인증한다
  EC2_HOST=$(read_env EC2_HOST)
  EC2_USER=$(read_env EC2_USER); EC2_USER="${EC2_USER:-ubuntu}"
  EC2_PORT=$(read_env EC2_PORT); EC2_PORT="${EC2_PORT:-22}"
  INSTANCE_TYPE=$(read_env EC2_INSTANCE_TYPE); INSTANCE_TYPE="${INSTANCE_TYPE:-t3.small}"
  VOLUME_SIZE=$(read_env EC2_VOLUME_SIZE); VOLUME_SIZE="${VOLUME_SIZE:-30}"
  ALLOWED_SSH_CIDR=$(read_env ALLOWED_SSH_CIDR); ALLOWED_SSH_CIDR="${ALLOWED_SSH_CIDR:-0.0.0.0/0}"
  APP_PORT=$(read_env APP_PORT); APP_PORT="${APP_PORT:-80}"
  MINIO_API_PORT=$(read_env MINIO_API_PORT); MINIO_API_PORT="${MINIO_API_PORT:-9000}"
}

# 지정한 키들에 CHANGE_ME 가 남아 있으면 중단한다
require_filled() {
  local missing=()
  for key in "$@"; do
    [ "$(read_env "$key")" = "CHANGE_ME" ] && missing+=("$key")
  done
  [ ${#missing[@]} -eq 0 ] || die "${ENV_FILE} 에 채워야 할 값이 있습니다: ${missing[*]}"
}

stack_output() {
  aws cloudformation describe-stacks --stack-name "$1" \
    --query "Stacks[0].Outputs[?OutputKey=='$2'].OutputValue" --output text 2>/dev/null
}

# infra/env/.env.prod 의 KEY 값을 교체한다 (인라인 주석은 보존)
set_env() {
  local key="$1" value="$2"
  grep -qE "^${key}=" "$ENV_FILE" || return 0
  python3 - "$ENV_FILE" "$key" "$value" <<'PY'
import re, sys
path, key, value = sys.argv[1], sys.argv[2], sys.argv[3]
out = []
for line in open(path):
    m = re.match(rf'^({re.escape(key)}=)([^#\n]*)(#.*)?$', line)
    if m:
        comment = m.group(3) or ''
        out.append(f'{m.group(1)}{value}   {comment}\n' if comment else f'{m.group(1)}{value}\n')
    else:
        out.append(line)
open(path, 'w').writelines(out)
PY
  log "${ENV_FILE} 갱신: ${key}=${value}"
}

# 서버 .env 로 들어갈 앱 실행 환경변수만 출력 (배포·인프라 설정 제외).
# 서버에서 deploy.sh 가 이 파일을 source 하므로, 인라인 주석을 떼고
# 공백이 있는 값은 따옴표로 감싼다 (예: BACKUP_SCHEDULE=30 3 * * *).
app_env_lines() {
  local exclude
  exclude=$(printf '%s\n' "${SECRET_KEYS[@]}" "${LOCAL_ONLY_KEYS[@]}" | paste -sd '|' -)
  grep -vE '^[[:space:]]*(#|$)' "$ENV_FILE" \
    | grep -vE "^(${exclude})=" \
    | sed -E 's/[[:space:]]+#.*$//; s/[[:space:]]+$//' \
    | awk -F= '{
        key = $1
        sub(/^[^=]*=/, "", $0)
        if ($0 ~ /[[:space:]]/ && $0 !~ /^".*"$/) printf "%s=\"%s\"\n", key, $0
        else printf "%s=%s\n", key, $0
      }'
}

# ===== 원격 실행 헬퍼 =====
ssh_run() {
  ssh -i "$KEY_PATH" -p "$EC2_PORT" -o StrictHostKeyChecking=accept-new \
    "${EC2_USER}@${EC2_HOST}" "$@"
}

# 대화형 셸로 서버 접속
ssh_shell() {
  ssh -i "$KEY_PATH" -p "$EC2_PORT" -o StrictHostKeyChecking=accept-new -t \
    "${EC2_USER}@${EC2_HOST}"
}

scp_to() {
  scp -i "$KEY_PATH" -P "$EC2_PORT" -o StrictHostKeyChecking=accept-new \
    "$1" "${EC2_USER}@${EC2_HOST}:$2"
}

require_remote() {
  [ -n "$EC2_HOST" ] && [ "$EC2_HOST" != "CHANGE_ME" ] || die "EC2_HOST 가 비어 있습니다. infra/scripts-local/aws-setup.sh 를 먼저 실행하세요."
  [ -f "$KEY_PATH" ] || die "키페어 파일이 없습니다: ${KEY_PATH} (infra/scripts-local/aws-setup.sh 실행 필요)"
}
