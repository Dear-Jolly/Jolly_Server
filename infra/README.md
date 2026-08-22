# infra

Dear Jolly 서버의 AWS 인프라 정의와 배포 자동화. **이 문서 하나로 구축부터 운영까지 다룬다.**

원칙 네 가지.

| 원칙 | 의미 |
| --- | --- |
| 콘솔에서 만들지 않는다 | 모든 AWS 리소스는 CloudFormation 템플릿이 정의한다 |
| 서버에서 고치지 않는다 | 서버 설정은 배포할 때마다 스크립트가 새로 만든다 |
| 이미지는 CI 에서만 빌드한다 | 로컬·EC2 에서 `docker build` 하지 않는다 |
| 비밀값은 `.env.*` 한 곳에 | 스크립트가 여기서 읽어 Secrets·서버로 퍼뜨린다 |

---

## 1. 폴더 구조

```
infra/
├── env/              # 환경변수 (.env.local, .env.prod + 예시)  ← 모든 설정의 출발점
├── caddy/            # 리버스 프록시 설정 (로컬 / 운영 템플릿)
├── docker/           # Dockerfile, compose.yaml, compose.prod.yaml
├── cloudformation/   # ecr.yaml, ec2.yaml
├── scripts-local/    # 내 컴퓨터에서 실행
└── scripts-ec2/      # EC2 서버에서 실행 (전송 후 원격 실행)
```

### scripts-local — 내 컴퓨터에서 실행

| 스크립트 | 역할 |
| --- | --- |
| [aws-setup.sh](scripts-local/aws-setup.sh) | **최초 1회.** ECR·EC2 스택 생성, 키페어(.pem) 생성·저장, `.env.prod` 자동 갱신 |
| [github-secrets.sh](scripts-local/github-secrets.sh) | `.env.prod` 값을 GitHub Actions Secrets 로 등록. `dump` 인자로 수동 입력용 목록 생성 |
| [aws-manage.sh](scripts-local/aws-manage.sh) | 구축 이후 운영. `deploy [태그]`·`images`·`restart`·`sync`·`drift`·`sg`·`backup`·`status`·`logs`·`ssh`·`update-*`·`down-*` |
| [common.sh](scripts-local/common.sh) | 위 세 스크립트가 공유하는 로직 (`.env.prod` 파싱, SSH 헬퍼, 리전 기본값) |

### scripts-ec2 — EC2 서버에서 실행

직접 실행하지 않는다. GitHub Actions 나 `aws-manage.sh` 가 EC2 로 전송해 원격 실행한다.

| 파일 | 역할 |
| --- | --- |
| [deploy.sh](scripts-ec2/deploy.sh) | 블루그린 무중단 배포. 디렉터리 생성 → 백업 cron 등록 → 비활성 색상 기동 → 헬스체크 → Caddy 전환 → 구버전·구이미지 정리 |
| [backup.sh](scripts-ec2/backup.sh) | 매일 1회 cron 실행. MySQL 덤프 + MinIO tar → `~/backup/`, 3개 초과분 삭제 |

### 나머지 폴더

| 폴더 | 파일 | 내용 |
| --- | --- | --- |
| cloudformation | [ecr.yaml](cloudformation/ecr.yaml) | ECR 리포지터리, 이미지 스캔, 최신 10개 보관 |
| | [ec2.yaml](cloudformation/ec2.yaml) | VPC·서브넷·IGW·라우팅, 보안그룹, IAM 역할, 키페어, 탄력적 IP, EC2(Docker 자동 설치) |
| docker | [Dockerfile](docker/Dockerfile) | 2단계 빌드 (temurin 21-jdk → 21-jre). 컨텍스트는 리포지터리 루트 |
| | [compose.yaml](docker/compose.yaml) | 로컬. Caddy(HTTPS) + 앱 + MySQL + MinIO |
| | [compose.prod.yaml](docker/compose.prod.yaml) | 운영. Caddy + blue/green 앱 + MySQL + MinIO |
| caddy | [Caddyfile.local](caddy/Caddyfile.local) | `https://localhost:8080` 종단, 80 → HTTPS 리다이렉트 |
| | [Caddyfile.prod.template](caddy/Caddyfile.prod.template) | 블루그린 + HTTPS. `deploy.sh` 가 색상·주소·TLS 방식을 치환 |
| env | `.env.local` · `.env.prod` | 실제 값 (gitignore) |
| | `.env.githubsecrets` | Secrets 수동 등록용 목록 (gitignore, 자동 생성) |
| | [.env.local.example](env/.env.local.example) · [.env.prod.example](env/.env.prod.example) | 템플릿 (커밋 대상) |

---

## 2. 최초 구축 절차

### 2.1 사전 준비 — 사람이 해야 하는 것

스크립트가 대신할 수 없는 것은 아래 셋뿐이다.

| 항목 | 내용 |
| --- | --- |
| AWS IAM 사용자 | 액세스 키 발급 후 `.env.prod` 에 기입. **필요한 관리형 정책 5개** ↓ |
| GitHub 토큰 | fine-grained PAT. 대상 리포지터리에 **Repository permissions → Secrets: Read and write** |
| `.env.prod` 작성 | `.env.prod.example` 복사 후 `CHANGE_ME` 를 실제 값으로 |

IAM 사용자에게 붙일 관리형 정책:

```
AWSCloudFormationFullAccess             스택 생성·갱신
AmazonEC2FullAccess                     VPC·보안그룹·키페어·인스턴스
AmazonEC2ContainerRegistryFullAccess    ECR
AmazonSSMFullAccess                     키페어 개인키 저장/조회 + AMI 조회
IAMFullAccess                           EC2 인스턴스 역할 생성 (CAPABILITY_IAM)
```

> **ECR·EC2 권한만으로는 안 된다.** CloudFormation·IAM·SSM 이 함께 필요하다.
> 특히 SSM 은 **읽기 전용으로 부족하다** — `AWS::EC2::KeyPair` 가 개인키를 SSM 에 **쓰기** 때문이다.
> 권한을 좁히려면 `ssm:PutParameter`/`DeleteParameter`/`GetParameter` 를
> `arn:aws:ssm:ap-northeast-2:<계정>:parameter/ec2/keypair/*` 에만 허용한다.

### 2.2 실행

```bash
cp infra/env/.env.prod.example infra/env/.env.prod   # 값 채우기
./infra/scripts-local/aws-setup.sh                   # 스택·키페어·EIP 생성 (3~5분)
./infra/scripts-local/github-secrets.sh              # Secrets 10개 등록
git push origin main                                 # CI 가 빌드·배포
```

`aws-setup.sh` 가 자동으로 하는 일:

| 단계 | 내용 |
| --- | --- |
| 1 | ECR 스택 생성 |
| 2 | EC2 스택 생성 (VPC·서브넷·IGW·보안그룹·IAM 역할·키페어·탄력적 IP·EC2) |
| 3 | 키페어 개인키를 SSM 에서 받아 `EC2_SSH_KEY_PATH` 에 저장 (`chmod 400`) |
| 4 | `.env.prod` 갱신 — `EC2_HOST`, `SITE_ADDRESS`(sslip.io), `MINIO_PUBLIC_ENDPOINT`, OAuth 콜백 URL |
| 5 | EC2 부팅·Docker 설치 완료까지 대기 |

모든 리소스는 **서울 리전(`ap-northeast-2`)** 에 만들어진다. `.env.prod` 에 리전이 비어 있어도 서울로 떨어진다.

### 2.3 GitHub Secrets

`.env.prod` 와 키페어 파일에서 값을 읽어 만든다. **손으로 관리하지 않는다.**

| 명령 | 동작 |
| --- | --- |
| `./infra/scripts-local/github-secrets.sh` | Secrets 10개를 바로 등록·갱신 (토큰 권한 필요) |
| `./infra/scripts-local/github-secrets.sh dump` | `env/.env.githubsecrets` 파일 생성 (콘솔에서 손으로 넣을 때) |

| Secret | 출처 |
| --- | --- |
| `EC2_HOST` · `EC2_USER` · `EC2_PORT` · `DEPLOY_PATH` | `.env.prod` |
| `AWS_ACCESS_KEY_ID` · `AWS_SECRET_ACCESS_KEY` · `AWS_REGION` · `ECR_REPOSITORY` | `.env.prod` |
| `EC2_SSH_KEY` | `EC2_SSH_KEY_PATH` 가 가리키는 `.pem` 파일 내용 |
| `PROD_ENV_FILE` | `.env.prod` 중 앱 실행 환경변수만 추려서 (인프라·배포 설정은 제외) |

> **값이 바뀌면 반드시 다시 실행한다.** IP 변경, 비밀번호 교체, `SITE_ADDRESS` 변경 모두 해당된다.
> `.env.githubsecrets` 는 자동 생성 파일이라 직접 편집하지 않는다 — 다음 생성 때 덮어써진다.

---

## 3. 배포

`main` 에 push 되면 [deploy.yml](../.github/workflows/deploy.yml) 이 자동 실행된다.

| 단계 | 위치 | 내용 |
| --- | --- | --- |
| 1. 빌드 | **GitHub Actions** | 이미지 빌드 → ECR push. 태그 `버전-빌드번호-커밋` (예: `0.0.1-SNAPSHOT-42-a1b2c3d`) |
| 2. 전송 | Actions → EC2 | `compose.prod.yaml`·`deploy.sh`·`backup.sh`·`Caddyfile.prod.template`, Secrets 기반 `.env` |
| 3. 전환 | EC2 | 비활성 색상 기동 → healthy 확인 → Caddy graceful reload |
| 4. 정리 | EC2 | 구버전 컨테이너 종료, 이미지는 현재 + 직전 1개만 보관 |

이미지 빌드는 **CI 에서만** 한다. `aws-manage.sh deploy` 는 CI 가 ECR 에 올린 이미지를 가져다 배포만 한다.

### 블루그린

| 항목 | 내용 |
| --- | --- |
| 진입점 | Caddy 443(HTTPS). 80 은 HTTPS 로 리다이렉트 |
| 앱 컨테이너 | blue 8081 / green 8082 를 번갈아 사용. 루프백 바인딩이라 외부 비공개 |
| 전환 | 신규 색상이 healthy 된 뒤 Caddy reload. 실패 시 전환 없이 롤백 |
| 검증 | 전환 2회 동안 747회 요청 실패 0건 (로컬 재현 기준) |

### ECR 인증

| 위치 | 방식 |
| --- | --- |
| GitHub Actions (push) | Secrets 의 IAM 액세스 키 → `aws-actions/amazon-ecr-login` |
| EC2 (pull) | **인스턴스 IAM 역할**(`AmazonEC2ContainerRegistryReadOnly`)로 서버가 직접 토큰 발급 |

토큰이 머신 사이를 오가지 않는다. 역할이 없는 서버라면 `ECR_PASSWORD` 를 넘기면 되고, 둘 다 없으면 이미 받아둔 이미지로 진행한다.

---

## 4. HTTPS

Caddy 가 TLS 를 종단한다. `SITE_ADDRESS` 값에 따라 인증서 방식이 자동으로 갈린다.

| `SITE_ADDRESS` | 인증서 | 브라우저 경고 |
| --- | --- | --- |
| 도메인 (sslip.io 포함) | Let's Encrypt 자동 발급·갱신 | 없음 |
| IP 주소 | Caddy 내장 CA (자체 서명) | 발생 |

**IP 로는 공인 인증서를 받을 수 없다.** 도메인을 사지 않고 해결하려고 `sslip.io` 를 쓴다 —
`43-201-80-36.sslip.io` 처럼 IP 를 그대로 해석해 주는 공개 DNS 라서, Caddy 입장에서는 정상 도메인이고
Let's Encrypt 인증서를 자동으로 받아온다.

`aws-setup.sh` 가 `SITE_ADDRESS` 를 비워두면 IP 에서 sslip.io 호스트명을 만들어 채우고,
`MINIO_PUBLIC_ENDPOINT` 와 OAuth 콜백 URL 도 같은 주소로 맞춘다.
자체 도메인이 생기면 `.env.prod` 의 `SITE_ADDRESS` 만 바꾸고 `github-secrets.sh` → 재배포하면 된다.

| 알아둘 점 | 내용 |
| --- | --- |
| 포트 | Let's Encrypt 검증에 80·443 이 열려 있어야 한다 (보안그룹에 이미 포함) |
| OAuth 콜백 | `SITE_ADDRESS` 를 바꾸면 카카오·애플 콘솔의 리다이렉트 URL 도 함께 바꿔야 한다 |
| MinIO | 이미지 URL 은 `http://{SITE_ADDRESS}:9000` 이라 HTTPS 가 아니다. mixed content 가 문제되면 Caddy 뒤로 넣어야 한다 |

### 로컬 HTTPS

`./run.sh` 로 띄우면 `https://localhost:8080` 으로 접속한다. `http://localhost` 는 HTTPS 로 리다이렉트된다.

```bash
./run.sh trust    # Caddy 로컬 CA 를 시스템 키체인에 등록 (sudo 필요, 브라우저 경고 제거)
```

---

## 5. 데이터 저장 · 백업

| 항목 | 로컬 | EC2 |
| --- | --- | --- |
| MySQL 데이터 | `dear-jolly/mount/mysql` | `~/mount/mysql` |
| MinIO 데이터 | `dear-jolly/mount/minio` | `~/mount/minio` |
| 백업 | — | `~/backup/mysql`, `~/backup/minio` |

컨테이너가 아니라 호스트 디렉터리에 저장하므로 컨테이너를 지워도 데이터가 남는다.

백업은 매일 03:30 에 돌며 최대 3개(3일치)를 보관한다. cron 은 **배포할 때마다 `deploy.sh` 가 최신 설정으로 재등록**하므로 서버에서 손댈 일이 없다.
경로·주기·보관 개수는 `.env.prod` 의 `MYSQL_DATA_PATH`·`MINIO_DATA_PATH`·`BACKUP_PATH`·`BACKUP_SCHEDULE`·`BACKUP_RETENTION` 으로 조정한다.

```bash
./infra/scripts-local/aws-manage.sh backup    # 즉시 1회 실행
./infra/scripts-local/aws-manage.sh backups   # 보관 목록 확인
```

---

## 6. 운영

```bash
./infra/scripts-local/aws-manage.sh <명령>
```

| 명령 | 용도 |
| --- | --- |
| `deploy [태그]` | ECR 이미지를 블루그린 배포 (생략 시 `latest`). 태그 존재를 먼저 확인한다 |
| `images` | ECR 에 올라온 태그 목록 |
| `restart` · `files` | 현재 이미지로 재배포 / 배포 파일만 재전송 |
| `status` · `outputs` · `logs` · `ssh` | 상태·출력값·로그 확인, 서버 접속 |
| `sync` | `.env.prod` 기준으로 스택 재적용 (보안그룹·포트·SSH 대역 반영) |
| `drift` · `sg` | 콘솔에서 손댄 리소스 탐지 / 현재 보안그룹 규칙 확인 |
| `backup` · `backups` | 백업 즉시 실행 / 보관 목록 |
| `update-ecr` · `update-ec2` | 템플릿 변경분 반영 |
| `down-ec2` · `down-ecr` | 스택 삭제 (ECR 리포지터리는 Retain 으로 보존) |

보안그룹은 [ec2.yaml](cloudformation/ec2.yaml) 이 유일한 기준이다. 콘솔에서 고치지 말고 `.env.prod` 를 수정한 뒤 `sync` 로 반영하고, 손댄 흔적은 `drift` 로 찾는다.

---

## 7. 트러블슈팅 — 실제로 겪은 것들

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| `cloudformation:DescribeStacks ... not authorized` | IAM 에 ECR·EC2 권한만 있음 | §2.1 의 정책 5개 부착 |
| `KeyPair ... InternalFailure "null"` | `ssm:PutParameter` 없음. 키페어 개인키를 SSM 에 못 씀 | `AmazonSSMFullAccess` 부착 |
| 스택 삭제가 `DELETE_FAILED` | `ssm:DeleteParameter` 없어 KeyPair 삭제 실패 | 권한 부여 후 재시도, 또는 `delete-stack --retain-resources KeyPair` |
| `Invalid rule description` (보안그룹) | 규칙 설명에 한글 사용. EC2 는 ASCII 만 허용 | AWS 로 전달되는 `Description` 은 전부 영문으로 유지 |
| ECR pull 시 `not found` | 이미지가 push 되지 않았는데 배포가 진행됨 | `deploy` 가 `describe-images` 로 존재를 먼저 확인 |
| 로컬 Caddy 기동 실패 | 80/443 을 다른 컨테이너·프로세스가 점유 | `lsof -nP -iTCP:80 -sTCP:LISTEN` 로 확인 후 중지, 또는 `.env.local` 의 `APP_HTTP_PORT` 변경 |
| 앱이 `WeakKeyException` 으로 기동 실패 | 컨테이너에 `JWT_SECRET` 이 전달되지 않음 | compose 가 `env_file` 로 `.env` 전체를 주입 |
| `.env` source 중 `3: command not found` | 값에 공백·인라인 주석 (`BACKUP_SCHEDULE=30 3 * * * # …`) | `PROD_ENV_FILE` 생성 시 주석 제거·따옴표 처리 |
| GitHub Secrets 등록 403 | PAT 에 Secrets 쓰기 권한 없음 | 토큰 권한 수정 후 `github-secrets.sh` 재실행 |

---

## 8. 참고

- 각 스크립트 상단 주석에 사전 조건과 필요한 권한이 적혀 있다.
- 프로젝트 전체 개요·로컬 실행은 [루트 README](../README.md) 를 본다.
