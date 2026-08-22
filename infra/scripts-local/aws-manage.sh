#!/usr/bin/env bash
# 초기 구축(infra/scripts-local/aws-setup.sh) 이후의 배포·운영 관리 스크립트.
# 키페어 생성/저장은 aws-setup.sh 에만 있다. 여기서는 있는 키를 쓰기만 한다.
#
# ===== 실행 전 필요한 것 =====
#
#   - infra/scripts-local/aws-setup.sh 완료 (스택 생성 + infra/env/.env.prod 의 EC2_HOST 채워짐 + .pem 파일 존재)
#   - AWS CLI (이미지 빌드는 GitHub Actions CI 가 담당하므로 로컬 docker 는 필요 없다)
#   - infra/env/.env.prod 의 AWS 액세스 키 — aws-setup.sh 와 동일한 IAM 사용자면 충분하다.
#     명령별로 실제 필요한 권한은 아래와 같다.
#       deploy·images          : ECR 읽기, CloudFormation Describe
#       status·outputs         : CloudFormation Describe
#       update-ecr·update-ec2  : aws-setup.sh 와 동일 (CFN·EC2·IAM·SSM)
#       down-ec2·down-ecr      : CloudFormation DeleteStack + 각 리소스 삭제 권한
#   - EC2 접속용 SSH 키(.pem)는 infra/env/.env.prod 의 EC2_SSH_KEY_PATH 경로에 있어야 한다.
#
# 사용법: ./infra/scripts-local/aws-manage.sh <명령>
#   deploy [태그]  ECR 의 이미지를 EC2 에 블루그린 배포 (태그 생략 시 latest)
#   images       ECR 에 올라온 이미지 태그 목록
#   files        docker/compose.prod.yaml · scripts-ec2/deploy.sh · Caddyfile.template 재전송
#   restart      서버의 현재 이미지로 블루그린 재배포
#   sync         infra/env/.env.prod 기준으로 두 스택 재적용 (보안그룹·포트·SSH 대역 반영)
#   swap         EC2_SWAP_SIZE 값을 실행 중인 인스턴스에 적용 (재생성 없이)
#   amis         인스턴스 타입 변경 전에 만들어 둔 백업 AMI 목록
#   drift        콘솔에서 손댄 리소스가 있는지 CloudFormation 드리프트 탐지
#   sg           현재 적용된 보안그룹 인바운드 규칙 확인
#   backup       서버에서 백업 즉시 1회 실행
#   backups      서버에 보관 중인 백업 목록
#   status       스택 상태 + 서버 컨테이너 상태
#   outputs      CloudFormation 출력값
#   logs         서버 앱 로그 확인 (Ctrl+C 로 종료)
#   ssh          서버 접속
#   update-ecr   ECR 스택 갱신 (템플릿 수정 반영)
#   update-ec2   EC2 스택 갱신 (템플릿·인스턴스 타입 등 변경 반영)
#   down-ec2     EC2 스택 삭제
#   down-ecr     ECR 스택 삭제 (리포지터리는 Retain 으로 보존)
set -euo pipefail

# shellcheck disable=SC2034  # common.sh 의 log/die 가 사용한다
SCRIPT_NAME="aws-manage"
# shellcheck source=common.sh disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

require_cmd aws "https://aws.amazon.com/cli/"
load_env
SITE_ADDRESS=$(read_env SITE_ADDRESS)

usage() {
  cat <<'USAGE'
사용법: ./infra/scripts-local/aws-manage.sh <명령>

  deploy [태그]  ECR 의 이미지를 EC2 에 블루그린 배포 (태그 생략 시 latest)
  images       ECR 에 올라온 이미지 태그 목록
  files        docker/compose.prod.yaml · scripts-ec2/deploy.sh · Caddyfile.template 재전송
  restart      서버의 현재 이미지로 블루그린 재배포
  sync         infra/env/.env.prod 기준으로 두 스택 재적용 (보안그룹·포트·SSH 대역 반영)
  swap         EC2_SWAP_SIZE 값을 실행 중인 인스턴스에 적용 (재생성 없이)
  amis         인스턴스 타입 변경 전에 만들어 둔 백업 AMI 목록
  drift        콘솔에서 손댄 리소스가 있는지 CloudFormation 드리프트 탐지
  sg           현재 적용된 보안그룹 인바운드 규칙 확인
  backup       서버에서 백업 즉시 1회 실행
  backups      서버에 보관 중인 백업 목록
  status       스택 상태 + 서버 컨테이너 상태
  outputs      CloudFormation 출력값
  logs         서버 앱 로그 확인 (Ctrl+C 로 종료)
  ssh          서버 접속
  update-ecr   ECR 스택 갱신 (템플릿 수정 반영)
  update-ec2   EC2 스택 갱신 (템플릿·인스턴스 타입 등 변경 반영)
  down-ec2     EC2 스택 삭제
  down-ecr     ECR 스택 삭제 (리포지터리는 Retain 으로 보존)

초기 구축(스택·키페어 생성)은 ./infra/scripts-local/aws-setup.sh 를 쓴다.
USAGE
}

registry_uri() {
  local uri
  uri=$(stack_output "$STACK_ECR" RepositoryUri)
  [ -n "$uri" ] && [ "$uri" != "None" ] || die "ECR 스택이 없습니다. infra/scripts-local/aws-setup.sh 를 먼저 실행하세요."
  echo "$uri"
}

# 이미지 빌드는 GitHub Actions CI 에서만 한다 (로컬·EC2 에서 빌드하지 않는다).
# 여기서는 CI 가 ECR 에 올려둔 이미지를 골라 배포만 한다.
cmd_images() {
  aws ecr describe-images --repository-name "$ECR_REPOSITORY" \
    --query 'sort_by(imageDetails,&imagePushedAt)[*].[imageTags[0],imagePushedAt,imageSizeInBytes]' \
    --output table 2>/dev/null || die "ECR 리포지터리를 읽지 못했습니다: ${ECR_REPOSITORY}"
}

cmd_files() {
  require_remote
  log "배포 파일 전송"
  ssh_run "mkdir -p '${DEPLOY_PATH}'"
  scp_to infra/docker/compose.prod.yaml "${DEPLOY_PATH}/compose.yaml"
  scp_to infra/scripts-ec2/deploy.sh "${DEPLOY_PATH}/deploy.sh"
  scp_to infra/scripts-ec2/backup.sh "${DEPLOY_PATH}/backup.sh"
  scp_to infra/caddy/Caddyfile.prod.template "${DEPLOY_PATH}/Caddyfile.template"
}

# 서버에 .env 와 .deploy_env 를 만들고 블루그린 배포를 실행한다
remote_deploy() {
  local image="$1" uri="${2:-}"
  require_remote

  log "서버 환경변수 파일 생성"
  { app_env_lines; printf 'APP_IMAGE=%s\n' "$image"; } \
    | ssh_run "cat > '${DEPLOY_PATH}/.env' && chmod 600 '${DEPLOY_PATH}/.env'"

  # ECR 인증은 EC2 인스턴스 역할(AmazonEC2ContainerRegistryReadOnly)이 처리한다.
  # 토큰을 넘기지 않으므로 자격증명이 서버로 오갈 일이 없다.
  {
    printf 'APP_IMAGE=%s\n' "$image"
    printf 'ECR_REGISTRY=%s\n' "${uri%%/*}"
    printf 'AWS_REGION=%s\n' "$AWS_REGION"
  } | ssh_run "cat > '${DEPLOY_PATH}/.deploy_env' && chmod 600 '${DEPLOY_PATH}/.deploy_env'"

  log "블루그린 배포 실행"
  ssh_run "DEPLOY_PATH='${DEPLOY_PATH}' bash -s" <<'REMOTE'
set -euo pipefail
cd "$DEPLOY_PATH"
trap 'rm -f "$DEPLOY_PATH/.deploy_env"' EXIT
set -a
. ./.deploy_env
set +a
chmod +x deploy.sh
./deploy.sh
REMOTE
}

cmd_deploy() {
  local uri tag image
  uri=$(registry_uri)
  tag="${1:-latest}"
  image="${uri}:${tag}"

  # CI 가 올린 이미지인지 먼저 확인한다. 없는 이미지를 배포로 넘기지 않는다.
  aws ecr describe-images --repository-name "$ECR_REPOSITORY" --image-ids "imageTag=${tag}" >/dev/null 2>&1 \
    || die "ECR 에 '${tag}' 태그가 없습니다. main 에 push 해 CI 빌드를 돌리거나, 'images' 로 태그를 확인하세요."

  log "배포할 이미지: ${image}"
  cmd_files
  remote_deploy "$image" "$uri"
  log "배포 완료: https://${SITE_ADDRESS:-$EC2_HOST}"
}

cmd_restart() {
  require_remote
  local image
  image=$(ssh_run "grep -E '^APP_IMAGE=' '${DEPLOY_PATH}/.env' | cut -d= -f2-" || true)
  [ -n "$image" ] || die "서버에 배포된 이미지가 없습니다. 먼저 deploy 를 실행하세요."
  log "현재 이미지로 재배포: ${image}"
  cmd_files
  remote_deploy "$image" "$(registry_uri)"
}

# 보안그룹·포트·SSH 허용 대역 등은 전부 ec2.yaml 에 선언돼 있다.
# 콘솔에서 손대지 말고 infra/env/.env.prod 를 고친 뒤 이 명령으로 반영한다.
update_ecr_stack() {
  aws cloudformation deploy --stack-name "$STACK_ECR" \
    --template-file "${TEMPLATE_DIR}/ecr.yaml" \
    --parameter-overrides "RepositoryName=${ECR_REPOSITORY}" \
    --no-fail-on-empty-changeset
}

# 인스턴스 타입을 바꾸면 CloudFormation 이 인스턴스를 정지·교체하므로,
# 그 전에 반드시 AMI 스냅샷을 남긴다. 데이터 유실 시 이 AMI 로 되돌릴 수 있다.
backup_ami_if_type_changes() {
  local current_type desired_type instance_id ami_id
  current_type=$(aws cloudformation describe-stacks --stack-name "$STACK_EC2" \
    --query "Stacks[0].Parameters[?ParameterKey=='InstanceType'].ParameterValue" --output text 2>/dev/null)
  desired_type="$INSTANCE_TYPE"

  [ -n "$current_type" ] && [ "$current_type" != "None" ] || return 0   # 스택이 없으면 백업할 것도 없다
  [ "$current_type" != "$desired_type" ] || return 0                    # 타입이 그대로면 통과

  log "인스턴스 타입 변경 감지: ${current_type} → ${desired_type}"
  instance_id=$(aws cloudformation describe-stacks --stack-name "$STACK_EC2" \
    --query "Stacks[0].Outputs[?OutputKey=='InstanceId'].OutputValue" --output text)
  [ -n "$instance_id" ] && [ "$instance_id" != "None" ] || return 0

  # 1) 애플리케이션 데이터 백업 (컨테이너가 떠 있을 때만)
  if ssh_run "test -x '${DEPLOY_PATH}/backup.sh' && docker ps -q | grep -q ." 2>/dev/null; then
    log "타입 변경 전 데이터 백업 실행"
    ssh_run "cd '${DEPLOY_PATH}' && ./backup.sh" || die "백업이 실패했습니다. 타입 변경을 중단합니다."
  else
    log "실행 중인 컨테이너가 없어 데이터 백업은 건너뛴다"
  fi

  # 2) 인스턴스 전체 AMI 스냅샷
  log "AMI 스냅샷 생성 중 (${instance_id})"
  ami_id=$(aws ec2 create-image --instance-id "$instance_id" \
    --name "dear-jolly-pre-resize-$(aws ec2 describe-instances --instance-ids "$instance_id" --query 'Reservations[0].Instances[0].LaunchTime' --output text | tr -d ':-' | cut -c1-15)-${current_type}" \
    --description "Backup before instance type change ${current_type} to ${desired_type}" \
    --no-reboot --tag-specifications 'ResourceType=image,Tags=[{Key=Project,Value=dear-jolly},{Key=Purpose,Value=pre-resize-backup}]' \
    --query ImageId --output text) || die "AMI 생성에 실패했습니다. 타입 변경을 중단합니다."
  log "AMI 생성 요청 완료: ${ami_id} (백그라운드에서 완성된다)"

  # available 까지 잠깐 기다린다 (최대 5분). 실패해도 요청 자체는 남는다
  for _ in $(seq 1 30); do
    [ "$(aws ec2 describe-images --image-ids "$ami_id" --query 'Images[0].State' --output text 2>/dev/null)" = "available" ] && {
      log "AMI 사용 가능: ${ami_id}"; break; }
    sleep 10
  done
}

update_ec2_stack() {
  backup_ami_if_type_changes
  aws cloudformation deploy --stack-name "$STACK_EC2" \
    --template-file "${TEMPLATE_DIR}/ec2.yaml" \
    --capabilities CAPABILITY_IAM \
    --parameter-overrides \
      "KeyPairName=${KEY_PAIR_NAME}" \
      "InstanceType=${INSTANCE_TYPE}" \
      "VolumeSize=${VOLUME_SIZE}" \
      "SwapSizeGb=${SWAP_SIZE}" \
      "AllowedSshCidr=${ALLOWED_SSH_CIDR}" \
      "AppPort=${APP_PORT}" \
      "MinioApiPort=${MINIO_API_PORT}" \
      "DeployPath=${DEPLOY_PATH}" \
    --no-fail-on-empty-changeset
}

cmd_sync() {
  log "ECR 스택 재적용"
  update_ecr_stack
  log "EC2 스택 재적용 (보안그룹: SSH ${ALLOWED_SSH_CIDR} · 앱 ${APP_PORT} · MinIO ${MINIO_API_PORT})"
  update_ec2_stack
  log "동기화 완료. 현재 보안그룹 규칙:"
  cmd_sg
}

# 실제 적용된 보안그룹 인바운드 규칙을 보여준다
cmd_sg() {
  local sg_id
  sg_id=$(aws cloudformation describe-stack-resources --stack-name "$STACK_EC2" \
    --logical-resource-id SecurityGroup --query 'StackResources[0].PhysicalResourceId' --output text 2>/dev/null)
  [ -n "$sg_id" ] && [ "$sg_id" != "None" ] || { echo "(보안그룹 없음)"; return; }
  aws ec2 describe-security-rules --filters "Name=group-id,Values=${sg_id}" \
    --query 'SecurityGroupRules[?!IsEgress].[IpProtocol,FromPort,ToPort,CidrIpv4,Description]' \
    --output table 2>/dev/null \
    || aws ec2 describe-security-groups --group-ids "$sg_id" \
         --query 'SecurityGroups[0].IpPermissions[].[IpProtocol,FromPort,ToPort,IpRanges[0].CidrIp]' --output table
}

# 콘솔에서 수동으로 바꾼 리소스가 있는지 확인한다
cmd_drift() {
  local id status
  log "드리프트 탐지 시작 (${STACK_EC2})"
  id=$(aws cloudformation detect-stack-drift --stack-name "$STACK_EC2" --query StackDriftDetectionId --output text)
  for _ in $(seq 1 30); do
    status=$(aws cloudformation describe-stack-drift-detection-status --stack-drift-detection-id "$id" \
      --query DetectionStatus --output text)
    [ "$status" = "DETECTION_IN_PROGRESS" ] || break
    sleep 3
  done
  aws cloudformation describe-stack-resource-drifts --stack-name "$STACK_EC2" \
    --stack-resource-drift-status-filters MODIFIED DELETED \
    --query 'StackResourceDrifts[].[LogicalResourceId,ResourceType,StackResourceDriftStatus]' --output table
  log "MODIFIED/DELETED 가 보이면 'aws-manage.sh sync' 로 템플릿 기준으로 되돌린다."
}

# user-data 는 최초 부팅에만 돌기 때문에, 이미 떠 있는 인스턴스의 스왑은 여기서 맞춘다
cmd_swap() {
  require_remote
  log "스왑 ${SWAP_SIZE}GB 적용"
  ssh_run "SWAP_GB='${SWAP_SIZE}' bash -s" <<'REMOTE'
set -euo pipefail
CURRENT=$(free -g | awk '/Swap:/{print $2}')
if [ "$CURRENT" = "$SWAP_GB" ]; then
  echo "  이미 ${SWAP_GB}GB 입니다"
  exit 0
fi
sudo swapoff /swapfile 2>/dev/null || true
sudo rm -f /swapfile
sudo fallocate -l "${SWAP_GB}G" /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile >/dev/null
sudo swapon /swapfile
grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
sudo sysctl -w vm.swappiness=20 >/dev/null
free -h | awk '/Swap:/{print "  적용 결과: " $2}'
REMOTE
}

cmd_amis() {
  aws ec2 describe-images --owners self \
    --filters "Name=tag:Purpose,Values=pre-resize-backup" \
    --query 'sort_by(Images,&CreationDate)[*].[ImageId,Name,State,CreationDate]' --output table
}

cmd_backup() {
  require_remote
  log "서버에서 백업 실행"
  ssh_run "cd '${DEPLOY_PATH}' && ./backup.sh"
}

cmd_backups() {
  require_remote
  ssh_run "ls -lh '${DEPLOY_PATH}'/../backup/mysql '${DEPLOY_PATH}'/../backup/minio 2>/dev/null || ls -lhR \$HOME/backup 2>/dev/null || echo '(백업 없음)'"
}

cmd_status() {
  echo "===== CloudFormation ====="
  aws cloudformation describe-stacks \
    --query "Stacks[?StackName=='${STACK_ECR}'||StackName=='${STACK_EC2}'].[StackName,StackStatus]" \
    --output table
  echo "===== 서버 컨테이너 ====="
  if [ -n "$EC2_HOST" ] && [ "$EC2_HOST" != "CHANGE_ME" ] && [ -f "$KEY_PATH" ]; then
    ssh_run "cd '${DEPLOY_PATH}' 2>/dev/null && docker compose ps || echo '(배포 이력 없음)'"
    ssh_run "cat '${DEPLOY_PATH}/.deploy_color' 2>/dev/null | sed 's/^/활성 색상: /'" || true
  else
    echo "(EC2 정보 없음 — infra/scripts-local/aws-setup.sh 필요)"
  fi
}

cmd_outputs() {
  for stack in "$STACK_ECR" "$STACK_EC2"; do
    echo "===== ${stack} ====="
    aws cloudformation describe-stacks --stack-name "$stack" \
      --query 'Stacks[0].Outputs[].[OutputKey,OutputValue]' --output table 2>/dev/null \
      || echo "(스택 없음)"
  done
}

case "${1:-}" in
  deploy)     shift; cmd_deploy "$@" ;;
  images)     cmd_images ;;
  files)      cmd_files ;;
  restart)    cmd_restart ;;
  sync)       cmd_sync ;;
  swap)       cmd_swap ;;
  amis)       cmd_amis ;;
  drift)      cmd_drift ;;
  sg)         cmd_sg ;;
  backup)     cmd_backup ;;
  backups)    cmd_backups ;;
  status)     cmd_status ;;
  outputs)    cmd_outputs ;;
  logs)       require_remote; ssh_run -t "cd '${DEPLOY_PATH}' && docker compose logs -f --tail 100" ;;
  ssh)        require_remote; ssh_shell ;;
  update-ecr) update_ecr_stack ;;
  update-ec2) update_ec2_stack ;;
  down-ec2)   aws cloudformation delete-stack --stack-name "$STACK_EC2" && log "${STACK_EC2} 삭제 요청 완료" ;;
  down-ecr)   aws cloudformation delete-stack --stack-name "$STACK_ECR" && log "${STACK_ECR} 삭제 요청 완료" ;;
  *)
    usage
    exit 1
    ;;
esac
