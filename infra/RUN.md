# 실행 가이드

로컬에서 서버를 띄우고, 배포된 서버를 다루고, 테스트를 돌리는 방법.
**설정값·구축 절차는 [README.md](./README.md) 를 본다.**

| 항목 | 내용 |
| --- | --- |
| 최종 갱신 | 2026-08-22 |
| 실행 방식 | Docker Compose (앱 · MySQL · MinIO · Caddy) |

---

## 1. 사전 준비

| 도구 | 버전 | 비고 |
| --- | --- | --- |
| Docker · Docker Compose | Compose V2 (`docker compose`) | Docker Desktop 또는 Colima. **앱은 컨테이너에서 빌드되므로 로컬 JDK 는 없어도 된다** |
| Git | - | - |
| JDK 21 | Eclipse Temurin 권장 | IDE 에서 열거나 `./gradlew test` 를 돌릴 때만 필요 |

포트 `8080`(HTTPS) · `80`(HTTP) · `3306`(MySQL) · `9000`·`9001`(MinIO) 가 비어 있어야 한다.
점유돼 있으면 `.env.local` 에서 포트를 바꾼다.

---

## 2. 로컬 실행

```bash
git clone <repo> && cd server
./run.sh
```

최초 실행 시 `infra/env/.env.local.example` 이 `infra/env/.env.local` 로 자동 복사된다.
**그대로도 기동되지만**, `CHANGE_ME` 로 남아 있는 DB·MinIO 비밀번호는 바꿔 쓰는 편이 좋다.
소셜 로그인과 AI 피드백을 실제로 쓰려면 [README.md 의 환경변수](./README.md) 에서 키를 채운다.

```bash
./run.sh trust   # macOS. 브라우저 인증서 경고 제거 (sudo 필요)
```

Caddy 가 자체 서명 인증서로 HTTPS 를 종단하므로 등록 전에는 브라우저 경고가 뜬다.
앱·Postman 에서는 인증서 검증을 끄고 호출해도 된다.

### run.sh 명령

| 명령 | 동작 |
| --- | --- |
| `./run.sh` (= `up`) | 앱 + MySQL + MinIO + Caddy 기동 (변경 시 이미지 재빌드) |
| `./run.sh logs` | 앱 로그 실시간 확인 |
| `./run.sh ps` | 컨테이너 상태 확인 |
| `./run.sh restart` | 앱 컨테이너만 재빌드 후 재기동 |
| `./run.sh down` | 컨테이너 종료 (데이터 유지) |
| `./run.sh reset-db` | DB 테이블을 전부 비우고 스키마를 다시 생성 (MinIO 는 그대로) |
| `./run.sh clean` | 컨테이너 + MySQL·MinIO 데이터까지 전부 삭제 |
| `./run.sh trust` | Caddy 로컬 CA 를 시스템 키체인에 신뢰 등록 (macOS 전용) |

환경변수를 바꾼 뒤에는 `./run.sh restart` 로 앱을 다시 띄운다.

`run.sh` 를 거치지 않고 직접 실행할 때는 compose 파일과 env 파일을 매번 지정해야 한다.

```bash
docker compose -f infra/docker/compose.yaml --env-file infra/env/.env.local ps
```

### 접속 정보

| 대상 | 주소 | 계정 |
| --- | --- | --- |
| 애플리케이션 | **https://localhost:8080** (Caddy 종단) | - |
| HTTP 진입 | http://localhost → HTTPS 자동 리다이렉트 | - |
| 앱 컨테이너 직접 | http://127.0.0.1:8081 (디버깅용, 루프백 전용) | - |
| Swagger UI | https://localhost:8080/swagger-ui/index.html | - |
| 헬스체크 | https://localhost:8080/actuator/health | - |
| MySQL | localhost:3306 (`dearjolly`) | `MYSQL_USER` / `MYSQL_PASSWORD` |
| 우표 이미지 | https://localhost:8080/dear-jolly-stamps/... (Caddy 종단) | 공개 |
| MinIO API | http://localhost:9000 (직접 접근, 디버깅용) | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |
| MinIO 콘솔 | http://localhost:9001 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |

DB·이미지 데이터는 컨테이너가 아니라 호스트의 `dear-jolly/mount/{mysql,minio}` 에 쌓인다.
컨테이너를 지워도 남고, 지우려면 `./run.sh clean` 을 쓴다.

---

## 3. 원격 실행

배포는 `main` push 로 자동 실행된다. 아래는 **이미 떠 있는 서버를 다룰 때** 쓰는 명령이다.

```bash
./infra/scripts-local/aws-manage.sh <명령>
```

| 명령 | 용도 |
| --- | --- |
| `deploy [태그]` | ECR 이미지를 블루그린 배포 (생략 시 `latest`). 태그 존재를 먼저 확인한다 |
| `images` | ECR 에 올라온 태그 목록 |
| `restart` · `files` | 현재 이미지로 재배포 / 배포 파일만 재전송 |
| `status` · `outputs` · `logs` · `ssh` | 상태·출력값·로그 확인, 서버 접속 |
| `sync` | `.env.prod` 기준으로 스택 재적용 (보안그룹·포트·SSH 대역). 타입 변경 시 백업 선행 |
| `swap` · `amis` | 스왑 크기 적용 / 백업 AMI 목록 |
| `drift` · `sg` | 콘솔에서 손댄 리소스 탐지 / 현재 보안그룹 규칙 확인 |
| `backup` · `backups` | 백업 즉시 실행 / 보관 목록 |
| `update-ecr` · `update-ec2` | 템플릿 변경분 반영 |
| `down-ec2` · `down-ecr` | 스택 삭제 (ECR 리포지터리는 Retain 으로 보존) |

이미지 빌드는 **CI 에서만** 한다. `deploy` 는 CI 가 ECR 에 올린 이미지를 가져다 배포만 한다.

### 접속 정보

| 대상 | 주소 | 비고 |
| --- | --- | --- |
| 애플리케이션 | `https://{SITE_ADDRESS}` (443) | Caddy 종단. 80 으로 들어오면 HTTPS 로 리다이렉트 |
| 우표 이미지 | `https://{SITE_ADDRESS}/{MINIO_BUCKET}/...` | Caddy 가 MinIO 로 넘긴다. 앱이 받는 `stampImage` 주소 |
| MinIO API | `http://{EC2_HOST}:9000` | 직접 접근, 디버깅용 |
| 앱 컨테이너 | `127.0.0.1:8081` · `8082` | blue / green. 루프백 바인딩이라 외부 비공개 |
| MySQL · MinIO 콘솔 | `127.0.0.1:3306` · `9001` | 루프백 바인딩. SSH 터널로만 접근 |
| SSH | `ssh -i {EC2_SSH_KEY_PATH} ubuntu@{EC2_HOST}` | `aws-manage.sh ssh` 로도 접속 |

`{EC2_HOST}` · `{SITE_ADDRESS}` 는 `env/.env.prod` 에 있으며 `aws-setup.sh` 가 구축 시 자동으로 채운다.

보안그룹은 [ec2.yaml](cloudformation/ec2.yaml) 이 유일한 기준이다.
콘솔에서 고치지 말고 `.env.prod` 를 수정한 뒤 `sync` 로 반영하고, 손댄 흔적은 `drift` 로 찾는다.

---

## 4. 테스트

```bash
./gradlew test                        # 전체
./gradlew test --tests '*AuthApiTest' # 단건
```

| 계층 | 방식 | 비고 |
| --- | --- | --- |
| API 통합 | RestAssured + `@SpringBootTest` | API 당 필수 |
| 서비스 단위 | Mockito | |
| 레포지터리 | H2 | |
| 스키마 대조 | Testcontainers + 실제 MySQL | `SchemaMigrationTest` 가 Flyway 결과와 엔티티 매핑을 대조한다 |

Testcontainers 는 **Docker 가 떠 있어야** 돈다. Docker Desktop 이든 Colima 든 띄워만 두면 `./gradlew test` 가 그대로 돈다 —
`DOCKER_HOST` 가 비어 있고 `~/.colima/default/docker.sock` 이 있으면 `build.gradle` 이 소켓 경로 · API 버전 · Ryuk 비활성화를 자동으로 채운다.

```bash
colima status   # 안 떠 있으면 colima start
```

소켓 경로가 위와도 다르면 쉘에서 직접 내보낸다. 쉘 값이 자동 탐지보다 우선한다.

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export DOCKER_API_VERSION=1.47
export TESTCONTAINERS_RYUK_DISABLED=true
```

마지막 값이 없으면 정리 컨테이너(Ryuk)가 colima 소켓을 마운트하려다 `operation not supported` 로 죽는다.

---

## 5. 자주 겪는 문제

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| Caddy 기동 실패 | 80/443 을 다른 프로세스가 점유 | `lsof -nP -iTCP:80 -sTCP:LISTEN` 로 확인 후 중지, 또는 `APP_HTTP_PORT` 변경 |
| 브라우저 인증서 경고 | Caddy 내장 CA(자체 서명) | `./run.sh trust` (macOS) |
| `WeakKeyException` 으로 기동 실패 | `JWT_SECRET` 이 32바이트 미만이거나 비어 있음 | `openssl rand -base64 48` 로 생성해 교체 |
| `Schema-validation: missing column` | 엔티티는 바뀌었는데 스키마가 그대로 | 마이그레이션 추가, 또는 개발 중이라면 `./run.sh reset-db` |
| 엔티티에서 지운 컬럼이 DB 에 남음 | `update` 는 컬럼을 삭제하지 않는다 | `./run.sh reset-db` |
| Flyway checksum 불일치 | 적용된 마이그레이션 파일을 수정 | `./run.sh reset-db` |
| 컨테이너를 지웠는데 옛 데이터가 남음 | 호스트 바인드 마운트라 `down -v` 로는 안 지워진다 | `./run.sh clean` |
| Testcontainers 가 Docker 를 못 찾음 | Docker 가 안 떠 있거나 소켓 경로가 자동 탐지 대상 밖 | `colima start` 후 재시도, 그래도 안 되면 §4 의 환경변수 |
| 컨테이너 재생성이 건너뛰어짐 | 스펙이 그대로면 Compose 가 재생성하지 않는다 | `./run.sh restart` (내부적으로 `--build`) |

구축·배포 과정에서 겪는 문제는 [README.md](./README.md) 의 트러블슈팅을 본다.

---

## 6. 함께 볼 문서

| 문서 | 내용 |
| --- | --- |
| [README.md](./README.md) | 설정값 · 구축 · 배포 · 운영 정책 |
| [기능명세.md](../docs/기능명세.md) | 도메인 규칙 · 처리 흐름 |
| [API명세.md](../docs/API명세.md) | 요청/응답 계약의 정본 |
| [ERD.md](../docs/ERD.md) | 데이터 모델의 정본 |
