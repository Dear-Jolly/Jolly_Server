---
name: seed-data
description: 시드 데이터(우표·앱 최소 지원 버전·관리자 시드 User)를 만들거나 고칠 때 사용. 시드 클래스를 어디에 두는지, Seeder/Writer 분리 이유, 멱등성·기동 차단 금지·활성화 플래그·실행 순서 규칙, 시드 원본을 어디에 둘지를 다룬다. "시드 추가", "초기 데이터", "기본값 채우기" 요청에 해당.
---

# 시드 데이터 규칙

기동할 때 애플리케이션이 스스로 채워 넣는 데이터를 다룬다.
스키마는 Flyway 가 소유하고, **행(row)은 시더가 소유한다.** 마이그레이션 SQL 에 INSERT 를 넣지 않는다.

## 파일 배치

```
src/main/java/com/dearjolly/server/global/seed/
├── {대상}Seed*.java            # 시드 한 건을 나타내는 record (필요할 때만)
├── {대상}SeedData.java         # 코드에 박아 두는 시드 원본 (필요할 때만)
├── {대상}SeedProperties.java   # dearjolly.seed.{대상} 설정
├── {대상}SeedWriter.java       # @Transactional DB 반영
└── {대상}Seeder.java           # ApplicationRunner. 켜짐 여부 판단 + 로깅
src/main/resources/seed/        # 코드에 담을 수 없는 원본 (우표 이미지 등)
```

현재 시더는 셋이다.

| 시더 | 채우는 것 | 자연키 |
|---|---|---|
| `StampSeeder` | `STAMPS` 행 + MinIO 우표 이미지 | `STAMPS.name` |
| `AppVersionSeeder` | `APP_VERSIONS` 행 (플랫폼별 최소 지원 버전) | `APP_VERSIONS.platform` |
| `UserSeeder` | 관리자 권한의 시드 User + 약관 동의 | `(oauth_provider, oauth_id)` |

- 시드는 도메인이 아니라 `global/seed` 에 모은다. 여러 도메인의 엔티티를 가로질러 만들기 때문이다.
- **Seeder 와 Writer 를 반드시 나눈다.** `ApplicationRunner` 는 트랜잭션 밖에서 돌기 때문에,
  DB 작업은 `@Transactional` 이 붙은 Writer 빈에 두어야 프록시를 타고 트랜잭션이 열린다.

## 시더가 지켜야 할 네 가지

### 1. 멱등성 — 몇 번을 돌려도 같은 상태로 수렴한다

블루그린 배포·재기동·`./run.sh restart` 마다 시더가 다시 돈다. 행이 불어나면 안 된다.

- 행마다 **자연키**를 정하고, 그 키로 먼저 찾은 뒤 없을 때만 만든다.
  - 우표: `STAMPS.name`
  - 앱 버전: `APP_VERSIONS.platform`
  - 시드 User: `(oauth_provider, oauth_id)`
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

- **운영에 있어도 되는 시드**(우표·앱 버전처럼 서비스가 돌아가는 데 필요한 데이터)만 기본값 `true`.
- **진짜 계정처럼 보이는 행을 만드는 시드**(관리자 시드 User)는 기본값을 `false` 로 두고 켤 환경에서만 켠다.
- 통합 테스트는 각자 필요한 데이터만 만든다. `src/test/resources/application.yaml` 에서 전부 끈다.

### 4. 시더끼리 의존하면 순서를 못 박는다

`ApplicationRunner` 가 여러 개면 실행 순서가 정해져 있지 않다. 한 시더가 다른 시더의 결과를 읽어야 하면
`@Order` 로 고정하고 **왜 그 순서인지 주석으로 남긴다.** 의존이 없으면 붙이지 않는다.

현재 시더끼리는 서로의 결과를 읽지 않으므로 실행 순서를 지정하지 않는다.

## 시드 원본을 어디에 둘지

| 원본 | 위치 | 이유 |
|---|---|---|
| 바이너리 (이미지 등) | `src/main/resources/seed/` | 코드에 담을 수 없다 |
| 텍스트 목록 | `{대상}SeedData.java` 의 `List` 상수 | 컴파일 시점에 깨지고, 어차피 재기동해야 반영된다 |
| 환경마다 달라지는 값 | `{대상}SeedProperties` | `.env` 로 덮어쓸 수 있어야 한다 |

## 값의 주인이 따로 있는 시드

`APP_VERSIONS` 처럼 **런타임에 API 로 바뀌는 값**은 시드가 "빈 자리만 채우는" 역할이어야 한다.

- 행이 없을 때만 만들고, **이미 있는 행은 절대 덮어쓰지 않는다.**
- 덮어쓰면 관리자가 올려 둔 값이 재기동·블루그린 배포마다 기본값으로 되돌아간다.
- 기본값은 코드 상수(`VersionValidationConstants.DEFAULT_MIN_SUPPORTED_VERSION`)로 두고 설정에서 읽지 않는다.
  설정에서 읽으면 "설정값과 실제 값 중 무엇이 정본인가"가 흐려진다.

```java
if (appVersionRepository.existsById(platform)) {
    continue;
}
appVersionRepository.save(AppVersions.create(platform, DEFAULT_MIN_SUPPORTED_VERSION));
```

## 관리자 계정은 시드가 만든다

관리자는 **별도 테이블이 아니라 `USERS` 행 하나**다. 일반 사용자와 같은 행이고 `role` 만 `ROLE_ADMIN` 이다.

- 소셜 회원가입을 거치지 않으므로 시드가 만든다. `Users.createAdmin(…)` 을 쓴다.
- **관리자 로그인 API 는 회원가입을 하지 않는다.** 아이디·비밀번호를 설정값과 대조한 뒤,
  시드가 만들어 둔 계정을 찾아 토큰만 발급한다. 계정이 없으면 로그인도 실패한다.
- 발급되는 토큰은 소셜 로그인 토큰과 형식·수명이 같다. 그래서 편지·홈 API 까지 같은 토큰으로 호출된다.
- 온보딩 가드를 통과해야 쓸모가 있으므로, 약관 동의와 닉네임까지 채운 상태로 만든다.
- 사용자 편지는 시드하지 않는다. 편지 API 는 인증된 사용자가 직접 작성해 저장한 데이터만 조회한다.

## 하지 말 것

- 마이그레이션 SQL 에 `INSERT` 를 넣는 것 — 체크섬이 굳어 고칠 수 없게 된다.
- 시드에서 `deleteAll()` 후 다시 넣는 것 — 사용자가 만든 데이터를 지운다.
- API 로 바꿀 수 있는 값을 시드가 매 기동마다 덮어쓰는 것.
- 실제 발급된 토큰·키·비밀번호를 시드 데이터나 예제 `.env` 에 적는 것.
- 테스트 편의를 위해 서버에 **인증을 우회하는** 엔드포인트를 만드는 것 — 그 구멍은 운영까지 따라간다.
  아이디·비밀번호로 **인증을 거치는** 관리자 로그인은 이에 해당하지 않는다.
