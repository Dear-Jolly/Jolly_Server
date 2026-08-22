# Dear Jolly — 실행 가이드

로컬에서 서버를 띄우고 테스트를 돌리는 방법. **배포·운영은 [infra/README.md](../infra/README.md) 를 본다.**

| 항목 | 내용 |
| --- | --- |
| 최종 갱신 | 2026-08-22 |
| 대상 | 이 리포지터리를 처음 클론한 서버 개발자 |
| 실행 방식 | Docker Compose (앱 · MySQL · MinIO · Caddy 4개 컨테이너) |

---

## 1. 사전 준비

| 도구 | 버전 | 비고 |
| --- | --- | --- |
| Docker · Docker Compose | Compose V2 (`docker compose`) | Docker Desktop 또는 Colima. **앱은 컨테이너에서 빌드되므로 로컬 JDK 는 없어도 된다** |
| Git | - | - |
| JDK 21 | Eclipse Temurin 권장 | IDE 에서 열거나 `./gradlew test` 를 돌릴 때만 필요 |

포트 `8080`(HTTPS) · `80`(HTTP) · `3306`(MySQL) · `9000`·`9001`(MinIO) 가 비어 있어야 한다. 점유돼 있으면 `.env.local` 에서 포트를 바꾼다.

---

## 2. 실행

```bash
git clone <repo> && cd server
./run.sh
```

최초 실행 시 `infra/env/.env.local.example` 이 `infra/env/.env.local` 로 자동 복사된다.
**그대로도 기동되지만**, `CHANGE_ME` 로 남아 있는 DB·MinIO 비밀번호는 바꿔 쓰는 편이 좋다. 소셜 로그인과 AI 피드백을 실제로 쓰려면 §4 의 키를 채운다.

```bash
./run.sh trust   # macOS. 브라우저 인증서 경고 제거 (sudo 필요)
```

Caddy 가 자체 서명 인증서로 HTTPS 를 종단하므로 등록 전에는 브라우저 경고가 뜬다. 앱·Postman 에서는 인증서 검증을 끄고 호출해도 된다.

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

`run.sh` 를 거치지 않고 직접 실행할 때는 compose 파일과 env 파일을 매번 지정해야 한다.

```bash
docker compose -f infra/docker/compose.yaml --env-file infra/env/.env.local ps
```

---

## 3. 접속 정보

| 대상 | 주소 | 계정 |
| --- | --- | --- |
| 애플리케이션 | **https://localhost:8080** (Caddy 종단) | - |
| HTTP 진입 | http://localhost → HTTPS 자동 리다이렉트 | - |
| 앱 컨테이너 직접 | http://127.0.0.1:8081 (디버깅용, 루프백 전용) | - |
| Swagger UI | https://localhost:8080/swagger-ui/index.html | - |
| 헬스체크 | https://localhost:8080/actuator/health | - |
| MySQL | localhost:3306 (`dearjolly`) | `MYSQL_USER` / `MYSQL_PASSWORD` |
| MinIO API | http://localhost:9000 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |
| MinIO 콘솔 | http://localhost:9001 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |

DB·이미지 데이터는 컨테이너가 아니라 호스트의 `dear-jolly/mount/{mysql,minio}` 에 쌓인다. 컨테이너를 지워도 남고, 지우려면 `./run.sh clean` 을 쓴다.

---

## 4. 환경변수 — `infra/env/.env.local`

모든 설정의 출발점은 이 파일 하나다. compose 가 `env_file` 로 통째로 앱 컨테이너에 주입하므로, 값을 추가해도 compose 를 고칠 필요가 없다.
값을 바꾼 뒤에는 `./run.sh restart` 로 앱을 다시 띄운다.

> 기본값과 주석은 [.env.local.example](../infra/env/.env.local.example) 에 있다. **`.env.local` 은 gitignore 대상이라 커밋되지 않는다.**

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
| `STAMP_SEED_ENABLED` | `true` | 기동 시 우표 시드 실행 여부. §5.1 참고 |

### 스키마 · 로깅

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `JPA_DDL_AUTO` | 앱 기본값 `validate` | §5 참고. **두 값은 항상 같이 움직인다** |
| `FLYWAY_ENABLED` | `true` | |
| `JPA_SHOW_SQL` | `true` | 실행 SQL 로그 |

### 인증 · 정책

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `JWT_SECRET` | 예시값 존재 | **HS256 시크릿. 256bit(32바이트) 이상.** 짧으면 `WeakKeyException` 으로 기동 실패 |
| `JWT_ACCESS_EXPIRE` · `JWT_REFRESH_EXPIRE` | 30분 · 14일 (ms) | 토큰 만료 |
| `TERMS_CURRENT_VERSION` | `1.0.0` | 현재 약관 버전. 동의 시점에 기록된다 |
| `WITHDRAWAL_RETENTION_DAYS` | `30` | 탈퇴 후 완전 삭제까지의 유예 (기능명세 R8) |

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

## 5. 스키마 관리

스키마의 정본은 [src/main/resources/db/migration](../src/main/resources/db/migration) 의 Flyway 마이그레이션이고, 데이터 모델 문서는 [ERD.md](./ERD.md) 다.

`JPA_DDL_AUTO` 와 `FLYWAY_ENABLED` 는 **스키마 소유자를 정하는 한 쌍**이라 항상 같이 움직인다.

| 상황 | `JPA_DDL_AUTO` | `FLYWAY_ENABLED` | 의미 |
| --- | --- | --- | --- |
| 기본 · 출시 | `validate` | `true` | Flyway 가 스키마를 소유하고, Hibernate 는 대조만 한다 |
| 출시 전 개발 중 | `update` | `false` | 엔티티 변경이 스키마에 바로 반영된다 |

> 현재 [.env.local.example](../infra/env/.env.local.example) 은 개발 편의를 위해 `JPA_DDL_AUTO=update` 로 배포되어 있다.
> 출시 시점에는 `validate` / `true` 조합으로 되돌린다.

`update` 는 컬럼을 **추가만** 하고 삭제·타입 축소는 하지 않는다. 엔티티에서 필드를 빼면 DB 에 죽은 컬럼이 남으므로, 그럴 때 한 번 비운다.

```bash
./run.sh reset-db    # 테이블 전체 삭제 후 재생성 (확인 프롬프트 있음)
```

`V1__init.sql` 을 수정했을 때도 checksum 불일치로 기동이 거부되므로 같은 명령을 쓴다.

### 5.1 초기 데이터 — 우표 시드

`STAMPS` 행과 우표 이미지는 손으로 넣지 않는다. 이미지 원본이 [src/main/resources/seed/stamps](../src/main/resources/seed/stamps) 에 들어 있고,
앱이 기동할 때 `global/seed/StampSeeder` 가 **MinIO 업로드 + DB 행 삽입**을 함께 처리한다. 로컬 `./run.sh` 든 운영 배포든 경로가 같다.

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

## 6. 테스트

```bash
./gradlew test                        # 전체
./gradlew test --tests '*AuthApiTest' # 단건
```

| 계층 | 방식 | 비고 |
| --- | --- | --- |
| API 통합 | RestAssured + `@SpringBootTest` | API 당 필수 (`.claude/skills/spring-test`) |
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

## 7. 자주 겪는 문제

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| Caddy 기동 실패 | 80/443 을 다른 프로세스가 점유 | `lsof -nP -iTCP:80 -sTCP:LISTEN` 로 확인 후 중지, 또는 `APP_HTTP_PORT` 변경 |
| 브라우저 인증서 경고 | Caddy 내장 CA(자체 서명) | `./run.sh trust` (macOS) |
| `WeakKeyException` 으로 기동 실패 | `JWT_SECRET` 이 32바이트 미만이거나 비어 있음 | `openssl rand -base64 48` 로 생성해 교체 |
| `Schema-validation: missing column` | 엔티티는 바뀌었는데 스키마가 그대로 | 마이그레이션 추가, 또는 개발 중이라면 §5 |
| 엔티티에서 지운 컬럼이 DB 에 남음 | `update` 는 컬럼을 삭제하지 않는다 | `./run.sh reset-db` |
| Flyway checksum 불일치 | 적용된 마이그레이션 파일을 수정 | `./run.sh reset-db` |
| 컨테이너를 지웠는데 옛 데이터가 남음 | 호스트 바인드 마운트라 `down -v` 로는 안 지워진다 | `./run.sh clean` |
| Testcontainers 가 Docker 를 못 찾음 | Docker 가 안 떠 있거나 소켓 경로가 자동 탐지 대상 밖 | `colima start` 후 재시도, 그래도 안 되면 §6 의 환경변수 |
| 컨테이너 재생성이 건너뛰어짐 | 스펙이 그대로면 Compose 가 재생성하지 않는다 | `./run.sh restart` (내부적으로 `--build`) |

---

## 8. 함께 볼 문서

| 문서 | 내용 |
| --- | --- |
| [기능명세.md](./기능명세.md) | 도메인 규칙 · 처리 흐름 |
| [API명세.md](./API명세.md) | 요청/응답 계약의 정본 |
| [ERD.md](./ERD.md) | 데이터 모델의 정본 |
| [infra/README.md](../infra/README.md) | 구축 · 배포 · 운영 |
