# infra

Dear Jolly 서버의 설정값과 인프라 정의. **구축·설정에 관한 것은 전부 이 문서에 있다.**
실행 명령은 [RUN.md](./RUN.md) 를 본다.

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

우표 초기 데이터는 배포 단계에 별도 작업이 없다. 이미지가 앱 이미지 안에 함께 실려 있어, 컨테이너가 뜰 때 앱이 MinIO 업로드와
`STAMPS` 행 삽입을 스스로 처리한다. 이미 올라가 있으면 건너뛰므로 블루그린 전환마다 반복돼도 무해하다 ([RUN.md](./RUN.md)).

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

로컬도 같은 방식으로 Caddy 가 HTTPS 를 종단하지만, 인증서는 내장 CA 로 만든다.
실행 방법과 신뢰 등록은 [RUN.md](./RUN.md) 를 본다.

---

## 5. 데이터 저장 · 백업

| 항목 | EC2 경로 |
| --- | --- |
| MySQL 데이터 | `~/mount/mysql` |
| MinIO 데이터 | `~/mount/minio` |
| 백업 | `~/backup/mysql` · `~/backup/minio` |

컨테이너가 아니라 호스트 디렉터리에 저장하므로 컨테이너를 지워도 데이터가 남는다.

백업은 매일 03:30 에 돌며 최대 3개(3일치)를 보관한다. cron 은 **배포할 때마다 `deploy.sh` 가 최신 설정으로 재등록**하므로 서버에서 손댈 일이 없다.
경로·주기·보관 개수는 `.env.prod` 의 `MYSQL_DATA_PATH`·`MINIO_DATA_PATH`·`BACKUP_PATH`·`BACKUP_SCHEDULE`·`BACKUP_RETENTION` 으로 조정한다.

```bash
./infra/scripts-local/aws-manage.sh backup    # 즉시 1회 실행
./infra/scripts-local/aws-manage.sh backups   # 보관 목록 확인
```

---

## 6. 인스턴스 스펙

| 항목 | 값 | 조정 위치 (`.env.prod`) |
| --- | --- | --- |
| 타입 | t3.micro (vCPU 2 · 메모리 1GB) | `EC2_INSTANCE_TYPE` |
| 스왑 | 4GB, `vm.swappiness=20` | `EC2_SWAP_SIZE` |
| 디스크 | 30GB gp3 (암호화) | `EC2_VOLUME_SIZE` |
| OS | Ubuntu 24.04 LTS | 템플릿의 SSM 파라미터 |

메모리 1GB 에 컨테이너 5개(전환 중 6개)가 뜨므로 스왑으로 보완한다.
JVM 힙은 `compose.prod.yaml` 의 `JAVA_OPTS` 가 `MaxRAMPercentage=35` 로 묶어, 전환 중 앱 2개가 동시에 떠도 버티게 한다.

| 상황 | 명령 | 비고 |
| --- | --- | --- |
| 스왑 크기 변경 | `aws-manage.sh swap` | 실행 중인 인스턴스에 바로 적용 (재생성 없음) |
| 타입·디스크 변경 | `aws-manage.sh sync` | **타입이 바뀌면 자동으로 백업 후 진행** ↓ |
| 백업 AMI 확인 | `aws-manage.sh amis` | 타입 변경 전에 남긴 스냅샷 목록 |

> **인스턴스 타입을 바꾸면 CloudFormation 이 인스턴스를 정지·교체한다.**
> 그래서 `sync`·`update-ec2` 는 타입 변경을 감지하면 진행 전에 반드시
> ① 데이터 백업(`backup.sh`) → ② 인스턴스 전체 AMI 스냅샷을 만든다. 둘 중 하나라도 실패하면 변경을 중단한다.
> user-data 는 최초 부팅에만 실행되므로, 이미 떠 있는 인스턴스의 스왑은 `swap` 명령으로 맞춘다.

---

## 7. 환경변수

모든 설정의 출발점은 `env/` 의 `.env` 파일 하나다. compose 가 `env_file` 로 통째로 앱 컨테이너에 주입하므로,
값을 추가해도 compose 를 고칠 필요가 없다. 로컬은 `.env.local`, 운영은 `.env.prod` 이며 **둘 다 gitignore 대상이라 커밋되지 않는다.**
기본값과 주석은 [.env.local.example](env/.env.local.example) · [.env.prod.example](env/.env.prod.example) 에 있다.

### 애플리케이션 · 인프라

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `APP_PORT` | `8080` | Caddy 가 여는 HTTPS 포트. 앱이 접속하는 주소 |
| `APP_HTTP_PORT` | `80` | HTTP 진입 포트. 점유돼 있으면 `8000` 등으로 바꾼다 |
| `APP_INTERNAL_PORT` | `8081` | 앱 컨테이너가 듣는 포트 (Caddy 뒤, 루프백 전용) |
| `MYSQL_PORT` · `MYSQL_DATABASE` | `3306` · `dearjolly` | MySQL 포트·스키마 |
| `MYSQL_USER` · `MYSQL_PASSWORD` | `jolly` · **필수** | 앱이 쓰는 계정 |
| `MYSQL_ROOT_PASSWORD` | **필수** | `reset-db` 등 관리 작업용 |
| `MYSQL_DATA_PATH` · `MINIO_DATA_PATH` | `../../../mount/{mysql,minio}` | 호스트 저장 경로. compose 파일 기준 상대경로 |
| `MINIO_API_PORT` · `MINIO_CONSOLE_PORT` | `9000` · `9001` | MinIO 포트 |
| `MINIO_ROOT_USER` · `MINIO_ROOT_PASSWORD` | `jollyadmin` · **필수** | MinIO 관리자. 앱에는 `MINIO_ACCESS_KEY`/`SECRET_KEY` 로 전달된다 |
| `MINIO_BUCKET` | `dear-jolly-stamps` | 우표 이미지 버킷. 기동 시 자동 생성되고 공개 읽기로 열린다 |
| `STAMP_SEED_ENABLED` | `true` | 기동 시 우표 시드 실행 여부 |

### 스키마 · 로깅

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `JPA_DDL_AUTO` | 앱 기본값 `validate` | `FLYWAY_ENABLED` 와 **항상 같이 움직인다** |
| `FLYWAY_ENABLED` | `true` | |
| `JPA_SHOW_SQL` | `true` | 실행 SQL 로그 |

### 인증 · 정책

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `JWT_SECRET` | 예시값 존재 | **HS256 시크릿. 256bit(32바이트) 이상.** 짧으면 `WeakKeyException` 으로 기동 실패 |
| `JWT_ACCESS_EXPIRE` · `JWT_REFRESH_EXPIRE` | 30분 · 14일 (ms) | 토큰 만료 |
| `TERMS_CURRENT_VERSION` | `1.0.0` | 현재 약관 버전. 동의 시점에 기록된다 |
| `WITHDRAWAL_RETENTION_DAYS` | `30` | 탈퇴 후 완전 삭제까지의 유예 |

### 소셜 로그인 — 비어 있어도 기동된다

로그인 API 를 실제로 호출할 때만 필요하다. 값이 없으면 provider 호출 단계에서 실패한다.

| 변수 | 발급처 |
| --- | --- |
| `KAKAO_CLIENT_ID` · `KAKAO_CLIENT_SECRET` · `KAKAO_ADMIN_KEY` | Kakao 개발자 콘솔 → 앱 키 (REST API 키) |
| `KAKAO_REDIRECT_URI` | 콘솔에 등록한 값과 **완전히 같아야 한다.** 로컬은 `http://localhost:8080/api/v1/auth/kakao/callback` |
| `APPLE_CLIENT_ID` · `APPLE_TEAM_ID` · `APPLE_KEY_ID` · `APPLE_PRIVATE_KEY` | Apple Developer → Identifiers (Services ID) · Keys |
| `APPLE_REDIRECT_URI` | 로컬은 `http://localhost:8080/api/v1/auth/apple/callback` |
| `OAUTH_APP_REDIRECT_URI` | 인증 후 앱으로 돌아갈 딥링크. 기본 `dearjolly://auth/callback` |

> 로컬 콜백은 `http://localhost` 라 앱 딥링크 검증까지 그대로 재현되지 않는다. 흐름 전체를 확인하려면 배포 환경에서 테스트한다.

### LLM (편지 피드백)

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `OPENAI_API_KEY` | 없음 | 비어 있으면 피드백 파이프라인만 동작하지 않는다 |
| `OPENAI_MODEL` | `gpt-4o-mini` | 모델 ID 는 `FEEDBACKS.model` 에 기록돼 재현·과금 추적에 쓰인다 |

---

## 8. 스키마 관리

스키마의 정본은 [db/migration](../src/main/resources/db/migration) 의 Flyway 마이그레이션이고,
데이터 모델 문서는 [ERD.md](../docs/ERD.md) 다.

`JPA_DDL_AUTO` 와 `FLYWAY_ENABLED` 는 **스키마 소유자를 정하는 한 쌍**이라 항상 같이 움직인다.

| 상황 | `JPA_DDL_AUTO` | `FLYWAY_ENABLED` | 의미 |
| --- | --- | --- | --- |
| 기본 · 출시 | `validate` | `true` | Flyway 가 스키마를 소유하고, Hibernate 는 대조만 한다 |
| 출시 전 개발 중 | `update` | `false` | 엔티티 변경이 스키마에 바로 반영된다 |

> 현재 [.env.local.example](env/.env.local.example) 은 개발 편의를 위해 `JPA_DDL_AUTO=update` 로 배포되어 있다.
> 출시 시점에는 `validate` / `true` 조합으로 되돌린다.

`update` 는 컬럼을 **추가만** 하고 삭제·타입 축소는 하지 않는다. 엔티티에서 필드를 빼면 DB 에 죽은 컬럼이 남으므로,
그럴 때 `./run.sh reset-db` 로 한 번 비운다. `V1__init.sql` 을 수정했을 때도 checksum 불일치로 기동이 거부되므로 같은 명령을 쓴다.

### 초기 데이터 — 우표 시드

`STAMPS` 행과 우표 이미지는 손으로 넣지 않는다. 이미지 원본이 [seed/stamps](../src/main/resources/seed/stamps) 에 들어 있고,
앱이 기동할 때 `StampSeeder` 가 **MinIO 업로드 + DB 행 삽입**을 함께 처리한다. 로컬이든 운영 배포든 경로가 같다.

| 항목 | 규칙 |
| --- | --- |
| `STAMPS.name` | 확장자를 뗀 파일명 (`꽃_장미.png` → `꽃_장미`) |
| `STAMPS.image_key` | `stamp/` + 파일명 (`stamp/꽃_장미.png`) |
| 오브젝트 위치 | `{MINIO_BUCKET}/stamp/{파일명}` |
| 순서 | `soon` 이 `stamp_id = 1`, 나머지는 파일명 순 |

시드는 멱등하다. MinIO 는 같은 키에 같은 크기면 건너뛰고 다르면 덮어쓰며, DB 는 같은 `name` 행이 있으면 `image_key` 가
다를 때만 갱신한다. 둘 다 "있으면 맞추고 없으면 만든다" 라서 몇 번을 실행하든 같은 상태로 수렴하고,
블루그린 배포로 컨테이너가 매번 새로 떠도 실제로 쓰는 것은 첫 회뿐이다.

| 상황 | 대응 |
| --- | --- |
| 우표를 추가하고 싶다 | 이미지를 `seed/stamps/` 에 넣고 배포한다. 채울 값은 없다 |
| 시드를 아예 끄고 싶다 | `.env` 에 `STAMP_SEED_ENABLED=false` |
| 이미지를 교체하고 싶다 | `seed/stamps/` 의 파일을 바꾸고 배포한다. 크기가 달라지면 자동으로 덮어쓴다 |
| MinIO 가 죽어 시드가 실패했다 | 기동은 막지 않는다. 로그에 `우표 시드에 실패했다` 가 남고, 다음 기동에서 다시 시도한다 |

`soon.png` 는 편지 등록 시점에 붙는 "준비 중" 우표다. `stamp_id = 1` 로 들어가고 LLM 우표 선택 후보에서는 빠진다.

> 파일명이 곧 이름이자 파일 키라 **파일명을 바꾸면 새 우표가 하나 더 생긴다.** 이름을 고치려면 기존 행을 직접 정리해야 한다.

---

## 9. 트러블슈팅 — 실제로 겪은 것들

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| `cloudformation:DescribeStacks ... not authorized` | IAM 에 ECR·EC2 권한만 있음 | §2.1 의 정책 5개 부착 |
| `KeyPair ... InternalFailure "null"` | `ssm:PutParameter` 없음. 키페어 개인키를 SSM 에 못 씀 | `AmazonSSMFullAccess` 부착 |
| 스택 삭제가 `DELETE_FAILED` | `ssm:DeleteParameter` 없어 KeyPair 삭제 실패 | 권한 부여 후 재시도, 또는 `delete-stack --retain-resources KeyPair` |
| `Invalid rule description` (보안그룹) | 규칙 설명에 한글 사용. EC2 는 ASCII 만 허용 | AWS 로 전달되는 `Description` 은 전부 영문으로 유지 |
| ECR pull 시 `not found` | 이미지가 push 되지 않았는데 배포가 진행됨 | `deploy` 가 `describe-images` 로 존재를 먼저 확인 |
| 앱이 `WeakKeyException` 으로 기동 실패 | 컨테이너에 `JWT_SECRET` 이 전달되지 않음 | compose 가 `env_file` 로 `.env` 전체를 주입 |
| `.env` source 중 `3: command not found` | 값에 공백·인라인 주석 (`BACKUP_SCHEDULE=30 3 * * * # …`) | `PROD_ENV_FILE` 생성 시 주석 제거·따옴표 처리 |
| GitHub Secrets 등록 403 | PAT 에 Secrets 쓰기 권한 없음 | 토큰 권한 수정 후 `github-secrets.sh` 재실행 |

---

## 10. 참고

- 각 스크립트 상단 주석에 사전 조건과 필요한 권한이 적혀 있다.
- 프로젝트 개요는 [루트 README](../README.md), 실행 명령은 [RUN.md](./RUN.md) 를 본다.
- 이 문서는 **설정·구축의 유일한 기준**이다. 설정값 설명을 다른 문서에 흩어 두지 않는다.
