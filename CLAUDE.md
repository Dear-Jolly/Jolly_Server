# CLAUDE.md

Dear Jolly 백엔드 서버. Java 21 / Spring Boot 3.5.7 / Spring Data JPA / MySQL / Gradle.

모든 코드 컨벤션은 `.claude/skills/` 하위 스킬로 분리되어 있다.
**이 문서는 "언제 어떤 스킬을 쓸지"만 규정한다. 규칙 자체는 각 스킬 문서에 있다.**

## 스킬 라우팅

| 작업 상황 | 사용할 스킬 |
|---|---|
| 새 도메인/패키지 추가, 파일 배치 위치 결정, 선언 순서·네이밍 판단 | `spring-architecture` |
| REST API 엔드포인트 작성·수정 | `spring-controller` |
| 비즈니스 로직 작성·수정, 트랜잭션 처리 | `spring-service` |
| JPA 엔티티 생성·수정, 컬럼/연관관계 매핑 | `spring-entity` |
| Request/Response DTO 작성, 입력값 검증 추가 | `spring-dto` |
| 예외 던지기, 새 에러 코드 추가, 에러 응답 포맷 | `spring-exception` |
| 테스트 코드 작성 | `spring-test` |
| 코드 리뷰, PR 점검, 컨벤션 준수 확인 | `spring-code-review` |

## 적용 규칙

- **작업 전에 해당 스킬을 먼저 읽는다.** 기억에 의존해 컨벤션을 추측하지 않는다.
- **하나의 작업에 여러 스킬이 걸리면 모두 적용한다.** 예: API 하나를 추가하면
  `spring-controller` + `spring-service` + `spring-dto` + `spring-test`를 함께 본다.
- **API를 추가하거나 수정했다면 `spring-test`는 항상 함께 적용한다.** 통합 테스트는 API당 필수다.
- 엔티티를 건드렸다면 `docs/ERD.md`, API를 건드렸다면 `docs/API명세.md`의 갱신이 필요한지 확인한다.

## 프로젝트 참고 문서

- `docs/API명세.md` — API 명세
- `docs/기능명세.md` — 기능 명세
- `docs/ERD.md` — 데이터베이스 ERD
