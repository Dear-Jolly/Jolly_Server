#!/usr/bin/env bash
# 빈 AWS 계정에서 처음 한 번 실행하는 AWS 인프라 구축 스크립트.
#
#   1. ECR 리포지터리 스택 생성
#   2. EC2 스택 생성 (VPC·서브넷·IGW·보안그룹·IAM·키페어·탄력적 IP·EC2)
#   3. 키페어(.pem) 생성 결과를 SSM 에서 내려받아 로컬에 저장  ← 여기서만 만든다
#   4. 스택 출력값을 .env.prod 에 반영 (EC2_HOST · MINIO_PUBLIC_ENDPOINT)
#   5. EC2 초기 설정(Docker 설치) 완료 대기
#
# 구축이 끝난 뒤의 배포·운영 관리는 infra/scripts-local/aws-manage.sh 를 쓴다.
#
# ===== 사전 준비 =====
#
#   1. AWS CLI 설치 (https://aws.amazon.com/cli/), python3
#   2. IAM 사용자 생성 후 액세스 키 발급 → infra/env/.env.prod 의
#      AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_REGION 에 기입
#      (이 스크립트는 aws configure 프로필이 아니라 .env.prod 값을 쓴다)
#   3. infra/env/.env.prod 의 CHANGE_ME 를 전부 실제 값으로 채울 것
#      (DB·MinIO 비밀번호까지. 비어 있으면 스크립트가 중단한다)
#
# ===== IAM 사용자에게 필요한 권한 =====
#
# 이 스크립트가 실제로 호출하는 API 기준이다. EC2·ECR 권한만으로는 부족하고,
# 스택을 만드는 CloudFormation, EC2 인스턴스 역할을 만드는 IAM,
# 키페어 개인키를 읽는 SSM 권한이 함께 필요하다.
#
#   | 서비스         | 쓰는 곳                                      | 필요한 동작                                    |
#   |----------------|----------------------------------------------|------------------------------------------------|
#   | CloudFormation | 두 스택 생성·갱신                            | CreateStack/UpdateStack, ChangeSet, DescribeStacks |
#   | ECR            | 리포지터리 + 라이프사이클 정책               | CreateRepository, PutLifecyclePolicy, Describe |
#   | EC2            | VPC·서브넷·IGW·라우팅·보안그룹·키페어·EIP·인스턴스 | Create/Describe/Delete 전반, Tag               |
#   | IAM            | EC2 인스턴스 역할·인스턴스 프로파일 생성     | CreateRole, AttachRolePolicy, PassRole,        |
#   |                | (deploy 시 --capabilities CAPABILITY_IAM)    | CreateInstanceProfile, AddRoleToInstanceProfile|
#   | SSM            | 키페어 개인키 조회, Ubuntu AMI ID 조회       | GetParameter(s)                                |
#   | STS            | 자격증명 확인                                | GetCallerIdentity                              |
#
# 관리형 정책으로 빠르게 맞춘다면 아래 조합이면 된다 (개발 계정 기준).
#   AWSCloudFormationFullAccess
#   AmazonEC2FullAccess
#   AmazonEC2ContainerRegistryFullAccess
#   AmazonSSMReadOnlyAccess
#   IAMFullAccess            ← 역할 생성이 필요해서. 부담되면 iam:*Role*, iam:*InstanceProfile*,
#                              iam:PassRole 만 허용하는 커스텀 정책으로 좁힐 것
#
# 배포용(GitHub Actions)에는 ECR push 권한만 있으면 되므로,
# 운영을 분리하려면 이 구축용 키와 별도의 IAM 사용자를 쓰는 편이 안전하다.
set -euo pipefail

# shellcheck disable=SC2034  # common.sh 의 log/die 가 사용한다
SCRIPT_NAME="aws-setup"
# shellcheck source=common.sh disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

require_cmd aws "https://aws.amazon.com/cli/"
require_cmd python3
load_env
require_filled AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY MYSQL_PASSWORD MYSQL_ROOT_PASSWORD MINIO_ROOT_PASSWORD

# ===== 0. 자격증명 확인 =====
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text) \
  || die "AWS 자격증명이 유효하지 않습니다. infra/env/.env.prod 의 AWS_ACCESS_KEY_ID/SECRET 을 확인하세요."
REGION_LABEL=$([ "$AWS_REGION" = "ap-northeast-2" ] && echo "서울" || echo "$AWS_REGION")
log "AWS 계정 ${ACCOUNT_ID} / ${REGION_LABEL} 리전(${AWS_REGION}) 에 구축합니다"

# ===== 1. ECR 스택 =====
log "[1/5] ECR 스택 생성: ${STACK_ECR}"
aws cloudformation deploy \
  --stack-name "$STACK_ECR" \
  --template-file "${TEMPLATE_DIR}/ecr.yaml" \
  --parameter-overrides "RepositoryName=${ECR_REPOSITORY}" \
  --no-fail-on-empty-changeset
log "리포지터리 URI: $(stack_output "$STACK_ECR" RepositoryUri)"

# ===== 2. EC2 스택 =====
log "[2/5] EC2 스택 생성: ${STACK_EC2} (VPC·키페어·보안그룹 포함, 3~5분 소요)"
aws cloudformation deploy \
  --stack-name "$STACK_EC2" \
  --template-file "${TEMPLATE_DIR}/ec2.yaml" \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    "KeyPairName=${KEY_PAIR_NAME}" \
    "InstanceType=${INSTANCE_TYPE}" \
    "VolumeSize=${VOLUME_SIZE}" \
    "AllowedSshCidr=${ALLOWED_SSH_CIDR}" \
    "AppPort=${APP_PORT}" \
    "MinioApiPort=${MINIO_API_PORT}" \
    "DeployPath=${DEPLOY_PATH}" \
  --no-fail-on-empty-changeset

# 탄력적 IP(EIP): 스택이 할당해 인스턴스에 연결한다. 재부팅·인스턴스 교체에도 주소가 유지된다
PUBLIC_IP=$(stack_output "$STACK_EC2" PublicIp)
[ -n "$PUBLIC_IP" ] && [ "$PUBLIC_IP" != "None" ] || die "EC2 스택에서 탄력적 IP 를 얻지 못했습니다."
ALLOC_ID=$(stack_output "$STACK_EC2" ElasticIpAllocationId)
ASSOC=$(aws ec2 describe-addresses --allocation-ids "$ALLOC_ID" \
  --query 'Addresses[0].InstanceId' --output text 2>/dev/null || echo "")
if [ -n "$ASSOC" ] && [ "$ASSOC" != "None" ]; then
  log "탄력적 IP ${PUBLIC_IP} 할당·연결 완료 (${ALLOC_ID} → ${ASSOC})"
else
  log "경고: 탄력적 IP ${PUBLIC_IP} 가 인스턴스에 연결되지 않았습니다 (${ALLOC_ID})"
fi

# ===== 3. 키페어 내려받기 (생성은 CloudFormation, 저장은 여기서만) =====
log "[3/5] 키페어 개인키 저장"
KEY_PARAM=$(stack_output "$STACK_EC2" PrivateKeyParameter)
if [ -f "$KEY_PATH" ]; then
  log "이미 존재하므로 건너뜁니다: ${KEY_PATH}"
  log "스택을 재생성했다면 이 파일을 지우고 setup.sh 를 다시 실행하세요."
else
  mkdir -p "$(dirname "$KEY_PATH")"
  aws ssm get-parameter --name "$KEY_PARAM" --with-decryption \
    --query 'Parameter.Value' --output text > "$KEY_PATH"
  chmod 400 "$KEY_PATH"
  log "개인키 저장 완료: ${KEY_PATH} (SSM ${KEY_PARAM})"
fi

# ===== 4. .env.prod 반영 =====
log "[4/5] .env.prod 갱신"
set_env EC2_HOST "$PUBLIC_IP"

# SITE_ADDRESS 가 비어 있으면 sslip.io 호스트명으로 채운다.
# IP 를 그대로 쓰면 공인 인증서를 받을 수 없지만, sslip.io 는 정상 도메인이라
# Caddy 가 Let's Encrypt 인증서를 자동 발급받는다 (도메인 구매 불필요).
CURRENT_SITE=$(read_env SITE_ADDRESS)
if [ -z "$CURRENT_SITE" ] || [ "$CURRENT_SITE" = "CHANGE_ME" ] || [ "$CURRENT_SITE" = "$PUBLIC_IP" ]; then
  set_env SITE_ADDRESS "$(echo "$PUBLIC_IP" | tr '.' '-').sslip.io"
  CURRENT_SITE=$(read_env SITE_ADDRESS)
fi
set_env MINIO_PUBLIC_ENDPOINT "http://${CURRENT_SITE}:${MINIO_API_PORT}"

# OAuth 콜백도 서비스 주소를 따라간다 (각 개발자 콘솔에도 같은 URL 을 등록해야 한다)
set_env KAKAO_REDIRECT_URI "https://${CURRENT_SITE}/api/v1/auth/kakao/callback"
set_env APPLE_REDIRECT_URI "https://${CURRENT_SITE}/api/v1/auth/apple/callback"
# shellcheck disable=SC2034  # common.sh 의 ssh_run 이 사용한다
EC2_HOST="$PUBLIC_IP"

# ===== 5. EC2 초기 설정 완료 대기 =====
log "[5/5] EC2 부팅 및 Docker 설치 대기 (최대 5분)"
for i in $(seq 1 60); do
  if ssh_run "command -v docker >/dev/null && docker compose version >/dev/null" 2>/dev/null; then
    log "EC2 준비 완료 (${i}회차)"
    break
  fi
  if [ "$i" -eq 60 ]; then
    log "아직 준비되지 않았습니다. 잠시 후 'infra/scripts-local/aws-manage.sh status' 로 다시 확인하세요."
    break
  fi
  sleep 5
done

cat <<SUMMARY

===== 구축 완료 =====
  계정        : ${ACCOUNT_ID}
  리전        : ${AWS_REGION}
  탄력적 IP   : ${PUBLIC_IP} (고정, 재부팅해도 유지)
  앱 진입점   : https://$(read_env SITE_ADDRESS)  (HTTP 는 HTTPS 로 리다이렉트)
  MinIO API   : http://$(read_env SITE_ADDRESS):${MINIO_API_PORT}
  SSH         : ssh -i ${KEY_PATH} ${EC2_USER}@${PUBLIC_IP}

다음 단계
  1) ./infra/scripts-local/github-secrets.sh   # GitHub Secrets 등록
  2) main 브랜치에 push               # 자동 배포
     또는 ./infra/scripts-local/aws-manage.sh deploy     # 수동 배포
SUMMARY
