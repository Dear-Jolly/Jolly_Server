# jolly_server

Write to Jolly, feel jolly

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 언어 · 런타임 | Java 21 (Eclipse Temurin) |
| 프레임워크 | Spring Boot 3.5.7 (Web, Data JPA, Validation) |
| 데이터베이스 | MySQL 8.4 (`utf8mb4` / `Asia/Seoul`) |
| 오브젝트 스토리지 | MinIO (우표 이미지, S3 호환) |
| 빌드 | Gradle 8.14.3 (Wrapper) |
| 실행 환경 | Docker · Docker Compose |

## 실행 방법

| 명령 | 설명 |
| --- | --- |
| `./run.sh` | 앱 + MySQL + MinIO 기동 (이미지 재빌드 포함) |
| `./run.sh logs` | 앱 로그 실시간 확인 |
| `./run.sh ps` | 컨테이너 상태 확인 |
| `./run.sh restart` | 앱 컨테이너만 재빌드 후 재기동 |
| `./run.sh down` | 컨테이너 종료 (데이터 유지) |
| `./run.sh clean` | 컨테이너 + 볼륨 삭제 (DB·이미지 데이터 초기화) |

> 최초 실행 시 `.env.example` 이 `.env` 로 자동 복사된다. 포트·계정 정보는 `.env` 에서 수정한다.

## 접속 정보

| 대상 | 주소 | 계정 |
| --- | --- | --- |
| 애플리케이션 | http://localhost:8080 | - |
| MySQL | localhost:3306 (`dearjolly`) | `MYSQL_USER` / `MYSQL_PASSWORD` |
| MinIO API | http://localhost:9000 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |
| MinIO 콘솔 | http://localhost:9001 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |

## 문서

| 문서 | 내용 |
| --- | --- |
| [docs/기능명세.md](docs/기능명세.md) | 기능 명세 |
| [docs/API명세.md](docs/API명세.md) | API 명세 |
| [docs/ERD.md](docs/ERD.md) | 데이터 모델 |
