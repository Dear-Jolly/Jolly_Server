---
name: seed-data
description: 시드 데이터(우표·목 사용자)를 만들거나 고칠 때 사용. 시드 클래스를 어디에 두는지, 멱등성·기동 차단 금지·활성화 플래그·실행 순서 규칙, 목 사용자 시드와 로컬 토큰 발급 스크립트 사용법을 다룬다. "시드 추가", "테스트 데이터 넣어줘", "목 유저", "토큰 발급 스크립트" 요청에 해당.
---

# 시드 데이터 규칙

기동할 때 애플리케이션이 스스로 채워 넣는 데이터를 다룬다.
스키마는 Flyway 가 소유하고, **행(row)은 시더가 소유한다.** 마이그레이션 SQL 에 INSERT 를 넣지 않는다.

## 파일 배치

```
src/main/java/com/dearjolly/server/global/seed/
├── SeedOrder.java              # 시더 실행 순서 상수
├── {대상}Seed*.java            # 시드 한 건을 나타내는 record
├── {대상}SeedData.java         # 코드에 박아 두는 시드 원본 (필요할 때만)
├── {대상}SeedProperties.java   # dearjolly.seed.{대상} 설정
├── {대상}SeedWriter.java       # @Transactional DB 반영
└── {대상}Seeder.java           # ApplicationRunner. 켜짐 여부 판단 + 로깅
src/main/resources/seed/        # 코드에 담을 수 없는 원본 (우표 이미지 등)
infra/scripts-local/seed/       # 로컬에서만 돌리는 시드 보조 스크립트
```

- 시드는 도메인이 아니라 `global/seed` 에 모은다. 여러 도메인의 엔티티를 가로질러 만들기 때문이다.
- **Seeder 와 Writer 를 반드시 나눈다.** `ApplicationRunner` 는 트랜잭션 밖에서 돌기 때문에,
  DB 작업은 `@Transactional` 이 붙은 Writer 빈에 두어야 프록시를 타고 트랜잭션이 열린다.

## 시더가 지켜야 할 네 가지

### 1. 멱등성 — 몇 번을 돌려도 같은 상태로 수렴한다

블루그린 배포·재기동·`./run.sh restart` 마다 시더가 다시 돈다. 행이 불어나면 안 된다.

- 행마다 **자연키**를 정하고, 그 키로 먼저 찾은 뒤 없을 때만 만든다.
  - 우표: `STAMPS.name`
  - 목 사용자: `(oauth_provider, oauth_id)`
  - 목 사용자의 편지: `LETTERS.content`
- 이미 있는 행은 **필요한 필드만 갱신**한다 (예: 우표 이미지 키가 바뀌었을 때).
- 자연키를 바꾸면 예전 행이 남고 새 행이 하나 더 생긴다. 시드 원본을 고칠 때 이 점을 명시한다.

### 2. 실패해도 기동을 막지 않는다

`run()` 전체를 `try/catch` 로 감싸고 `log.error` 만 남긴다.
시드가 안 됐다고 서버가 못 뜨면 무중단 배포가 통째로 멈춘다.

```java
try {
    seed();
} catch (Exception e) {
    log.error("... 시드에 실패했다.", e);
}
```

전제가 안 갖춰진 경우(버킷 없음, 우표 없음)는 예외가 아니라 `log.warn` + 건너뛰기로 처리한다.

### 3. 켜고 끌 수 있다

`dearjolly.seed.{대상}.enabled` 를 `{대상}SeedProperties` 에 두고, 시더 첫 줄에서 확인한다.

- **운영에 있어도 되는 시드**(우표처럼 서비스 데이터)만 기본값 `true`.
- **테스트 편의용 시드**(목 사용자처럼 진짜 계정처럼 보이는 데이터)는 기본값 `false` 로 두고,
  `infra/env/.env.local.example` 에서만 켠다. `.env.prod.example` 에는 키를 넣지 않는다.
- 테스트가 시드에 영향받지 않도록 `src/test/resources/application.yaml` 에서 명시적으로 끈다.

### 4. 순서는 `SeedOrder` 로 고정한다

`ApplicationRunner` 는 여러 개면 순서가 정해져 있지 않다. 시더끼리 의존이 있으면
`@Order(SeedOrder.XXX)` 로 못 박고, 상수 옆에 왜 그 순서인지 적는다.

## 시드 원본을 어디에 둘지

| 원본 | 위치 | 이유 |
|---|---|---|
| 바이너리 (이미지 등) | `src/main/resources/seed/` | 코드에 담을 수 없다 |
| 텍스트 (편지 본문 등) | `{대상}SeedData.java` 의 `List` 상수 | 컴파일 시점에 깨지고, 어차피 재기동해야 반영된다 |
| 환경마다 달라지는 값 | `{대상}SeedProperties` | `.env` 로 덮어쓸 수 있어야 한다 |

## 목 사용자 시드

`MockUserSeeder` 가 소셜 로그인 없이 인증 API 를 두드릴 수 있는 계정 하나를 만든다.
약관 동의·닉네임 등록까지 끝난 상태라 온보딩 인터셉터를 그대로 통과한다.

- 편지는 `MockUserSeedData.LETTERS` 에 있다. 피드백이 붙은 편지는 우표·교정 조각·학습 팁까지
  완성된 상태로 들어가고, `correctedContent` 가 없는 편지는 `SUBMITTED` 로 남는다.
- 편지를 늘리거나 고칠 때는 `MockUserSeedDataTest` 가 편지 작성 API 의 제약
  (500자·영문·우표 존재·팁 3개)을 대신 검증한다. 새 제약이 생기면 이 테스트에 추가한다.
- 우표를 STAMPS 에서 찾아 붙이므로 `StampSeeder` 뒤에 돌아야 한다 (`SeedOrder.MOCK_USER`).

### 토큰 발급

```bash
./infra/scripts-local/seed/mock-token.sh            # 사람이 읽는 형식
eval $(./infra/scripts-local/seed/mock-token.sh --export)
./infra/scripts-local/seed/mock-token.sh --json     # jq 로 파싱
./infra/scripts-local/seed/mock-token.sh --user-id 3
```

`.env.local` 의 `JWT_SECRET` 으로 서버 `JwtProvider` 와 같은 HS256 토큰을 로컬에서 직접 서명하고,
Refresh Token 은 `USERS.refresh_token` 에도 기록해 `/api/v1/auth/reissue` 가 동작하게 한다.

**서버에 토큰 발급용 엔드포인트를 만들지 않는다.** 테스트 편의로 뚫은 구멍은 운영까지 따라간다.
같은 이유로 이 스크립트는 `infra/scripts-local/` 아래에 두고 컨테이너 이미지에 넣지 않는다.

## 하지 말 것

- 마이그레이션 SQL 에 `INSERT` 를 넣는 것 — 체크섬이 굳어 고칠 수 없게 된다.
- 시드에서 `deleteAll()` 후 다시 넣는 것 — 사용자가 만든 데이터를 지운다.
- 목 사용자 시드를 운영 환경 설정에 넣는 것.
- 실제 발급된 토큰·키·비밀번호를 시드 데이터나 예제 `.env` 에 적는 것.
