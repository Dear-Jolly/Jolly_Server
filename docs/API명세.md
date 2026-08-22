# Dear Jolly — API 명세서 (MVP)

> 이 문서는 **요청/응답 계약의 정본**이다. 각 API 의 `도메인 규칙` 항목은 앱·서버가 함께 지켜야 하는 조건을 요약한 것이고, 전체 규칙과 처리 흐름은 [기능명세.md](./기능명세.md), 테이블·제약 조건은 [ERD.md](./ERD.md) 를 따른다.

| 항목 | 내용 |
| --- | --- |
| 최종 갱신 | 2026-08-22 |
| Base URL | `https://{host}` |
| 인증 | `Authorization: Bearer {accessToken}` |
| Content-Type | `application/json; charset=UTF-8` |
| 날짜 포맷 | `LocalDate` → `yyyy-MM-dd` / `LocalDateTime` → `yyyy-MM-dd'T'HH:mm:ss` |

---

## 1. 엔드포인트 목록

| # | 패키지 | Method | 엔드포인트 | 설명 | 인증 | 온보딩 |
| --- | --- | --- | --- | --- | :---: | :---: |
| 1 | user | `POST` | `/api/v1/auth/login` | 소셜 로그인 (회원가입 포함) | — | — |
| 2 | user | `POST` | `/api/v1/auth/reissue` | 토큰 재발급 | — | — |
| 3 | user | `POST` | `/api/v1/auth/logout` | 로그아웃 | ✔ | — |
| 4 | user | `POST` | `/api/v1/users/terms` | 약관 동의 · 마케팅 동의 철회 | ✔ | — |
| 5 | user | `GET` | `/api/v1/users` | 계정 정보 조회 | ✔ | — |
| 6 | user | `DELETE` | `/api/v1/users` | 회원 탈퇴 | ✔ | — |
| 7 | user | `PATCH` | `/api/v1/users/nickname` | 닉네임 설정 | ✔ | — |
| 8 | letter | `POST` | `/api/v1/letters` | 편지 작성 + 피드백 요청 | ✔ | ✔ |
| 9 | letter | `GET` | `/api/v1/letters/{letterId}` | 편지 및 피드백 상세 조회 | ✔ | ✔ |
| 10 | letter | `GET` | `/api/v1/letters` | 전체 편지 리스트 조회 | ✔ | ✔ |
| 11 | letter | `GET` | `/api/v1/home` | 닉네임, 모은 우표 수 조회 | ✔ | ✔ |
| 12 | global | `GET` | `/api/v1/version` | 최소 지원 버전 조회 | — | — |

- **패키지** 열은 서버 구현 위치다. `version` 은 도메인이 아니라 `global` 하위이며, `/api/v1/home` 은 편지 집계가 본질이므로 `letter` 도메인에 둔다 ([기능명세 §2.1](./기능명세.md#21-패키지-구조)).
- **온보딩** 열이 ✔ 인 API 는 온보딩 미완료 시 `USER_005` 로 차단된다 (§2.6).

---

## 2. 공통 규약

### 2.1 인증

| 항목 | 값 |
| --- | --- |
| 헤더 | `Authorization: Bearer {accessToken}` |
| Access Token 만료 | 30분 |
| Refresh Token 만료 | 14일 |
| 인증 불필요 | `POST /api/v1/auth/login`, `POST /api/v1/auth/reissue`, `GET /api/v1/version` |

- Refresh Token 은 재발급 시 **회전(rotate)** 되며, 서버에 저장된 값과 다르면 `AUTH_004` 로 거절한다.
- 인증 필터는 토큰 서명·만료를 검증한 뒤 **계정 상태까지 확인**한다. 탈퇴 처리된 계정의 토큰은 `AUTH_007`(401) 로 거절한다. 탈퇴 직후 최대 30분간 유효한 Access Token 이 남아 있기 때문이다.

### 2.2 성공 응답

별도 래퍼 없이 DTO 를 그대로 반환한다.

| 상황 | 상태 코드 |
| --- | --- |
| 조회 · 수정 성공 | `200 OK` |
| 생성 성공 | `201 Created` |
| 처리 성공, 본문 없음 | `204 No Content` |

**예외 2건**

| API | 코드 | 이유 |
| --- | --- | --- |
| `POST /api/v1/auth/login` | `200 OK` | 신규 가입을 함께 처리하지만 로그인/가입이 단일 엔드포인트이며, 앱은 `isNewUser` 로 구분한다. 같은 요청이 상황에 따라 200/201 을 오가지 않도록 200 으로 고정한다 |
| `POST /api/v1/letters` (중복) | `200 OK` | 60초 내 동일 본문 재요청은 새 편지를 만들지 않고 최초 편지를 반환하므로 생성이 아니다 (§4.1) |

### 2.3 에러 응답

```json
{
    "status": 400,
    "code": "LETTER_001",
    "message": "편지 내용은 null일 수 없습니다."
}
```

- [필수] `status` (Integer): HTTP 상태 코드
- [필수] `code` (String): `{도메인}_{일련번호}` 형식 (`AUTH_`, `USER_`, `LETTER_`, `COMMON_`)
- [필수] `message` (String): 사용자 노출 가능한 에러 메시지

**공통 핸들러가 생산하는 코드**

아래 코드는 개별 엔드포인트에 매핑되지 않고 `GlobalExceptionHandler` · Security 계층에서 공통으로 발생한다. 각 API 의 실패 응답 표에는 다시 적지 않는다.

| code | status | 발생 지점 |
| --- | --- | --- |
| `AUTH_005` | 401 | 토큰 누락 · 서명 위조 · 만료 (`AuthenticationEntryPoint`) |
| `AUTH_006` | 403 | 인증은 됐으나 권한이 부족 (`AccessDeniedHandler`). MVP 에 관리자 API 가 없어 실제로는 발생하지 않는다 |
| `AUTH_007` | 401 | 탈퇴 처리된 계정의 토큰 (인증 필터) |
| `COMMON_001` | 400 | 요청 바디 파싱 실패, 타입 불일치, 쿼리 파라미터 제약 위반 |
| `COMMON_002` | 404 | 존재하지 않는 경로 |
| `COMMON_003` | 405 | 지원하지 않는 HTTP 메서드 |
| `COMMON_004` | 429 | Rate limit 초과 ([기능명세 §4.2](./기능명세.md#42-성능--제한)) |
| `COMMON_005` | 500 | 처리되지 않은 서버 오류 |

전체 목록은 [7. 에러 코드](#7-에러-코드) 참고.

### 2.4 시간 · 타임존

| 값 | 기준 |
| --- | --- |
| 편지 작성 시각 (`writtenAt` + `timeZone`) | **클라이언트가 명시**한다 |
| 편지 날짜 (`date`) | `writtenAt` 을 `timeZone` 기준으로 환산한 날짜 |
| 서버 생성 시각 (`createdAt`) | 서버 저장 시각을 요청의 `timeZone` 기준으로 변환해 반환 |
| 그 외 모든 서버 시각 | `Asia/Seoul` (KST) |

편지 날짜에 KST 를 강제하지 않는다. 해외에서 작성한 편지는 현지 날짜로 기록된다.

### 2.5 페이징

| 파라미터 | 기본값 | 제약 |
| --- | --- | --- |
| `page` | 0 | 0 이상 |
| `size` | 10 | 1 ~ 50 |
| `sort` | `LATEST` | `LATEST` / `OLDEST` |

제약을 벗어난 값은 `COMMON_001`(400) 이다.

### 2.6 온보딩 가드

온보딩(필수 약관 동의 + 닉네임 등록)을 마치지 않은 유저는 **본 기능 API 를 호출할 수 없다** (R11).

| 항목 | 내용 |
| --- | --- |
| 판별 | `SERVICE` · `PRIVACY` 의 최신 동의 이력이 모두 `agreed = true` **그리고** 닉네임이 등록됨 |
| 차단 대상 | §1 목록의 **온보딩** 열이 ✔ 인 API (`/api/v1/letters` 3종, `/api/v1/home`) |
| 위반 시 | `USER_005` (400) |

- 이 가드 덕분에 편지 목록·홈 응답의 `nickname` 은 **항상 non-null** 이다.
- 정상 앱 플로우에서는 온보딩 화면을 건너뛸 수 없으므로 이 코드는 거의 나가지 않는다. 앱을 우회한 직접 호출에 대한 방어선이다.

---

## 3. user

### 3.1 `POST /api/v1/auth/login`

> **API 설명**
> - 카카오 / 애플 소셜 로그인을 수행합니다.
> - 가입되지 않은 유저라면 회원가입을 함께 처리합니다.
>
> **도메인 규칙**
> - 유저는 `(provider, provider 회원 식별자)` 조합으로 식별한다. 같은 이메일이라도 카카오/애플은 별개 계정이다.
> - 가입 직후에는 약관 동의 이력이 없고 닉네임이 `null` 이다. 앱은 응답의 온보딩 상태로 진입 화면을 결정한다.
> - Refresh Token 은 **유저당 1개만 유지**한다(단일 세션). 새로 로그인하면 이전 토큰은 무효가 된다.
> - **탈퇴한 계정으로 다시 로그인하면 항상 신규 가입**이다 (`isNewUser: true`). 이전 편지는 복원되지 않으며 온보딩을 처음부터 다시 거친다.
> - 회원가입을 포함하지만 응답은 `201` 이 아니라 `200` 이다 (§2.2).

#### Request

**Endpoint**

```
/api/v1/auth/login
```

**Authorization 헤더**: 불필요

**Body**

```json
{
    "provider": "KAKAO",
    "token": "IwGRZ8bLXsC0hR..."
}
```

- [필수] `provider` (String): 로그인 수단 (`KAKAO`, `APPLE`)
- [필수] `token` (String): 소셜 인증 토큰
    - `KAKAO`: 앱에서 발급받은 access token
    - `APPLE`: identity token (JWT)

#### `성공` Response 200

```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1,
    "isNewUser": true,
    "termsAgreed": false,
    "nicknameRegistered": false
}
```

- [필수] `accessToken` (String): 액세스 토큰
- [필수] `refreshToken` (String): 리프레시 토큰
- [필수] `userId` (Long): 유저 ID
- [필수] `isNewUser` (Boolean): 이번 요청으로 가입된 유저인지 여부
- [필수] `termsAgreed` (Boolean): 필수 약관(`SERVICE` · `PRIVACY`) 동의 완료 여부
- [필수] `nicknameRegistered` (Boolean): 닉네임 등록 여부

**앱 분기**

| 조건 | 이동 화면 |
| --- | --- |
| `termsAgreed == false` | 온보딩 – 약관동의 |
| `termsAgreed == true && nicknameRegistered == false` | 온보딩 – 닉네임 |
| 둘 다 `true` | 홈 |

#### `실패` Error Response

```json
{
    "status": 401,
    "code": "AUTH_002",
    "message": "소셜 로그인 인증에 실패했습니다."
}
```

| code | status | 상황 |
| --- | --- | --- |
| `AUTH_001` | 400 | 지원하지 않는 `provider` |
| `AUTH_002` | 401 | 소셜 토큰 검증 실패 |
| `AUTH_003` | 502 | 카카오 / 애플 서버 통신 실패 |

---

### 3.2 `POST /api/v1/auth/reissue`

> **API 설명**
> - Refresh Token 으로 Access Token 을 재발급합니다.
> - Refresh Token 도 함께 재발급(회전)되며, 이전 토큰은 즉시 무효화됩니다.
>
> **도메인 규칙**
> - Access Token 만료는 30분, Refresh Token 만료는 14일이다.
> - 전달받은 Refresh Token 이 **서버에 저장된 값과 문자열까지 일치**해야 한다. 불일치하면 탈취된 이전 토큰의 재사용으로 보고 거절한다.
> - 만료된 Access Token 으로도 호출할 수 있도록 인증 필터에서 제외한다.
> - 탈퇴 처리된 계정의 Refresh Token 은 탈퇴 시점에 `null` 이 되므로 `AUTH_004` 로 거절된다.

#### Request

**Endpoint**

```
/api/v1/auth/reissue
```

**Authorization 헤더**: 불필요 (만료된 Access Token 으로도 호출 가능)

**Body**

```json
{
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

- [필수] `refreshToken` (String): 로그인 시 발급받은 리프레시 토큰

#### `성공` Response 200

```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

- [필수] `accessToken` (String): 새 액세스 토큰
- [필수] `refreshToken` (String): 새 리프레시 토큰 (앱은 기존 값을 이 값으로 교체 저장)

#### `실패` Error Response

```json
{
    "status": 401,
    "code": "AUTH_004",
    "message": "로그인이 만료되었습니다. 다시 로그인해주세요."
}
```

| code | status | 상황 |
| --- | --- | --- |
| `AUTH_004` | 401 | Refresh Token 만료 · 위조 · 서버 저장값과 불일치 |

- 앱은 `AUTH_004` 를 받으면 저장된 토큰을 폐기하고 로그인 화면으로 이동한다.

---

### 3.3 `POST /api/v1/auth/logout`

> **API 설명**
> - 서버에 저장된 Refresh Token 을 무효화합니다.
> - 카카오 세션 종료 등 소셜 로그아웃은 앱 SDK 에서 처리합니다.
>
> **도메인 규칙**
> - 로그아웃은 **세션만 끊는다.** 편지·계정 데이터는 그대로 보존된다 (디자인 문구: *"편지는 잘 보관해 둘게요. 언제든 다시 와요!"*).
> - 계정과 데이터를 지우는 것은 회원 탈퇴(§3.6)뿐이다.
> - 온보딩 미완료 상태에서도 호출할 수 있다.

#### Request

**Endpoint**

```
/api/v1/auth/logout
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Body**: 없음

#### `성공` Response 204

```
(No Content)
```

#### `실패` Error Response

공통 인증 실패 코드(`AUTH_005` · `AUTH_007`)만 발생한다. §2.3 참고.

---

### 3.4 `POST /api/v1/users/terms`

> **API 설명**
> - 온보딩 약관 동의 내역을 저장합니다.
> - 설정 화면에서 마케팅 동의를 철회할 때도 같은 API 를 사용합니다.
> - 필수 약관(`SERVICE`, `PRIVACY`)에 모두 동의해야 통과합니다.
>
> **도메인 규칙**
> - `MARKETING` 은 선택 항목이며, 동의하지 않아도 서비스 이용에 제한이 없다.
> - 동의 내역은 **덮어쓰지 않고 이력으로 누적**한다. 요청에 담긴 항목마다 새 행이 쌓이며, 현재 상태는 항목별 최신 행이다 ([ERD §2.2](./ERD.md#22-terms_agreements--약관-동의-이력)).
> - 동의 시각은 **서버 시각(KST)** 으로 기록한다. 동의 시점의 약관 버전도 함께 기록한다.
> - **약관이 개정돼도 재동의를 요구하지 않는다.** 버전은 사후 입증용 기록이며 `termsAgreed` 판별에 쓰지 않는다.
> - 요청 바디에 포함하지 않은 항목은 건드리지 않는다. 마케팅만 철회하려면 `MARKETING` 한 건만 보내면 된다.
> - 약관 본문은 서버가 제공하지 않는다. 웹뷰 링크(§5.1)로 처리한다.

#### Request

**Endpoint**

```
/api/v1/users/terms
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Body**

```json
{
    "agreements": [
        { "type": "SERVICE",   "agreed": true },
        { "type": "PRIVACY",   "agreed": true },
        { "type": "MARKETING", "agreed": false }
    ]
}
```

- [필수] `agreements` (Array): 약관 동의 목록 (1개 이상)
    - [필수] `type` (String): 약관 종류 (`SERVICE`, `PRIVACY`, `MARKETING`)
    - [필수] `agreed` (Boolean): 동의 여부

**마케팅 철회 예시**

```json
{
    "agreements": [
        { "type": "MARKETING", "agreed": false }
    ]
}
```

#### `성공` Response 200

```json
{
    "termsAgreed": true
}
```

- [필수] `termsAgreed` (Boolean): 이 요청 반영 후의 필수 약관 동의 완료 여부

#### `실패` Error Response

```json
{
    "status": 400,
    "code": "USER_002",
    "message": "필수 약관에 모두 동의해야 합니다."
}
```

| code | status | 상황 |
| --- | --- | --- |
| `USER_002` | 400 | 이 요청 반영 후에도 `SERVICE` 또는 `PRIVACY` 가 미동의 상태 |
| `USER_001` | 404 | 사용자를 찾을 수 없음 |

---

### 3.5 `GET /api/v1/users`

> **API 설명**
> - 설정 화면에 표시할 계정 정보를 조회합니다.
>
> **도메인 규칙**
> - 이메일은 Apple private relay 거부 등으로 제공되지 않을 수 있다. 이 경우 `null` 이며 앱은 provider 명만 표시한다. **서버가 대체 주소를 지어내지 않는다.**
> - 온보딩을 마치지 않은 유저는 `nickname` 이 `null` 이다. 이 API 는 온보딩 가드 대상이 아니므로 그 상태로도 호출된다.
> - `marketingAgreed` 는 `MARKETING` 의 **최신 동의 이력**의 값이다. 이력이 없으면 `false` 다.

#### Request

**Endpoint**

```
/api/v1/users
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

#### `성공` Response 200

```json
{
    "nickname": "ilovesally",
    "provider": "KAKAO",
    "email": "kakao_user@email.com",
    "marketingAgreed": true
}
```

- [선택] `nickname` (String): 유저 닉네임 (**온보딩 전에는 `null`**)
- [필수] `provider` (String): 로그인 수단 (`KAKAO`, `APPLE`) — 앱에서 아이콘 매핑
- [선택] `email` (String): 소셜 계정 이메일 (provider 미제공 시 `null`)
- [필수] `marketingAgreed` (Boolean): 마케팅 수신 동의 여부

#### `실패` Error Response

```json
{
    "status": 404,
    "code": "USER_001",
    "message": "사용자를 찾을 수 없습니다."
}
```

---

### 3.6 `DELETE /api/v1/users`

> **API 설명**
> - 회원 탈퇴를 처리합니다.
> - 모든 편지와 계정 정보가 삭제되며 복구할 수 없습니다.
>
> **도메인 규칙**
> - **사용자 관점에서 삭제는 되돌릴 수 없다.** 편지·피드백·교정 내역이 모두 사라진다 (디자인 문구: *"탈퇴하면 모든 편지와 계정 정보가 함께 삭제되며 다시 복구할 수 없어요."*).
> - **서버는 즉시 접근을 차단한 뒤 30일간 데이터를 보존하고, 유예기간이 지나면 완전 삭제한다.** 오삭제 복구와 분쟁 대응을 위한 내부 보존이며, 사용자에게 노출되는 복구 수단은 없다. 이 사실은 개인정보처리방침의 보유기간 항목에 명시한다.
> - 탈퇴 즉시 Refresh Token 이 무효화되고, 남아 있는 Access Token 도 `AUTH_007`(401) 로 거절된다.
> - 탈퇴 후 같은 소셜 계정으로 다시 로그인하면 **신규 가입**으로 처리된다. 이전 편지는 복원되지 않는다.
> - Apple 유저는 **토큰 revoke 가 필수**다. 누락 시 App Store 심사 리젝 사유가 된다.
> - 소셜 연결 해제(unlink / revoke)에 실패해도 **탈퇴 처리는 계속 진행**한다. 사용자가 탈퇴하지 못하는 상태에 갇히지 않게 하기 위함이다.
> - 온보딩 미완료 상태에서도 탈퇴할 수 있다.

#### Request

**Endpoint**

```
/api/v1/users
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Body**

```json
{
    "authorizationCode": "c1a2b3d4e5..."
}
```

- [선택] `authorizationCode` (String): Apple 토큰 revoke 용 인가 코드
    - **Apple 로그인 유저는 필수.** App Store 심사 요건(연동 해제)에 해당한다.
    - Kakao 유저는 생략한다.

#### `성공` Response 204

```
(No Content)
```

**처리 순서**

1. 소셜 연결 해제 (Kakao `unlink` / Apple `revoke`) — **트랜잭션 밖에서** 수행하며, 실패해도 로그만 남기고 탈퇴는 계속 진행
2. 계정을 탈퇴 상태로 전환하고 Refresh Token 을 무효화 (단일 트랜잭션)
3. 유예기간(30일) 경과 후 배치가 유저 행을 삭제하면 약관 이력·편지·피드백·교정 조각·팁이 cascade 로 함께 제거된다 ([ERD §3.3](./ERD.md#33-삭제-전파))

> 삭제 순서를 서버 코드가 지정하지 않는다. 유저 엔티티 하나를 삭제하면 JPA cascade 가 전 구간을 연쇄 삭제한다.

#### `실패` Error Response

```json
{
    "status": 404,
    "code": "USER_001",
    "message": "사용자를 찾을 수 없습니다."
}
```

---

### 3.7 `PATCH /api/v1/users/nickname`

> **API 설명**
> - 닉네임을 등록하거나 변경합니다.
> - 온보딩(이름 입력)과 설정(이름 변경)이 동일한 API 를 사용합니다.
>
> **도메인 규칙**
> - 닉네임은 Jolly 에게 보여줄 **표시용 이름**이므로 **중복을 허용**한다. 유니크 제약이 없어 중복 에러도 없다.
> - 영문·숫자 1~20자만 허용한다. 길이는 **문자 수** 기준으로 세며 앱의 `10/20` 카운터와 일치한다.
> - **검증은 길이 → 문자 순서로 수행한다.** 두 조건을 동시에 어겨도 먼저 걸린 사유 하나만 반환한다. 앱이 사유별로 다른 문구를 보여줘야 하기 때문이다.
> - 변경 횟수에 제한이 없고, 이전 닉네임은 보관하지 않는다.

#### Request

**Endpoint**

```
/api/v1/users/nickname
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Body**

```json
{
    "nickname": "iloveJolly"
}
```

- [필수] `nickname` (String): 변경할 닉네임 (영문 + 숫자, 1~20자)

**검증 규칙**

| 순서 | 규칙 | 값 | code |
| --- | --- | --- | --- |
| 1 | 길이 | 1 ~ 20자 (문자 수) | `USER_004` |
| 2 | 허용 문자 | `^[A-Za-z0-9]+$` | `USER_003` |
| — | 공백 · 특수기호 · 한글 | 불가 | `USER_003` |
| — | 중복 | **허용** (표시용 이름) | — |

- 정규식에 길이 수량자(`{1,20}`)를 넣지 않는다. 넣으면 길이 위반과 문자 위반이 같은 코드로 뭉개진다.
- 21자 영문을 보내면 `USER_004`, 5자 한글을 보내면 `USER_003`, 21자 한글을 보내면 `USER_004` 다.
- 앱은 사유별 문구(`공백을 포함할 수 없어요` / `특수 기호를 포함할 수 없어요` / `한글을 포함할 수 없어요`)를 클라이언트에서 판별해 표시한다. 서버는 최종 방어선이다.

#### `성공` Response 200

```json
{
    "nickname": "iloveJolly"
}
```

- [필수] `nickname` (String): 변경된 닉네임

#### `실패` Error Response

```json
{
    "status": 400,
    "code": "USER_003",
    "message": "닉네임은 공백, 특수기호, 한글 없이 작성해야 합니다."
}
```

| code | status | 상황 |
| --- | --- | --- |
| `USER_003` | 400 | 공백 · 특수기호 · 한글 포함 |
| `USER_004` | 400 | 1자 미만 또는 20자 초과 |
| `USER_001` | 404 | 사용자를 찾을 수 없음 |

---

## 4. letter

### 4.1 `POST /api/v1/letters`

> **API 설명**
> - 새로운 편지를 작성합니다.
> - 편지가 저장되면 AI 피드백 프로세스가 트리거됩니다.
> - 피드백은 비동기로 처리되며, 이 API 는 결과를 기다리지 않고 즉시 반환합니다.
>
> **도메인 규칙**
> - 편지는 **전달 후 수정·삭제할 수 없다.** 수정·삭제 API 자체를 제공하지 않는다 (디자인 문구: *"전달된 편지는 다시 고칠 수 없어요."*).
> - 본문은 **영어 전용, 1~500자**다. 한글이 섞이면 거부한다. 숫자·구두점·이모지는 허용한다.
> - 편지 날짜는 **클라이언트가 보낸 작성 시각 + 타임존**으로 결정한다. 작성 화면의 `DATE:` 는 변경할 수 없는 UI 이므로 화면 표시와 항상 일치하며, 서버는 임의 날짜 조작만 차단한다.
> - **하루 작성 개수 제한이 없다.** 같은 날짜에 여러 통을 쓰면 목록에 같은 날짜 카드가 여러 개 쌓인다.
> - 생성 직후 상태는 항상 `SUBMITTED` 이고, 우표는 **아직 부여되지 않는다.** 피드백이 완료돼야 우표가 도착한다.
> - **중복 전달은 서버가 막는다.** 동일 유저가 60초 이내에 같은 본문을 다시 보내면 새 편지를 만들지 않고 최초 편지를 `200 OK` 로 반환한다. 앱이 별도 헤더를 보낼 필요가 없다.

#### Request

**Endpoint**

```
/api/v1/letters
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Body**

```json
{
    "content": "I got flowers from a friend today...",
    "writtenAt": "2025-11-01T21:00:00",
    "timeZone": "Asia/Seoul"
}
```

- [필수] `content` (String): 편지 내용 (최대 500자)
- [필수] `writtenAt` (LocalDateTime): **기기 로컬 기준** 작성 시각 (`yyyy-MM-dd'T'HH:mm:ss`)
- [필수] `timeZone` (String): IANA 타임존 ID (예: `Asia/Seoul`)

**검증 규칙**

| 규칙 | 상세 | code |
| --- | --- | --- |
| 필수 | `content` 가 `null` / 공백만 있는 값 불가 | `LETTER_001` |
| 길이 | **500자 초과** 불가 (문자 수, 공백 포함) | `LETTER_003` |
| 언어 | `[가-힣ㄱ-ㅎㅏ-ㅣ]` 포함 시 거부 | `LETTER_004` |
| 타임존 | `ZoneId` 로 해석 가능한 IANA ID | `LETTER_005` |
| 작성 시각 | `writtenAt` + `timeZone` 으로 환산한 시각이 서버 현재 시각 기준 ±24시간 이내 | `LETTER_005` |

- 0자·공백은 `LETTER_001` 이 담당하므로 `LETTER_003` 은 **상한만** 판정한다. 두 코드의 담당 구간이 겹치지 않는다.
- 편지 날짜(`date`)는 `writtenAt` 을 `timeZone` 기준으로 환산한 날짜다. 앱 작성 화면의 `DATE:` 표시와 항상 일치한다.
- 하루에 쓸 수 있는 편지 **개수 제한은 없다.**
- 편지는 전달 후 **수정 · 삭제할 수 없다.**

#### `성공` Response 201

```json
{
    "letterId": 16,
    "date": "2025-11-01",
    "createdAt": "2025-11-01T21:00:03"
}
```

- [필수] `letterId` (Long): 편지 ID
- [필수] `date` (LocalDate): 편지 날짜 (`writtenAt` + `timeZone` 기준)
- [필수] `createdAt` (LocalDateTime): 저장 시각 (요청의 `timeZone` 기준)

#### `성공` Response 200 — 중복 전달

동일 유저가 60초 이내에 같은 `content` 를 다시 보낸 경우다. 응답 본문은 **최초 편지의 생성 결과와 동일**하다.

```json
{
    "letterId": 16,
    "date": "2025-11-01",
    "createdAt": "2025-11-01T21:00:03"
}
```

- 앱은 201 과 200 을 구분할 필요 없이 동일하게 완료 화면으로 이동한다.
- 새 편지가 만들어지지 않으므로 피드백도 중복 요청되지 않는다.

#### `실패` Error Response

```json
{
    "status": 400,
    "code": "LETTER_001",
    "message": "편지 내용은 null일 수 없습니다."
}
```

| code | status | 상황 |
| --- | --- | --- |
| `LETTER_001` | 400 | `content` 가 `null` / 빈 값 / 공백만 있는 값 |
| `LETTER_003` | 400 | 500자 초과 |
| `LETTER_004` | 400 | 한글 포함 |
| `LETTER_005` | 400 | `writtenAt` / `timeZone` 이 올바르지 않음 |
| `USER_005` | 400 | 온보딩 미완료 (§2.6) |

---

### 4.2 `GET /api/v1/letters/{letterId}`

> **API 설명**
> - 특정 편지의 상세 내용과 도착한 피드백(교정문, 팁 등)을 조회합니다.
> - 조회에 성공하면 해당 편지는 읽음 처리됩니다.
>
> **도메인 규칙**
> - **본인 편지만** 조회할 수 있다. 타인의 편지는 존재 여부를 숨기기 위해 403 이 아닌 404 로 응답한다.
> - 피드백 완료 전에도 응답은 성공하지만 `feedback` 이 `null` 이다. 앱은 완료 전 카드의 진입 자체를 막는다.
> - 읽음 처리는 이 API 의 **부수 효과**다. 별도의 읽음 처리 API 는 없으며, 한 번 읽으면 다시 미열람으로 되돌아가지 않는다.
> - 교정 결과는 원문 전체를 순서대로 자른 조각(`correctionSegments`)으로 내려간다. 조각을 이어붙이면 원문·교정문이 그대로 복원되는 것을 서버가 보장한다.
> - 팁은 편지마다 0~3개다. 없을 수도 있다 (잘 쓴 편지).
> - 우표는 **AI 가 편지 내용에 어울리는 것으로 고른다.** 우표 종류는 DB(`stamps` 테이블)가 관리하므로 운영 중 추가·교체될 수 있다. 앱은 `stampImage` URL 을 그대로 표시하고 **우표 종류를 코드로 분기하지 않는다.**

#### Request

**Endpoint**

```
/api/v1/letters/{letterId}
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Path Variable**

- [필수] `letterId`: 조회할 편지의 ID

#### `성공` Response 200

```json
{
    "letterId": 15,
    "date": "2025-11-01",
    "originalContent": "I got flowers from a friend today...",
    "status": "FEEDBACK_COMPLETED",
    "stampImage": "https://cdn.dearjolly.com/stamps/flower_stamp.png",
    "feedback": {
        "feedbackId": 101,
        "correctedContent": "I received flowers from a friend today...",
        "tips": [
            "이 문맥에서는 'got'보다 'received'가 더 자연스러워요.",
            "'that'은 선행사를 한정하는 필수 정보를, 'which'는 추가 정보를 제공하는 데 쓰여요!"
        ],
        "correctionSegments": [
            {
                "sequence": 1,
                "originalText": "I ",
                "correctedText": "I ",
                "type": "UNCHANGED"
            },
            {
                "sequence": 2,
                "originalText": "got",
                "correctedText": "received",
                "type": "MODIFIED"
            },
            {
                "sequence": 3,
                "originalText": " flowers from a friend today",
                "correctedText": " flowers from a friend today",
                "type": "UNCHANGED"
            }
        ]
    }
}
```

- [필수] `letterId` (Long): 편지 ID
- [필수] `date` (LocalDate): 편지 날짜 (`yyyy-MM-dd`)
- [필수] `originalContent` (String): 원본 편지 내용
- [필수] `status` (String): 편지 상태 (`SUBMITTED`, `FEEDBACK_IN_PROGRESS`, `FEEDBACK_COMPLETED`)
- [선택] `stampImage` (String): 우표 이미지 URL (피드백 완료 전에는 `null`)
- [선택] `feedback` (Object): 피드백 정보 (**피드백 생성 전일 경우 `null`**)
    - [필수] `feedbackId` (Long): 피드백 ID
    - [필수] `correctedContent` (String): 교정된 전체 내용
    - [필수] `tips` (Array&lt;String&gt;): 피드백 팁 목록 (0~3개, 없으면 `[]`)
    - [필수] `correctionSegments` (Array): 교정 세그먼트 리스트 (1개 이상)
        - [필수] `sequence` (Integer): 문장 내 순서 (1부터 시작)
        - [필수] `originalText` (String): 원본 텍스트 조각
        - [필수] `correctedText` (String): 교정된 텍스트 조각
        - [필수] `type` (String): 수정 여부 (`UNCHANGED`, `MODIFIED`) — DB 컬럼명은 `correction_type` 이지만 **응답 필드명은 `type`** 이다

**렌더링 계약**

- `correctionSegments` 를 `sequence` 순서대로 이어붙이면 **`originalText` 는 `originalContent` 전체**, **`correctedText` 는 `correctedContent` 전체**와 정확히 일치한다. 앱은 인덱스 계산 없이 순차 렌더링만 하면 된다.
- `type == "UNCHANGED"` → 검은 글씨 그대로 출력
- `type == "MODIFIED"` → `originalText` 를 빨간 취소선, 바로 뒤에 `correctedText` 를 초록 하이라이트로 출력
- 공백은 세그먼트 문자열에 포함해 내려준다 (앱에서 공백을 임의로 추가하지 않음).
- 삭제 제안은 `type == "MODIFIED"` + `correctedText == ""` 로 표현한다.
- `tips` 가 `[]` 면 팁 영역을 표시하지 않는다.

#### `실패` Error Response

```json
{
    "status": 404,
    "code": "LETTER_002",
    "message": "존재하지 않는 편지입니다."
}
```

| code | status | 상황 |
| --- | --- | --- |
| `LETTER_002` | 404 | 존재하지 않는 편지 **또는 타인의 편지** (존재 여부를 노출하지 않기 위해 403 대신 404) |
| `USER_005` | 400 | 온보딩 미완료 (§2.6) |

---

### 4.3 `GET /api/v1/letters`

> **API 설명**
> - 홈 화면에 진입했을 때 보여질 편지 리스트를 조회합니다.
> - 헤더 영역에 필요한 닉네임 · 우표 수를 함께 반환하므로, 홈 진입 시 이 API 호출 한 번이면 됩니다.
>
> **도메인 규칙**
> - **본인 편지만** 조회된다. 유저는 서버 인증 컨텍스트에서 가져오며 파라미터로 받지 않는다.
> - `totalStampCount` 는 **피드백이 완료된 편지 수**다. 작성한 편지 수와 다르다.
> - 편지를 빼먹은 날이 눈에 띄지 않도록 **캘린더가 아니라 기록이 쌓이는 목록** 구조다. 비어 있는 날짜는 응답에 나타나지 않는다.
> - 피드백 완료 전 카드는 우표가 없고(`stampImage: null`) 상세로 들어갈 수 없다.
> - 정렬은 **서버가 처리**한다. 페이징된 일부만 앱에서 뒤집으면 전체 정렬이 깨지기 때문이다.
> - `stampImage` 는 DB 가 관리하는 우표 마스터의 이미지 URL 이다. 종류가 운영 중 늘어날 수 있으므로 앱은 **URL 을 그대로 렌더링**한다.
> - 온보딩 가드(§2.6)를 통과한 유저만 호출할 수 있으므로 `nickname` 은 **항상 non-null** 이다.

#### Request

**Endpoint**

```
/api/v1/letters
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Query Parameter**

- [선택] `page`: 페이지 번호 (기본값 0, 0 이상)
- [선택] `size`: 페이지 크기 (기본값 10, 1~50)
- [선택] `sort`: 정렬 기준 (기본값 `LATEST`)
    - `LATEST`: 최신순
    - `OLDEST`: 오래된 순

#### `성공` Response 200

```json
{
    "nickname": "Sally",
    "totalStampCount": 3,
    "letters": [
        {
            "letterId": 15,
            "date": "2025-11-01",
            "summary": "I got flowers from a friend today. It really touc",
            "status": "FEEDBACK_COMPLETED",
            "isRead": false,
            "stampImage": "https://cdn.dearjolly.com/stamps/flower_stamp.png"
        },
        {
            "letterId": 12,
            "date": "2025-10-30",
            "summary": "Hi! Jolly. I made a new friend at a Halloween part",
            "status": "FEEDBACK_COMPLETED",
            "isRead": true,
            "stampImage": "https://cdn.dearjolly.com/stamps/pumpkin_stamp.png"
        },
        {
            "letterId": 11,
            "date": "2025-10-29",
            "summary": "Lately I've been really worried about my new job a",
            "status": "SUBMITTED",
            "isRead": false,
            "stampImage": null
        }
    ],
    "hasNext": true
}
```

- [필수] `nickname` (String): 유저 닉네임
- [필수] `totalStampCount` (Integer): 모은 우표 총 개수
- [필수] `letters` (Array): 편지 목록
    - [필수] `letterId` (Long): 편지 ID
    - [필수] `date` (LocalDate): 편지 날짜 (`yyyy-MM-dd`)
    - [필수] `summary` (String): 편지 내용 미리보기 (**원문 앞 50자**, 말줄임은 클라이언트 처리)
    - [필수] `status` (String): 편지 상태
        - `SUBMITTED`: 제출됨 (피드백 대기중)
        - `FEEDBACK_IN_PROGRESS`: 피드백 생성 중
        - `FEEDBACK_COMPLETED`: 피드백 완료
    - [필수] `isRead` (Boolean): 피드백 열람 여부
    - [선택] `stampImage` (String): 우표 이미지 URL (피드백 완료 전에는 `null`)
- [필수] `hasNext` (Boolean): 다음 페이지 존재 여부

**클라이언트 렌더링 규칙**

| 조건 | 표시 |
| --- | --- |
| `status != "FEEDBACK_COMPLETED"` (`SUBMITTED` · `FEEDBACK_IN_PROGRESS`) | 회색 `soon` 우표, `ic_more_sm` 미노출, **터치 불가** |
| `status == "FEEDBACK_COMPLETED"` | `stampImage` 표시, `ic_more_sm` 노출, **카드 전체 영역** 터치 시 상세 이동 |
| `status == "FEEDBACK_COMPLETED" && isRead == false` | **날짜 앞 빨간 점** |

- 미열람 표시는 **피드백이 완료된 편지에만** 적용한다. 완료 전 편지도 `isRead` 는 `false` 지만 빨간 점을 표시하지 않는다.
- `totalStampCount` 는 **작성한 편지 수가 아니라** 피드백이 완료되어 우표가 도착한 편지 수다. (편지 3건 작성 + 1건 피드백 대기 → `totalStampCount = 2`)
- 정렬은 `date` 기준이며, 같은 날짜 안에서는 `letterId` 로 정렬한다 (`LATEST` → 내림차순, `OLDEST` → 오름차순).
- 헤더 필드(`nickname`, `totalStampCount`)는 `page > 0` 요청에서도 동일하게 내려간다. 앱은 첫 페이지 값만 사용하면 된다.
- `FEEDBACK_COMPLETED` 가 아닌 항목이 있으면 앱이 화면 재진입 · 새로고침 시 재조회해 상태 변화를 확인한다. 앱은 `SUBMITTED` 와 `FEEDBACK_IN_PROGRESS` 를 동일하게 처리하면 된다.
- 내부 상태 `FEEDBACK_FAILED` 는 응답에서 `SUBMITTED` 로 치환돼 내려간다.

#### `실패` Error Response

```json
{
    "status": 404,
    "code": "USER_001",
    "message": "사용자를 찾을 수 없습니다."
}
```

| code | status | 상황 |
| --- | --- | --- |
| `USER_001` | 404 | 사용자를 찾을 수 없음 |
| `USER_005` | 400 | 온보딩 미완료 (§2.6) |
| `COMMON_001` | 400 | `page` · `size` · `sort` 가 허용 범위를 벗어남 |

---

### 4.4 `GET /api/v1/home`

> **API 설명**
> - 홈 화면 헤더에 보여질 유저 정보(닉네임, 모은 우표 수)를 조회합니다.
> - 편지 목록 없이 헤더 정보만 갱신할 때 사용합니다. (홈 진입 시에는 `GET /api/v1/letters` 한 번이면 됩니다.)
>
> **도메인 규칙**
> - `totalStampCount` 는 **피드백이 완료되어 우표가 도착한 편지 수**다. 편지를 써도 검토가 끝나기 전에는 늘지 않는다.
> - `GET /api/v1/letters` 의 동일 필드와 항상 같은 값을 반환한다. 두 API 는 같은 서비스 메서드를 호출한다.
> - 온보딩 가드(§2.6)를 통과한 유저만 호출할 수 있으므로 `nickname` 은 **항상 non-null** 이다.

#### Request

**Endpoint**

```
/api/v1/home
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Query Parameter**: 없음

#### `성공` Response 200

```json
{
    "nickname": "Sally",
    "totalStampCount": 3
}
```

- [필수] `nickname` (String): 유저 닉네임
- [필수] `totalStampCount` (Integer): 모은 우표 총 개수 (피드백 완료된 편지 수)

#### `실패` Error Response

```json
{
    "status": 404,
    "code": "USER_001",
    "message": "사용자를 찾을 수 없습니다."
}
```

| code | status | 상황 |
| --- | --- | --- |
| `USER_001` | 404 | 사용자를 찾을 수 없음 |
| `USER_005` | 400 | 온보딩 미완료 (§2.6) |

---

## 5. global

### 5.1 `GET /api/v1/version`

> **API 설명**
> - 앱 최소 지원 버전과 정책 페이지 URL 을 조회합니다.
>
> **도메인 규칙**
> - 앱 버전이 `minSupportedVersion` 미만이면 앱이 강제 업데이트를 유도한다.
> - **공지사항 · 개인정보처리방침 · 이용약관은 서버 API 로 제공하지 않는다.** 이 응답의 웹뷰 링크로 처리한다.
> - 설정 화면 하단의 `현재 버전` 표기는 앱 로컬 값이며 이 응답과 무관하다.
> - 인증도 온보딩도 필요 없다. 로그인 전에 호출할 수 있어야 하기 때문이다.

#### Request

**Endpoint**

```
/api/v1/version
```

**Authorization 헤더**: 불필요

**Query Parameter**

- [선택] `platform`: 플랫폼 (`IOS`, `AOS`). 생략 시 공통 값 반환

#### `성공` Response 200

```json
{
    "latestVersion": "1.0.0",
    "minSupportedVersion": "1.0.0",
    "forceUpdate": false,
    "privacyPolicyUrl": "https://dearjolly.com/privacy",
    "termsOfServiceUrl": "https://dearjolly.com/terms",
    "noticeUrl": "https://dearjolly.com/notice"
}
```

- [필수] `latestVersion` (String): 최신 배포 버전
- [필수] `minSupportedVersion` (String): 이 버전 미만이면 강제 업데이트
- [필수] `forceUpdate` (Boolean): 강제 업데이트 여부
- [선택] `privacyPolicyUrl` (String): 개인정보처리방침 URL
- [선택] `termsOfServiceUrl` (String): 서비스 이용약관 URL
- [선택] `noticeUrl` (String): 공지사항 URL

- 설정 화면의 `현재 버전 1.0.0 (MVP)` 는 앱 로컬 값이고, 이 API 는 업데이트 유도 · 정책 링크 제공용이다.
- 공지사항 · 개인정보처리방침 · 이용약관은 별도 API 없이 **웹뷰 링크**로 처리한다.
- 약관 버전은 이 응답에 포함하지 않는다. MVP 는 재동의를 유도하지 않기 때문이다 ([기능명세 §3.2.1](./기능명세.md#321-약관-동의)).

---

## 6. Enum 정의

| Enum | 값 | 비고 |
| --- | --- | --- |
| `OauthProvider` | `KAKAO`, `APPLE` | 요청/응답의 `provider` 필드. ERD 와 클래스명을 동일하게 맞춘다 |
| `TermsType` | `SERVICE`, `PRIVACY`, `MARKETING` | `MARKETING` 만 선택 동의 |
| `Status` (letter) | `SUBMITTED`, `FEEDBACK_IN_PROGRESS`, `FEEDBACK_COMPLETED` | `FEEDBACK_IN_PROGRESS` 는 피드백 생성 중. 앱은 `SUBMITTED` 와 동일하게 렌더링한다. 내부 상태 `FEEDBACK_FAILED` 는 API 응답에서 `SUBMITTED` 로 변환해 내려보낸다 |
| `CorrectionType` | `UNCHANGED`, `MODIFIED` | 응답 필드명은 `type`, DB 컬럼명은 `correction_type` |
| `Sort` | `LATEST`, `OLDEST` | |

`Role`(`ROLE_USER` / `ROLE_ADMIN`), `UserStatus`(`ACTIVE` / `WITHDRAWN`) 는 서버 내부 enum 이며 API 응답에 노출되지 않는다. 정의는 [ERD §4](./ERD.md#4-enum-정의) 참고.

---

## 7. 에러 코드

`{도메인}_{일련번호}` 형식을 정본으로 한다. `ErrorCode` enum 은 이 표를 그대로 옮긴 것이어야 한다.

### 7.1 AUTH

| code | status | message | 발생 지점 |
| --- | --- | --- | --- |
| `AUTH_001` | 400 | 지원하지 않는 로그인 방식입니다. | `POST /auth/login` |
| `AUTH_002` | 401 | 소셜 로그인 인증에 실패했습니다. | `POST /auth/login` |
| `AUTH_003` | 502 | 소셜 로그인 서버와 통신하지 못했습니다. | `POST /auth/login` |
| `AUTH_004` | 401 | 로그인이 만료되었습니다. 다시 로그인해주세요. | `POST /auth/reissue` |
| `AUTH_005` | 401 | 유효하지 않은 토큰입니다. | 인증 필터 공통 (§2.3) |
| `AUTH_006` | 403 | 접근 권한이 없습니다. | Security `AccessDeniedHandler` 공통 (§2.3) |
| `AUTH_007` | 401 | 탈퇴한 계정입니다. 다시 로그인해주세요. | 인증 필터 공통 — `status = WITHDRAWN` (§2.3) |

### 7.2 USER

| code | status | message | 발생 지점 |
| --- | --- | --- | --- |
| `USER_001` | 404 | 사용자를 찾을 수 없습니다. | 유저 조회가 필요한 모든 API |
| `USER_002` | 400 | 필수 약관에 모두 동의해야 합니다. | `POST /users/terms` |
| `USER_003` | 400 | 닉네임은 공백, 특수기호, 한글 없이 작성해야 합니다. | `PATCH /users/nickname` |
| `USER_004` | 400 | 닉네임은 1자 이상 20자 이하여야 합니다. | `PATCH /users/nickname` |
| `USER_005` | 400 | 온보딩을 먼저 완료해야 합니다. | 온보딩 가드 (§2.6) |

### 7.3 LETTER

| code | status | message | 발생 지점 |
| --- | --- | --- | --- |
| `LETTER_001` | 400 | 편지 내용은 null일 수 없습니다. | `POST /letters` |
| `LETTER_002` | 404 | 존재하지 않는 편지입니다. | `GET /letters/{letterId}` |
| `LETTER_003` | 400 | 편지 내용은 500자를 초과할 수 없습니다. | `POST /letters` |
| `LETTER_004` | 400 | 편지는 영어로만 작성할 수 있습니다. | `POST /letters` |
| `LETTER_005` | 400 | 편지 작성 시각 정보가 올바르지 않습니다. | `POST /letters` |

### 7.4 COMMON

전부 `GlobalExceptionHandler` 가 공통으로 생산한다. 개별 API 의 실패 표에는 적지 않는다 (§2.3).

| code | status | message | 발생 지점 |
| --- | --- | --- | --- |
| `COMMON_001` | 400 | 잘못된 요청입니다. | 바디 파싱 실패, 타입 불일치, 쿼리 파라미터 제약 위반 |
| `COMMON_002` | 404 | 요청하신 경로를 찾을 수 없습니다. | 미정의 경로 |
| `COMMON_003` | 405 | 지원하지 않는 요청 방식입니다. | 미지원 HTTP 메서드 |
| `COMMON_004` | 429 | 요청이 너무 많습니다. 잠시 후 다시 시도해주세요. | Rate limit 초과 |
| `COMMON_005` | 500 | 일시적인 오류가 발생했습니다. | 처리되지 않은 서버 오류 |
