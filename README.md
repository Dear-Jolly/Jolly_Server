# jolly_server

Write to Jolly, feel jolly

## 1. 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 언어 · 런타임 | Java 21 (Eclipse Temurin) |
| 프레임워크 | Spring Boot 3.5.7 (Web, Data JPA, Validation) |
| 데이터베이스 | MySQL 8.4 (`utf8mb4` / `Asia/Seoul`) |
| 오브젝트 스토리지 | MinIO (우표 이미지, S3 호환) |
| 빌드 | Gradle 8.14.3 (Wrapper) |
| 실행 환경 | Docker · Docker Compose |
| 배포 | AWS (CloudFormation · ECR · EC2), GitHub Actions, Caddy 블루그린 |

## 2. 접속 정보

### 로컬 실행 시

| 대상 | 주소 | 계정 |
| --- | --- | --- |
| 애플리케이션 | **https://localhost:8080** (Caddy) | - |
| HTTP 진입 | http://localhost → HTTPS 자동 리다이렉트 | - |
| MySQL | localhost:3306 (`dearjolly`) | `MYSQL_USER` / `MYSQL_PASSWORD` |
| MinIO API | http://localhost:9000 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |
| MinIO 콘솔 | http://localhost:9001 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |

> 계정 값은 `infra/env/.env.local` 에 있다. DB·이미지 데이터는 프로젝트 루트의 `dear-jolly/mount/` 에 저장된다.

### 운영 접근 시

| 대상 | 주소 | 비고 |
| --- | --- | --- |
| 애플리케이션 | `https://{SITE_ADDRESS}` (443) | Caddy 종단. 80 으로 들어오면 HTTPS 로 리다이렉트 |
| MinIO API | `http://{EC2_HOST}:9000` | 우표 이미지 공개 제공 |
| 앱 컨테이너 | `127.0.0.1:8081` / `8082` | blue / green. 루프백 바인딩이라 외부 비공개 |
| MySQL · MinIO 콘솔 | `127.0.0.1:3306` / `9001` | 루프백 바인딩. SSH 터널로만 접근 |
| SSH | `ssh -i {EC2_SSH_KEY_PATH} ubuntu@{EC2_HOST}` | `./infra/scripts-local/aws-manage.sh ssh` 로도 접속 |
| 데이터 · 백업 | `~/mount/{mysql,minio}` · `~/backup/{mysql,minio}` | 호스트 저장, 매일 03:30 백업 3개 보관 |

> `{EC2_HOST}` 는 `infra/env/.env.prod` 에 있으며, 구축 시 자동으로 채워진다.

## 3. 로컬 실행 방법

| 명령 | 설명 |
| --- | --- |
| `./run.sh` | 앱 + MySQL + MinIO 기동 (이미지 재빌드 포함) |
| `./run.sh logs` | 앱 로그 실시간 확인 |
| `./run.sh ps` | 컨테이너 상태 확인 |
| `./run.sh restart` | 앱 컨테이너만 재빌드 후 재기동 |
| `./run.sh down` | 컨테이너 종료 (데이터 유지) |
| `./run.sh clean` | 컨테이너 + 볼륨 삭제 (DB·이미지 데이터 초기화) |
| `./run.sh trust` | Caddy 로컬 CA 를 시스템에 신뢰 등록 (브라우저 인증서 경고 제거) |

> 최초 실행 시 `infra/env/.env.local.example` 이 `infra/env/.env.local` 로 자동 복사된다. 포트·계정 정보는 이 파일에서 수정한다.
> `run.sh` 를 거치지 않고 직접 실행할 때는 `docker compose -f infra/docker/compose.yaml --env-file infra/env/.env.local ...` 형태로 지정해야 한다.

## 4. 원격 서버 실행 방법

빈 AWS 계정 + `infra/env/.env.prod` 의 IAM 액세스 키만 있으면 VPC·키페어까지 전부 생성된다.
필요한 IAM 권한은 [aws-setup.sh](infra/scripts-local/aws-setup.sh) 상단 주석 참고.

### 4.1 최초 1회 — 구축

| 순서 | 명령 | 동작 |
| --- | --- | --- |
| 1 | `infra/env/.env.prod.example` → `.env.prod` 복사 후 작성 | IAM 키·DB·MinIO 비밀번호 기입 (`CHANGE_ME` 제거) |
| 2 | `./infra/scripts-local/aws-setup.sh` | ECR 스택 → EC2 스택 → 키페어(.pem) 생성·저장 → `.env.prod` 갱신 |
| 3 | `./infra/scripts-local/github-secrets.sh` | `.env.prod` 값을 GitHub Secrets 로 등록 (gh CLI 필요) |

생성되는 리소스는 [infra/cloudformation/](infra/cloudformation/) 의 두 템플릿이 정의한다 — ECR(최신 10개 보관 정책), VPC·서브넷·IGW·보안그룹·IAM 역할·키페어·EIP·EC2(Docker 자동 설치).

### 4.2 배포

`main` 브랜치에 push 되면 [deploy.yml](.github/workflows/deploy.yml) 이 자동 배포한다.

| 단계 | 동작 |
| --- | --- |
| 1. 빌드 | 이미지 빌드 후 ECR push. 태그는 `build.gradle` 버전-빌드번호-커밋 (예: `0.0.1-SNAPSHOT-42-a1b2c3d`) |
| 2. 전송 | `compose.prod.yaml` · `deploy.sh` · `Caddyfile.template` 전송, Secrets 기반 `.env` 생성 |
| 3. 전환 | 비활성 색상 기동 → healthy 확인 → Caddy reload 로 트래픽 전환 |
| 4. 정리 | 구버전 컨테이너 종료, 이미지는 현재 + 직전 1개만 남기고 삭제 |

이미지 빌드는 **GitHub Actions CI 에서만** 한다. 로컬이나 EC2 에서 빌드하지 않는다.
배포·운영은 `./infra/scripts-local/aws-manage.sh` 를 쓴다.

| 명령 | 동작 |
| --- | --- |
| `deploy [태그]` | **CI 가 ECR 에 올린 이미지**를 블루그린 배포 (생략 시 `latest`) |
| `images` · `files` · `restart` | ECR 이미지 목록 / 배포 파일 재전송 / 현재 이미지로 재배포 |
| `status` · `outputs` · `logs` · `ssh` | 상태·출력값 확인, 로그 확인, 서버 접속 |
| `sync` · `drift` · `sg` | `.env.prod` 기준 스택 재적용(보안그룹 포함) / 콘솔 변경 탐지 / 현재 규칙 확인 |
| `swap` · `amis` | 스왑 크기 적용 / 타입 변경 전 백업 AMI 목록 |
| `backup` · `backups` | 백업 즉시 실행 / 보관 목록 확인 |
| `update-ecr` · `update-ec2` · `down-ec2` · `down-ecr` | 스택 갱신 / 삭제 |

### 4.3 블루그린 구성

| 항목 | 내용 |
| --- | --- |
| 진입점 | Caddy 443(HTTPS). 80 은 HTTPS 로 리다이렉트 |
| 앱 컨테이너 | blue 8081 / green 8082 를 번갈아 사용 |
| 전환 | 신규 색상이 healthy 된 뒤 Caddy graceful reload. 실패 시 전환 없이 롤백 |
| 검증 | 전환 2회 동안 747회 요청 실패 0건 (로컬 재현 기준) |

### 4.4 HTTPS

Caddy 가 TLS 를 종단한다. `SITE_ADDRESS` 값에 따라 인증서 방식이 자동으로 갈린다.

| `SITE_ADDRESS` | 인증서 | 비고 |
| --- | --- | --- |
| 도메인 | Let's Encrypt 자동 발급·갱신 | 경고 없음. 권장 |
| IP 주소 | Caddy 내장 CA(자체 서명) | **브라우저 경고 발생.** IP 로는 공인 인증서를 받을 수 없다 |

도메인이 준비되면 `.env.prod` 의 `SITE_ADDRESS` 만 바꾸고 재배포하면 된다.

### 4.5 데이터 저장 · 백업

| 항목 | 내용 |
| --- | --- |
| 저장 위치 | 컨테이너가 아닌 EC2 호스트. MySQL `~/mount/mysql`, MinIO `~/mount/minio` |
| 백업 | 매일 03:30 자동 실행. MySQL 덤프 + MinIO tar → `~/backup/{mysql,minio}` |
| 보관 | 최대 3개(3일치). 초과분은 오래된 것부터 자동 삭제 |
| 등록 방식 | 배포할 때마다 `deploy.sh` 가 cron 을 최신 설정으로 재등록 (수동 작업 없음) |

### 4.6 보안그룹 · 인프라 변경

보안그룹·포트·SSH 허용 대역은 [ec2.yaml](infra/cloudformation/ec2.yaml) 이 유일한 기준이다. 콘솔에서 직접 고치지 않는다.

| 상황 | 명령 |
| --- | --- |
| 포트·SSH 대역을 바꿨다 | `.env.prod` 수정 후 `aws-manage.sh sync` |
| 누가 콘솔에서 손댔는지 확인 | `aws-manage.sh drift` |
| 현재 적용된 규칙 확인 | `aws-manage.sh sg` |

### 4.7 ECR 인증

| 위치 | 방식 |
| --- | --- |
| GitHub Actions (push) | Secrets 의 IAM 액세스 키 → `aws-actions/amazon-ecr-login` |
| EC2 (pull) | 인스턴스 IAM 역할(`AmazonEC2ContainerRegistryReadOnly`)로 서버가 직접 토큰 발급 |

> 토큰이 서버로 오가지 않는다. 인스턴스 역할이 없다면 `ECR_PASSWORD` 를 넘기면 되고, 둘 다 없으면 이미 받아둔 이미지로 진행한다.

### 4.8 배포 설정

| 항목 | 내용 |
| --- | --- |
| 설정 파일 | `infra/env/.env.prod` (gitignore) |
| 등록되는 Secrets | `EC2_HOST` · `EC2_USER` · `EC2_PORT` · `DEPLOY_PATH` · `EC2_SSH_KEY` · `AWS_ACCESS_KEY_ID` · `AWS_SECRET_ACCESS_KEY` · `AWS_REGION` · `ECR_REPOSITORY` · `PROD_ENV_FILE` |
| 인프라 파일 위치 | [infra/README.md](infra/README.md) 참고 |

## 5. 문서

| 문서 | 내용 |
| --- | --- |
| [docs/기능명세.md](docs/기능명세.md) | 기능 명세 |
| [docs/API명세.md](docs/API명세.md) | API 명세 |
| [docs/ERD.md](docs/ERD.md) | 데이터 모델 |
| [infra/README.md](infra/README.md) | 인프라 파일 구조 |
