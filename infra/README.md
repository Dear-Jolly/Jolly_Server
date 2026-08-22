# infra

AWS 인프라 정의와 배포 스크립트. 실행 위치에 따라 폴더가 나뉜다.

## scripts-local — 내 컴퓨터에서 실행

| 스크립트 | 역할 |
| --- | --- |
| [aws-setup.sh](scripts-local/aws-setup.sh) | **최초 1회.** ECR·EC2 스택 생성, 키페어(.pem) 생성·저장, `infra/env/.env.prod` 갱신 |
| [github-secrets.sh](scripts-local/github-secrets.sh) | `infra/env/.env.prod` 값을 GitHub Actions Secrets 로 등록 (gh CLI). `dump` 인자를 주면 수동 입력용 목록 파일 생성 |
| [aws-manage.sh](scripts-local/aws-manage.sh) | 구축 이후 운영. CI 가 올린 ECR 이미지 배포, 보안그룹 동기화(`sync`)·드리프트 탐지(`drift`), 백업 실행, 로그·SSH |
| [common.sh](scripts-local/common.sh) | 위 세 스크립트가 공유하는 로직 (`infra/env/.env.prod` 파싱, SSH 헬퍼 등) |

## scripts-ec2 — EC2 서버에서 실행

전송 후 원격 실행되는 파일들이다. 직접 실행하는 게 아니라 GitHub Actions 나 `aws-manage.sh` 가 EC2 로 보낸다.

| 파일 | 역할 |
| --- | --- |
| [deploy.sh](scripts-ec2/deploy.sh) | 블루그린 무중단 배포. 데이터·백업 디렉터리 생성 → 백업 cron 등록 → 비활성 색상 기동 → 헬스체크 → Caddy 전환 → 구버전·구이미지 정리 |
| [backup.sh](scripts-ec2/backup.sh) | 매일 1회 cron 실행. MySQL 덤프 + MinIO tar → `~/backup/` 에 저장, 3개 초과분 삭제 |
| [Caddyfile.template](scripts-ec2/Caddyfile.template) | Caddy 설정 원본. `deploy.sh` 가 활성 색상·서비스 주소·TLS 방식을 치환해 사용 (HTTPS 종단) |

## env — 환경변수

| 파일 | 역할 |
| --- | --- |
| `.env.local` | 로컬 실행용 값 (gitignore). `run.sh` 가 `--env-file` 로 넘긴다 |
| `.env.githubsecrets` | Secrets 수동 등록용 목록 (gitignore, `github-secrets.sh dump` 가 생성) |
| `.env.prod` | 운영·배포용 값 + AWS/EC2 접속 정보 (gitignore). 스크립트 3종이 읽는다 |
| [.env.local.example](env/.env.local.example) · [.env.prod.example](env/.env.prod.example) | 위 두 파일의 템플릿 (커밋 대상) |

## caddy — 리버스 프록시 설정

로컬과 운영이 서로 다른 설정을 쓰므로 파일을 분리해 둔다.

| 파일 | 쓰는 곳 | 내용 |
| --- | --- | --- |
| [Caddyfile.local](caddy/Caddyfile.local) | 로컬 (`compose.yaml` 이 마운트) | `https://localhost:8080` 종단, 80 → HTTPS 리다이렉트, 내장 CA |
| [Caddyfile.prod.template](caddy/Caddyfile.prod.template) | 운영 (배포 시 EC2 로 전송) | 블루그린 전환 + HTTPS. `deploy.sh` 가 활성 색상·주소·TLS 방식을 치환 |

둘 다 사람이 서버에서 고치지 않는다. 운영 설정은 배포할 때마다 `deploy.sh` 가 새로 만들고
Caddy 를 graceful reload 하며, 로컬은 `run.sh` 가 컨테이너로 띄운다.

| 인증서 | 방식 |
| --- | --- |
| 로컬 | Caddy 내장 CA. `./run.sh trust` 로 시스템에 신뢰 등록하면 브라우저 경고가 사라진다 |
| 운영 | `SITE_ADDRESS` 가 도메인(sslip.io 포함)이면 Let's Encrypt 자동 발급, IP 면 내장 CA |

## docker — 이미지·컨테이너 정의

| 파일 | 역할 |
| --- | --- |
| [Dockerfile](docker/Dockerfile) | 2단계 빌드 (temurin 21-jdk → 21-jre). 빌드 컨텍스트는 리포지터리 루트 |
| [compose.yaml](docker/compose.yaml) | 로컬 개발용. Caddy(HTTPS) + 앱 + MySQL + MinIO |
| [compose.prod.yaml](docker/compose.prod.yaml) | 운영용. Caddy + blue/green 앱 + MySQL + MinIO |

## GitHub Secrets 관리

`.env.prod` 와 키페어 파일에서 값을 읽어 Secrets 를 만든다. **손으로 관리하지 않는다.**

| 명령 | 동작 |
| --- | --- |
| `./scripts-local/github-secrets.sh` | Secrets 10개를 GitHub 에 바로 등록·갱신 (토큰에 `Secrets: Read and write` 권한 필요) |
| `./scripts-local/github-secrets.sh dump` | `env/.env.githubsecrets` 파일로 목록 생성 (콘솔에서 손으로 넣을 때) |

값이 바뀌면(IP 변경·비밀번호 교체 등) 위 명령을 **다시 실행**해 갱신한다.
`env/.env.githubsecrets` 는 자동 생성 파일이므로 직접 편집하지 않는다. 다음 생성 때 덮어써진다.

| 등록되는 Secret | 출처 |
| --- | --- |
| `EC2_HOST` · `EC2_USER` · `EC2_PORT` · `DEPLOY_PATH` | `.env.prod` |
| `AWS_ACCESS_KEY_ID` · `AWS_SECRET_ACCESS_KEY` · `AWS_REGION` · `ECR_REPOSITORY` | `.env.prod` |
| `EC2_SSH_KEY` | `EC2_SSH_KEY_PATH` 가 가리키는 `.pem` 파일 내용 |
| `PROD_ENV_FILE` | `.env.prod` 중 앱 실행 환경변수만 추려서 |

## 데이터 저장·백업

| 항목 | 로컬 | EC2 |
| --- | --- | --- |
| MySQL 데이터 | `dear-jolly/mount/mysql` | `~/mount/mysql` |
| MinIO 데이터 | `dear-jolly/mount/minio` | `~/mount/minio` |
| 백업 | — | `~/backup/mysql`, `~/backup/minio` (매일 03:30, 3개 보관) |

컨테이너가 아니라 호스트 디렉터리에 저장하므로 컨테이너를 지워도 데이터가 남는다.
경로·주기·보관 개수는 `env/.env.*` 의 `MYSQL_DATA_PATH` · `MINIO_DATA_PATH` · `BACKUP_PATH` · `BACKUP_SCHEDULE` · `BACKUP_RETENTION` 으로 조정한다.

## cloudformation — AWS 리소스 정의

| 템플릿 | 생성 리소스 |
| --- | --- |
| [ecr.yaml](cloudformation/ecr.yaml) | ECR 리포지터리, 이미지 스캔, 최신 10개 보관 라이프사이클 |
| [ec2.yaml](cloudformation/ec2.yaml) | VPC·서브넷·IGW·라우팅, 보안그룹, IAM 역할, 키페어, EIP, EC2(Docker 자동 설치) |

> 보안그룹 규칙은 이 템플릿이 유일한 기준이다. 콘솔에서 직접 고치지 말고 `env/.env.prod` 를 수정한 뒤
> `aws-manage.sh sync` 로 반영한다. 콘솔에서 손댄 흔적은 `aws-manage.sh drift` 로 찾는다.

## 실행 순서

```
aws-setup.sh  →  github-secrets.sh  →  main 에 push (자동 배포)
                                    └→ 또는 aws-manage.sh deploy (수동 배포)
```

이미지 빌드는 GitHub Actions CI 에서만 한다. 로컬·EC2 에서 빌드하지 않으며,
`aws-manage.sh deploy` 는 CI 가 ECR 에 올린 이미지를 가져다 배포만 한다.

각 스크립트에 필요한 사전 조건과 IAM 권한은 파일 상단 주석에 적혀 있다.

```
infra/
├── env/              # 환경변수 (.env.local, .env.prod + 예시)
├── docker/           # Dockerfile, compose.yaml, compose.prod.yaml
├── cloudformation/   # ecr.yaml, ec2.yaml
├── scripts-local/    # 내 컴퓨터에서 실행
└── scripts-ec2/      # EC2 서버에서 실행 (전송 후 원격 실행)
```
