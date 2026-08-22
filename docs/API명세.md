# Dear Jolly — API 명세서 (MVP)

| 항목 | 내용 |
| --- | --- |
| 최종 갱신 | 2026-08-22 |
| Base URL | `https://{host}` |
| 인증 | `Authorization: Bearer {accessToken}` |
| Content-Type | `application/json; charset=UTF-8` |
| 날짜 포맷 | 날짜 → `yyyy-MM-dd` / 날짜+시각 → `yyyy-MM-dd'T'HH:mm:ss` |
| Swagger UI | `/swagger-ui/index.html` — 이 문서와 같은 내용을 담고 직접 호출까지 된다. 우측 상단에서 전체 · 인증 · 사용자 · 편지·홈 · 버전 그룹을 고른다 |

---

# 1. 엔드포인트 목록

| # | Method | 엔드포인트 | 설명 | 인증 | 온보딩 | 구현 |
| :---: | --- | --- | --- | :---: | :---: | :---: |
| [1](#1-get-apiv1authprovider-) | `GET` | `/api/v1/auth/{provider}` | 소셜 로그인 시작 | — | — | ✅ |
| [2](#2-get-apiv1authkakaocallback-) | `GET` | `/api/v1/auth/kakao/callback` | 카카오 콜백 (앱이 직접 호출하지 않음) | — | — | ✅ |
| [3](#3-post-apiv1authapplecallback-) | `POST` | `/api/v1/auth/apple/callback` | 애플 콜백 (앱이 직접 호출하지 않음) | — | — | ✅ |
| [4](#4-post-apiv1authreissue-) | `POST` | `/api/v1/auth/reissue` | 토큰 재발급 | — | — | ✅ |
| [5](#5-post-apiv1authlogout-) | `POST` | `/api/v1/auth/logout` | 로그아웃 | ✔ | — | ✅ |
| [6](#6-post-apiv1usersterms-) | `POST` | `/api/v1/users/terms` | 약관 동의, 마케팅 동의 및 철회 | ✔ | — | ✅ |
| [7](#7-get-apiv1users-) | `GET` | `/api/v1/users` | 계정 정보 조회 | ✔ | — | ✅ |
| [8](#8-delete-apiv1users-) | `DELETE` | `/api/v1/users` | 회원 탈퇴 | ✔ | — | ✅ |
| [9](#9-patch-apiv1usersnickname-) | `PATCH` | `/api/v1/users/nickname` | 닉네임 설정 | ✔ | — | ✅ |
| [10](#10-post-apiv1letters-) | `POST` | `/api/v1/letters` | 편지 작성 | ✔ | ✔ | ✅ |
| [11](#11-get-apiv1lettersletterid-) | `GET` | `/api/v1/letters/{letterId}` | 편지 상세 조회 (피드백 포함) | ✔ | ✔ | ✅ |
| [12](#12-get-apiv1letters-) | `GET` | `/api/v1/letters` | 편지 목록 조회 (홈) | ✔ | ✔ | ✅ |
| [13](#13-get-apiv1home-) | `GET` | `/api/v1/home` | 닉네임, 모은 우표 수 조회 (홈) | ✔ | ✔ | ✅ |
| [14](#14-get-apiv1version-) | `GET` | `/api/v1/version` | 최소 지원 버전, 정책 URL 조회 | — | — | ✅ |

- **인증** ✔ — `Authorization` 헤더가 필요하다.
- **온보딩** ✔ — 온보딩(필수 약관 동의 + 닉네임 등록) 전에 호출하면 `USER_005` 다.
- **구현** — ✅ 호출 가능, ❌ 명세만 확정되고 아직 개발 전이다.
- `{provider}` 는 `KAKAO` · `APPLE` 이며 **대소문자를 가리지 않는다**. (`kakao`, `Kakao` 모두 같다)

---

# 2. API 명세

## 1) GET /api/v1/auth/{provider} ✅

### [설명]

- 소셜 로그인을 시작한다. 앱은 이 주소를 외부 브라우저 또는 웹뷰로 열기만 하면 되고, 서버가 provider 로그인 페이지로 보내준다.
- 앱이 할 일은 세 가지다. **① 이 주소를 연다 → ② 열린 페이지에서 유저가 로그인한다 → ③ 딥링크로 토큰을 받는다.** 코드 교환·회원 생성·토큰 발급은 서버가 처리한다.
- **앱 SDK 로 받은 소셜 토큰을 서버에 전달하는 방식은 지원하지 않는다.**
- 유저는 `(provider + provider 회원 식별자)` 로 구분한다. 이메일이 같아도 카카오와 애플은 별개 계정이다.
- `provider` 는 대소문자를 가리지 않는다. `KAKAO` · `APPLE` 중 어느 것도 아니면 `COMMON_001` 이다.

### [스펙]

**Endpoint**

```
GET /api/v1/auth/{provider}
```

**Path Variable**

- [필수] `provider` (String): 로그인 수단 (`KAKAO`, `APPLE`). 대소문자를 가리지 않는다

**Authorization 헤더**: 불필요

### [성공 Response 302]

provider 의 로그인 페이지로 리다이렉트한다.

```
HTTP/1.1 302 Found
Location: https://kauth.kakao.com/oauth/authorize?client_id=...&redirect_uri=...&response_type=code&state=...
```

### [실패 Response 400]

```json
{
    "status": 400,
    "code": "COMMON_001",
    "message": "잘못된 요청입니다."
}
```

---

## 2) GET /api/v1/auth/kakao/callback ✅

### [설명]

- 카카오가 호출하는 주소다. **앱이 직접 호출하지 않는다.** 서버가 회원을 찾거나 새로 만들고, JWT 와 온보딩 상태를 앱 딥링크로 돌려준다.
- 앱은 딥링크로 받은 값으로 진입 화면을 정한다. **`termsAgreed == false` → 약관동의 화면**, **`termsAgreed == true` 이고 `nicknameRegistered == false` → 닉네임 화면**, **둘 다 `true` → 홈**이다.
- **토큰이 URL 에 실려 오므로**, 딥링크를 받은 즉시 보안 저장소(iOS Keychain / Android EncryptedSharedPreferences)로 옮기고 웹뷰를 썼다면 히스토리를 비운다.
- 가입 직후에는 약관 동의 이력이 없고 닉네임이 비어 있다.
- 로그인은 **기기 1대만 유지**된다. 새로 로그인하면 이전 로그인은 풀린다.
- **탈퇴한 계정으로 다시 로그인하면 항상 신규 가입**(`isNewUser=true`)이며, 이전 편지는 복원되지 않는다.
- 콜백 단계의 실패는 딥링크가 아니라 **JSON 에러 응답**으로 나간다. 인가 코드 검증에 실패하면 `AUTH_002`, 카카오 서버와 통신하지 못하면 `AUTH_003` 이다.

### [스펙]

**Endpoint**

```
GET /api/v1/auth/kakao/callback
```

**Authorization 헤더**: 불필요

**Request Parameter**

- [필수] `code` (String): 카카오가 발급한 인가 코드
- [선택] `state` (String): 로그인 시작 때 서버가 붙여 보낸 값

### [성공 Response 302]

JWT 와 온보딩 상태를 쿼리 파라미터에 실어 앱 딥링크로 리다이렉트한다.

```
HTTP/1.1 302 Found
Location: dearjolly://auth/callback
            ?accessToken=eyJhbGciOiJIUzI1NiJ9...
            &refreshToken=eyJhbGciOiJIUzI1NiJ9...
            &userId=1
            &isNewUser=true
            &termsAgreed=false
            &nicknameRegistered=false
```

- [필수] `accessToken` (String): 액세스 토큰 (30분)
- [필수] `refreshToken` (String): 리프레시 토큰 (14일)
- [필수] `userId` (Long): 유저 ID
- [필수] `isNewUser` (Boolean): 이번 로그인으로 가입된 신규 유저인지 여부
- [필수] `termsAgreed` (Boolean): 필수 약관 동의 완료 여부
- [필수] `nicknameRegistered` (Boolean): 닉네임 등록 여부

### [실패 Response 401]

```json
{
    "status": 401,
    "code": "AUTH_002",
    "message": "소셜 로그인 인증에 실패했습니다."
}
```

### [실패 Response 502]

```json
{
    "status": 502,
    "code": "AUTH_003",
    "message": "소셜 로그인 서버와 통신하지 못했습니다."
}
```

---

## 3) POST /api/v1/auth/apple/callback ✅

### [설명]

- 애플이 호출하는 주소다. **앱이 직접 호출하지 않는다.** 애플이 `form_post` 로 보내기 때문에 `POST` 다.
- 응답 형태와 앱 분기 규칙은 [2) GET /api/v1/auth/kakao/callback](#2-get-apiv1authkakaocallback-) 과 완전히 같다.
- 애플에서 이메일 제공을 거부한 유저는 **이메일이 비어 있다**(`null`). 서버가 대체 주소를 지어내지 않는다.
- 인가 코드 · `id_token` 검증에 실패하면 `AUTH_002`, 애플 서버와 통신하지 못하면 `AUTH_003` 이다.

### [스펙]

**Endpoint**

```
POST /api/v1/auth/apple/callback
```

**Authorization 헤더**: 불필요

**Request Parameter** (`application/x-www-form-urlencoded`)

- [필수] `code` (String): 애플이 발급한 인가 코드
- [필수] `id_token` (String): 회원 식별자와 이메일이 담긴 토큰
- [선택] `state` (String): 로그인 시작 때 서버가 붙여 보낸 값

### [성공 Response 302]

카카오 콜백과 동일한 파라미터로 앱 딥링크에 리다이렉트한다.

### [실패 Response 401]

```json
{
    "status": 401,
    "code": "AUTH_002",
    "message": "소셜 로그인 인증에 실패했습니다."
}
```

### [실패 Response 502]

```json
{
    "status": 502,
    "code": "AUTH_003",
    "message": "소셜 로그인 서버와 통신하지 못했습니다."
}
```

---

## 4) POST /api/v1/auth/reissue ✅

### [설명]

- 리프레시 토큰으로 액세스 토큰을 재발급한다.
- **리프레시 토큰도 함께 새로 발급되고 이전 토큰은 즉시 무효**가 되므로, 앱은 응답의 두 토큰을 모두 갈아 끼운다.
- 액세스 토큰 30분, 리프레시 토큰 14일이다.
- **같은 리프레시 토큰을 두 번 쓸 수 없다.** 만료됐거나 위조됐거나 이미 사용된 값이면 `AUTH_004` 다.
- **만료된 액세스 토큰이 헤더에 실려 있어도 정상 동작한다.** 앱의 토큰 인터셉터가 모든 요청에 헤더를 붙여도 문제없다.
- 앱은 `AUTH_004` 를 받으면 저장된 토큰을 지우고 로그인 화면으로 이동한다.

### [스펙]

**Endpoint**

```
POST /api/v1/auth/reissue
```

**Authorization 헤더**: 불필요 (실려 있어도 무시한다)

**Body**

```json
{
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

- [필수] `refreshToken` (String): 로그인 시 발급받은 리프레시 토큰

### [성공 Response 200]

```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

- [필수] `accessToken` (String): 새 액세스 토큰
- [필수] `refreshToken` (String): 새 리프레시 토큰 (기존 값을 이 값으로 교체 저장)

### [실패 Response 401]

```json
{
    "status": 401,
    "code": "AUTH_004",
    "message": "로그인이 만료되었습니다. 다시 로그인해주세요."
}
```

---

## 5) POST /api/v1/auth/logout ✅

### [설명]

- 로그아웃한다. **세션만 끊고 편지·계정 데이터는 그대로 보존**된다.
- 카카오 세션 종료 등 소셜 로그아웃은 앱 SDK 에서 처리한다.
- 온보딩 미완료 상태에서도 호출할 수 있다.
- 실패는 공통 인증 오류(`AUTH_005` · `AUTH_007`)뿐이다.

### [스펙]

**Endpoint**

```
POST /api/v1/auth/logout
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Body**: 없음

### [성공 Response 204]

```
(No Content)
```

---

## 6) POST /api/v1/users/terms ✅

### [설명]

- 약관 동의 내역을 저장한다. 온보딩 약관 동의와 설정 화면의 마케팅 동의 철회가 같은 API 를 쓴다.
- 약관은 3종이다. **`SERVICE`(서비스 이용약관) · `PRIVACY`(개인정보 처리방침)는 필수, `MARKETING`(마케팅 정보 수신)은 선택**이다.
- 마케팅에 동의하지 않아도 서비스 이용에 제한이 없다.
- 필수 2건이 모두 동의 상태가 아니면 `USER_002` 이며, **이때 그 요청은 아무것도 저장되지 않는다.** 온보딩에서는 필수 2건을 항상 함께 보낸다.
- **보내지 않은 항목은 그대로 유지된다.** 마케팅만 철회하려면 `MARKETING` 한 건만 보내면 된다.
- **약관이 개정돼도 다시 묻지 않는다.**
- 약관 본문은 이 API 가 주지 않는다. [14) GET /api/v1/version](#14-get-apiv1version-) 의 웹뷰 링크로 연다.

### [스펙]

**Endpoint**

```
POST /api/v1/users/terms
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

### [성공 Response 200]

```json
{
    "termsAgreed": true
}
```

- [필수] `termsAgreed` (Boolean): 이 요청 반영 후의 필수 약관 동의 완료 여부

### [실패 Response 400]

```json
{
    "status": 400,
    "code": "USER_002",
    "message": "필수 약관에 모두 동의해야 합니다."
}
```

---

## 7) GET /api/v1/users ✅

### [설명]

- 설정 화면에 표시할 계정 정보를 조회한다.
- **이메일이 비어 있을 수 있다.** 애플에서 이메일 제공을 거부한 경우이며, 이때 앱은 로그인 수단만 표시한다.
- 온보딩 전에도 호출할 수 있고, 이 경우 `nickname` 이 비어 있다.
- `marketingAgreed` 는 마케팅 동의 이력이 없으면 `false` 다.
- 실패는 공통 인증 오류(`AUTH_005` · `AUTH_007`)뿐이다.

### [스펙]

**Endpoint**

```
GET /api/v1/users
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

### [성공 Response 200]

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
- [선택] `email` (String): 소셜 계정 이메일 (제공되지 않으면 `null`)
- [필수] `marketingAgreed` (Boolean): 마케팅 수신 동의 여부

---

## 8) DELETE /api/v1/users ✅

### [설명]

- 회원 탈퇴를 처리한다. **모든 편지와 계정 정보가 삭제되며 복구할 수 없다.**
- 탈퇴 즉시 로그인이 풀린다. 남아 있던 액세스 토큰도 `AUTH_007` 로 거절된다.
- 탈퇴 후 같은 소셜 계정으로 다시 로그인하면 **신규 가입**으로 처리되고, 이전 편지는 돌아오지 않는다.
- 소셜 연결 해제(카카오 unlink / 애플 revoke)는 서버가 처리한다. **앱이 인가 코드를 따로 보낼 필요가 없다.**
- 온보딩 미완료 상태에서도 탈퇴할 수 있다.
- 실패는 공통 인증 오류(`AUTH_005` · `AUTH_007`)뿐이다.

### [스펙]

**Endpoint**

```
DELETE /api/v1/users
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Body**: 없음

### [성공 Response 204]

```
(No Content)
```

---

## 9) PATCH /api/v1/users/nickname ✅

### [설명]

- 닉네임을 등록하거나 변경한다. 온보딩(이름 입력)과 설정(이름 변경)이 같은 API 를 쓴다.
- **영문 · 숫자 1~20자만** 허용한다. 공백 · 특수기호 · 한글은 불가다.
- 길이는 **문자 수** 기준이라 앱의 `10/20` 카운터와 일치한다.
- **중복을 허용한다.** 같은 닉네임을 써도 에러가 나지 않는다.
- 변경 횟수 제한이 없다.
- **길이를 먼저 보고 통과하면 문자를 본다.** 두 조건을 동시에 어겨도 에러는 하나만 내려간다 — 21자 한글은 `USER_003` 이 아니라 `USER_004` 다.
- 앱은 사유별 문구(`공백을 포함할 수 없어요` / `특수 기호를 포함할 수 없어요` / `한글을 포함할 수 없어요`)를 클라이언트에서 판별해 표시한다. 서버는 최종 방어선이다.

### [스펙]

**Endpoint**

```
PATCH /api/v1/users/nickname
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
| 1 | 길이 | 1 ~ 20자 (문자 수). `null` · `""` 는 0자로 본다 | `USER_004` |
| 2 | 허용 문자 | 영문 + 숫자 (`^[A-Za-z0-9]+$`) | `USER_003` |

### [성공 Response 200]

```json
{
    "nickname": "iloveJolly"
}
```

- [필수] `nickname` (String): 변경된 닉네임

### [실패 Response 400]

```json
{
    "status": 400,
    "code": "USER_003",
    "message": "닉네임은 공백, 특수기호, 한글 없이 작성해야 합니다."
}
```

```json
{
    "status": 400,
    "code": "USER_004",
    "message": "닉네임은 1자 이상 20자 이하여야 합니다."
}
```

---

## 10) POST /api/v1/letters ✅

### [설명]

- 편지를 작성한다. 저장되면 AI 피드백이 시작되지만 **응답은 피드백을 기다리지 않고 바로 내려온다.**
- 편지는 **전달 후 수정·삭제할 수 없다.** 수정·삭제 API 가 없다.
- 본문은 **영어 전용, 1~500자**다. 한글이 섞이면 거부하고, 숫자·구두점·이모지는 허용한다.
- 편지 날짜는 앱이 보낸 `writtenAt` 의 날짜 부분이다.
- **하루 작성 개수 제한이 없다.** 같은 날짜 카드가 목록에 여러 개 쌓일 수 있다.
- 작성 직후 상태는 항상 `SUBMITTED` 이고 **우표는 `soon`(준비 중) 우표**다. 피드백이 끝나야 편지에 맞는 우표로 바뀐다.
- 검증은 **`content` → `timeZone` → `writtenAt` 순서**로 하며, 먼저 걸린 사유 하나만 내려간다.
- `writtenAt` 이 `2025-13-45T99:99:99` 처럼 **날짜로 해석조차 되지 않는 문자열**이면 `LETTER_005` 가 아니라 `COMMON_001` 이 나간다.
- **중복 전달은 서버가 막는다.** 60초 안에 같은 내용을 다시 보내면 새 편지를 만들지 않고 최초 편지를 `200 OK` 로 돌려준다. 앱이 별도 헤더를 보낼 필요가 없다.
- 중복 판정은 그 유저의 **직전 편지 1건**만 본다. 60초 안이라도 사이에 다른 내용의 편지를 한 통 쓰면 중복으로 잡히지 않는다.
- 앱은 `201` 과 `200` 을 구분할 필요 없이 동일하게 완료 화면으로 이동한다.

### [스펙]

**Endpoint**

```
POST /api/v1/letters
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
- [필수] `timeZone` (String): 기기 타임존 ID (예: `Asia/Seoul`)

**검증 규칙**

| 순서 | 규칙 | 상세 | code |
| --- | --- | --- | --- |
| 1 | 필수 | `content` 가 비었거나 공백만 있는 값 불가 | `LETTER_001` |
| 1 | 길이 | **500자 초과** 불가 (문자 수, 공백 포함) | `LETTER_003` |
| 1 | 언어 | 한글 포함 시 거부 | `LETTER_004` |
| 2 | 타임존 | 유효한 타임존 ID. 누락 불가 | `LETTER_005` |
| 3 | 작성 시각 | 서버 현재 시각 기준 **±24시간 이내**. 누락 불가 | `LETTER_005` |

### [성공 Response 201]

```json
{
    "letterId": 16,
    "date": "2025-11-01",
    "createdAt": "2025-11-01T21:00:03"
}
```

- [필수] `letterId` (Long): 편지 ID
- [필수] `date` (LocalDate): 편지 날짜
- [필수] `createdAt` (LocalDateTime): 저장 시각 (요청의 `timeZone` 기준, **초 단위까지**)

### [성공 Response 200 — 중복 전달]

60초 안에 같은 내용을 다시 보낸 경우이며, 응답 본문은 `createdAt` 까지 최초 편지의 `201` 응답과 완전히 같다.

### [실패 Response 400]

```json
{
    "status": 400,
    "code": "LETTER_001",
    "message": "편지 내용은 null일 수 없습니다."
}
```

```json
{
    "status": 400,
    "code": "LETTER_003",
    "message": "편지 내용은 500자를 초과할 수 없습니다."
}
```

```json
{
    "status": 400,
    "code": "LETTER_004",
    "message": "편지는 영어로만 작성할 수 있습니다."
}
```

```json
{
    "status": 400,
    "code": "LETTER_005",
    "message": "편지 작성 시각 정보가 올바르지 않습니다."
}
```

```json
{
    "status": 400,
    "code": "COMMON_001",
    "message": "잘못된 요청입니다."
}
```

```json
{
    "status": 400,
    "code": "USER_005",
    "message": "온보딩을 먼저 완료해야 합니다."
}
```

---

## 11) GET /api/v1/letters/{letterId} ✅

### [설명]

- 편지 상세와 도착한 피드백(교정문 · 팁)을 조회한다.
- **피드백이 완료된 편지를 조회하면 읽음 처리된다.** 별도의 읽음 처리 API 는 없고, 한 번 읽으면 다시 미열람으로 돌아가지 않는다.
- 피드백 완료 전 편지는 조회해도 읽음 처리되지 않는다. **피드백이 도착한 뒤 목록의 빨간 점은 그대로 뜬다.**
- **본인 편지만** 조회할 수 있다. 없는 편지든 남의 편지든 똑같이 `LETTER_002`(404) 다.
- 피드백 완료 전에도 응답은 성공한다. 이때 `feedback` 은 `null` 이고 `stampImage` 는 `soon`(준비 중) 우표 URL 이다. 앱은 완료 전 카드의 진입 자체를 막는다.
- 팁은 편지마다 0~3개이며, `tips` 가 `[]` 면 팁 영역을 표시하지 않는다.
- 우표는 AI 가 편지 내용에 맞춰 고르고 종류가 운영 중 늘거나 바뀔 수 있다. 앱은 **`stampImage` URL 을 그대로 표시하고 우표 종류로 분기하지 않는다.**
- 교정문은 `correctionSegments` 를 `sequence` 순서대로 이어붙여 그린다. **`originalText` 를 모두 이으면 원문 전체와, `correctedText` 를 모두 이으면 `correctedContent` 전체와 정확히 일치**하므로 앱은 인덱스 계산 없이 순서대로 그리기만 하면 된다.
- `type == "UNCHANGED"` 는 검은 글씨 그대로, `type == "MODIFIED"` 는 `originalText` 를 빨간 취소선으로 찍고 바로 뒤에 `correctedText` 를 초록 하이라이트로 찍는다.
- 삭제 제안은 `type == "MODIFIED"` 이면서 `correctedText` 가 빈 문자열인 조각이다.
- 공백도 조각에 포함돼 내려가므로 **앱이 공백을 임의로 추가하지 않는다.**

### [스펙]

**Endpoint**

```
GET /api/v1/letters/{letterId}
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Path Variable**

- [필수] `letterId` (Long): 조회할 편지의 ID

### [성공 Response 200]

```json
{
    "letterId": 15,
    "date": "2025-11-01",
    "originalContent": "I got flowers from a friend today...",
    "status": "FEEDBACK_COMPLETED",
    "stampImage": "http://localhost:9000/dear-jolly-stamps/stamp/%EA%BD%83_%EC%9E%A5%EB%AF%B8.png",
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
- [필수] `date` (LocalDate): 편지 날짜
- [필수] `originalContent` (String): 원본 편지 내용
- [필수] `status` (String): 편지 상태 (`SUBMITTED`, `FEEDBACK_IN_PROGRESS`, `FEEDBACK_COMPLETED`)
- [필수] `stampImage` (String): 우표 이미지 URL. 항상 값이 있으며, 피드백 완료 전에는 `soon`(준비 중) 우표다
- [선택] `feedback` (Object): 피드백 정보 (**피드백 완료 전에는 `null`**)
    - [필수] `feedbackId` (Long): 피드백 ID
    - [필수] `correctedContent` (String): 교정된 전체 내용
    - [필수] `tips` (Array&lt;String&gt;): 피드백 팁 목록 (0~3개, 없으면 `[]`)
    - [필수] `correctionSegments` (Array): 교정 조각 목록 (1개 이상)
        - [필수] `sequence` (Integer): 순서 (1부터 시작)
        - [필수] `originalText` (String): 원본 텍스트 조각
        - [필수] `correctedText` (String): 교정된 텍스트 조각
        - [필수] `type` (String): 수정 여부 (`UNCHANGED`, `MODIFIED`)

### [실패 Response 404]

```json
{
    "status": 404,
    "code": "LETTER_002",
    "message": "존재하지 않는 편지입니다."
}
```

### [실패 Response 400]

```json
{
    "status": 400,
    "code": "USER_005",
    "message": "온보딩을 먼저 완료해야 합니다."
}
```

---

## 12) GET /api/v1/letters ✅

### [설명]

- 홈 화면의 편지 목록을 조회한다. 닉네임 · 우표 수는 내려가지 않으므로, **홈 진입 시 [13) GET /api/v1/home](#13-get-apiv1home-) 과 함께 호출한다.**
- **본인 편지만** 조회된다. 유저 정보는 토큰에서 가져오므로 파라미터로 보내지 않는다.
- 편지를 쓰지 않은 날은 응답에 나타나지 않는다. **캘린더가 아니라 기록이 쌓이는 목록**이다.
- 정렬은 서버가 처리하므로 앱은 받은 순서대로 그린다. `page` · `size` · `sort` 가 허용 범위를 벗어나면 `COMMON_001` 이다.
- 정렬 기준은 **편지 날짜**이며, 같은 날짜에 여러 통을 썼다면 `LATEST` 는 **나중에 쓴 편지가 먼저**, `OLDEST` 는 **먼저 쓴 편지가 먼저** 온다.
- 편지가 한 통도 없으면 `letters` 는 빈 배열이고 `hasNext` 는 `false` 다.
- 앱은 `SUBMITTED` 와 `FEEDBACK_IN_PROGRESS` 를 동일하게 처리한다. 이 상태의 카드는 **회색 `soon` 우표 · `ic_more_sm` 미노출 · 터치 불가**다. 우표는 상태와 무관하게 `stampImage` 를 그대로 표시한다.
- `FEEDBACK_COMPLETED` 카드는 **`stampImage` 표시 · `ic_more_sm` 노출 · 카드 전체 영역 터치 시 상세 이동**이다.
- `FEEDBACK_COMPLETED` 이면서 `isRead == false` 면 **날짜 앞에 빨간 점**을 찍는다. 완료 전 편지도 `isRead` 는 `false` 지만 빨간 점을 표시하지 않는다.
- 목록에 `FEEDBACK_COMPLETED` 가 아닌 항목이 있으면, 앱은 화면 재진입 · 새로고침 때 다시 조회해 상태 변화를 확인한다.

### [스펙]

**Endpoint**

```
GET /api/v1/letters
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Query Parameter**

- [선택] `page` (Integer): 페이지 번호 (기본값 0, 0 이상)
- [선택] `size` (Integer): 페이지 크기 (기본값 10, 1~50)
- [선택] `sort` (String): 정렬 기준 (기본값 `LATEST`) — `LATEST` 최신순 / `OLDEST` 오래된 순

### [성공 Response 200]

```json
{
    "letters": [
        {
            "letterId": 15,
            "date": "2025-11-01",
            "summary": "I got flowers from a friend today. It really touch",
            "status": "FEEDBACK_COMPLETED",
            "isRead": false,
            "stampImage": "http://localhost:9000/dear-jolly-stamps/stamp/%EA%BD%83_%EC%9E%A5%EB%AF%B8.png"
        },
        {
            "letterId": 12,
            "date": "2025-10-30",
            "summary": "Hi! Jolly. I made a new friend at a Halloween part",
            "status": "FEEDBACK_COMPLETED",
            "isRead": true,
            "stampImage": "http://localhost:9000/dear-jolly-stamps/stamp/%ED%98%B8%EB%B0%95_%ED%95%A0%EB%A1%9C%EC%9C%88.png"
        },
        {
            "letterId": 11,
            "date": "2025-10-29",
            "summary": "Lately I've been really worried about my new job a",
            "status": "SUBMITTED",
            "isRead": false,
            "stampImage": "http://localhost:9000/dear-jolly-stamps/stamp/soon.png"
        }
    ],
    "hasNext": true
}
```

- [필수] `letters` (Array): 편지 목록
    - [필수] `letterId` (Long): 편지 ID
    - [필수] `date` (LocalDate): 편지 날짜
    - [필수] `summary` (String): 편지 미리보기 (**원문 앞 50자**, 말줄임은 앱에서 처리)
    - [필수] `status` (String): 편지 상태 (`SUBMITTED`, `FEEDBACK_IN_PROGRESS`, `FEEDBACK_COMPLETED`)
    - [필수] `isRead` (Boolean): 피드백 열람 여부
    - [필수] `stampImage` (String): 우표 이미지 URL. 항상 값이 있으며, 피드백 완료 전에는 `soon`(준비 중) 우표다
- [필수] `hasNext` (Boolean): 다음 페이지 존재 여부

### [실패 Response 400]

```json
{
    "status": 400,
    "code": "USER_005",
    "message": "온보딩을 먼저 완료해야 합니다."
}
```

```json
{
    "status": 400,
    "code": "COMMON_001",
    "message": "잘못된 요청입니다."
}
```

---

## 13) GET /api/v1/home ✅

### [설명]

- 홈 헤더의 닉네임 · 모은 우표 수를 조회한다.
- **홈 진입 시 [12) GET /api/v1/letters](#12-get-apiv1letters-) 와 함께 호출한다.** 편지 목록 응답에는 이 두 값이 들어 있지 않다.
- 헤더만 갱신할 때는 이 API 만 다시 호출하면 된다.
- `totalStampCount` 는 **작성한 편지 수가 아니라 피드백이 완료된 편지 수**다. 편지 3건을 썼고 1건이 피드백 대기라면 `2` 다.
- `nickname` 은 **항상 값이 있다.**

### [스펙]

**Endpoint**

```
GET /api/v1/home
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Query Parameter**: 없음

### [성공 Response 200]

```json
{
    "nickname": "Sally",
    "totalStampCount": 3
}
```

- [필수] `nickname` (String): 유저 닉네임
- [필수] `totalStampCount` (Integer): 모은 우표 총 개수 (피드백 완료된 편지 수)

### [실패 Response 400]

```json
{
    "status": 400,
    "code": "USER_005",
    "message": "온보딩을 먼저 완료해야 합니다."
}
```

---

## 14) GET /api/v1/version ✅

### [설명]

- 앱 최소 지원 버전과 정책 페이지 URL 을 조회한다. **로그인 전에도 호출할 수 있다.**
- 앱 버전이 `minSupportedVersion` 미만이면 강제 업데이트를 유도한다. **판정은 앱이 한다.** 서버는 앱 버전을 받지 않는다.
- `forceUpdate` 는 참고용 보조 신호다. 앱은 자기 버전과 `minSupportedVersion` 을 직접 비교한다.
- **공지사항 · 개인정보처리방침 · 이용약관은 별도 API 가 없다.** 이 응답의 링크를 웹뷰로 연다.
- 설정 화면 하단의 `현재 버전` 표기는 앱 로컬 값이며 이 응답과 무관하다.
- `platform` 이 `IOS` · `AOS` 가 아니면 `COMMON_001` 이다.

### [스펙]

**Endpoint**

```
GET /api/v1/version
```

**Authorization 헤더**: 불필요

**Query Parameter**

- [선택] `platform` (String): 플랫폼 (`IOS`, `AOS`). 생략하면 공통 값을 반환하고, 값을 주면 **그 플랫폼에 설정된 값만** 공통 값을 덮어쓴다

### [성공 Response 200]

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

### [실패 Response 400]

```json
{
    "status": 400,
    "code": "COMMON_001",
    "message": "잘못된 요청입니다."
}
```

---

# 3. Enum 정의

| Enum | 값 | 설명 |
| --- | --- | --- |
| `OauthProvider` | `KAKAO`, `APPLE` | 로그인 수단 |
| `TermsType` | `SERVICE`, `PRIVACY`, `MARKETING` | 약관 종류. `MARKETING` 만 선택 동의 |
| `Status` (letter) | `SUBMITTED`, `FEEDBACK_IN_PROGRESS`, `FEEDBACK_COMPLETED` | 편지 상태. 앱은 앞의 두 값을 동일하게 렌더링한다 |
| `CorrectionType` | `UNCHANGED`, `MODIFIED` | 교정 조각의 수정 여부 (응답 필드명은 `type`) |
| `Sort` | `LATEST`, `OLDEST` | 편지 목록 정렬 기준 |
| `Platform` | `IOS`, `AOS` | 버전 조회의 `platform` 파라미터 |

---

# 4. Error Code 정의

에러 코드는 `{도메인}_{일련번호}` 형식이다.

## 1) AUTH

| code | status | message | 발생 지점 |
| --- | --- | --- | --- |
| `AUTH_002` | 401 | 소셜 로그인 인증에 실패했습니다. | 소셜 로그인 콜백 |
| `AUTH_003` | 502 | 소셜 로그인 서버와 통신하지 못했습니다. | 소셜 로그인 콜백 |
| `AUTH_004` | 401 | 로그인이 만료되었습니다. 다시 로그인해주세요. | 토큰 재발급 |
| `AUTH_005` | 401 | 유효하지 않은 토큰입니다. | 인증이 필요한 모든 API |
| `AUTH_006` | 403 | 접근 권한이 없습니다. | 인증이 필요한 모든 API |
| `AUTH_007` | 401 | 탈퇴한 계정입니다. 다시 로그인해주세요. | 인증이 필요한 모든 API |

## 2) USER

| code | status | message | 발생 지점 |
| --- | --- | --- | --- |
| `USER_001` | 404 | 사용자를 찾을 수 없습니다. | 서버 내부 방어용. 토큰이 가리키는 유저가 없으면 `AUTH_005` 가 먼저 나가므로 앱은 이 코드를 받지 않는다 |
| `USER_002` | 400 | 필수 약관에 모두 동의해야 합니다. | 약관 동의 |
| `USER_003` | 400 | 닉네임은 공백, 특수기호, 한글 없이 작성해야 합니다. | 닉네임 설정 |
| `USER_004` | 400 | 닉네임은 1자 이상 20자 이하여야 합니다. | 닉네임 설정 |
| `USER_005` | 400 | 온보딩을 먼저 완료해야 합니다. | 온보딩 가드 |

## 3) LETTER

| code | status | message | 발생 지점 |
| --- | --- | --- | --- |
| `LETTER_001` | 400 | 편지 내용은 null일 수 없습니다. | 편지 작성 |
| `LETTER_002` | 404 | 존재하지 않는 편지입니다. | 편지 상세 조회 |
| `LETTER_003` | 400 | 편지 내용은 500자를 초과할 수 없습니다. | 편지 작성 |
| `LETTER_004` | 400 | 편지는 영어로만 작성할 수 있습니다. | 편지 작성 |
| `LETTER_005` | 400 | 편지 작성 시각 정보가 올바르지 않습니다. | 편지 작성 |

## 4) COMMON

모든 API 에서 공통으로 발생하므로 개별 API 의 실패 표에는 적지 않는다.

| code | status | message | 발생 지점 |
| --- | --- | --- | --- |
| `COMMON_001` | 400 | 잘못된 요청입니다. | 바디 형식 오류, 타입 불일치, 쿼리 파라미터 제약 위반, 정의되지 않은 enum 값 |
| `COMMON_002` | 404 | 요청하신 경로를 찾을 수 없습니다. | 없는 경로 |
| `COMMON_003` | 405 | 지원하지 않는 요청 방식입니다. | 지원하지 않는 HTTP 메서드 |
| `COMMON_004` | 429 | 요청이 너무 많습니다. 잠시 후 다시 시도해주세요. | 요청 횟수 초과 |
| `COMMON_005` | 500 | 일시적인 오류가 발생했습니다. | 서버 오류 |

---

# 5. 그 외 규약 정의

## 1) 인증 · 인가 규약

| 항목 | 값 |
| --- | --- |
| 헤더 | `Authorization: Bearer {accessToken}` |
| 액세스 토큰 만료 | 30분 |
| 리프레시 토큰 만료 | 14일 |
| 인증 불필요 | 소셜 로그인 시작 · 콜백 2종, 토큰 재발급, 버전 조회 |

- 리프레시 토큰은 재발급 때마다 새 값으로 교체되며, **이전 값으로는 재발급할 수 없다.**
- 로그인은 **기기 1대만 유지**된다. 다른 기기에서 로그인하면 이전 로그인은 풀린다.
- 탈퇴한 계정의 토큰은 만료 전이라도 `AUTH_007` 로 거절된다.

## 2) 성공 · 실패 응답 정의

- 성공 응답은 **별도 래퍼 없이 DTO 를 그대로** 내려준다.
- 조회 · 수정 성공은 `200 OK`, 생성 성공은 `201 Created`, 본문이 없으면 `204 No Content` 다.
- 예외적으로 소셜 로그인 3종은 `302 Found` 로 리다이렉트하고, **편지 중복 전달과 약관 동의는 `201` 이 아니라 `200 OK`** 다.
- 실패 응답은 **모든 API 가 같은 형태**다.

```json
{
    "status": 400,
    "code": "LETTER_001",
    "message": "편지 내용은 null일 수 없습니다."
}
```

- [필수] `status` (Integer): HTTP 상태 코드
- [필수] `code` (String): `{도메인}_{일련번호}` 형식 (`AUTH_`, `USER_`, `LETTER_`, `COMMON_`)
- [필수] `message` (String): 사용자에게 그대로 보여줘도 되는 문구

## 3) 시간 · 타임존 정의

| 값 | 기준 |
| --- | --- |
| 편지 작성 시각 (`writtenAt` + `timeZone`) | **앱이 보낸 값** |
| 편지 날짜 (`date`) | `writtenAt` 의 날짜 부분 |
| 편지 저장 시각 (`createdAt`) | 편지에 저장된 타임존 기준으로 변환해 반환 |
| 그 외 모든 서버 시각 | `Asia/Seoul` (KST) |

- 편지 날짜에 KST 를 강제하지 않는다. **해외에서 쓴 편지는 현지 날짜로 기록된다.**

## 4) 페이징 정의

| 파라미터 | 기본값 | 제약 |
| --- | --- | --- |
| `page` | 0 | 0 이상 |
| `size` | 10 | 1 ~ 50 |
| `sort` | `LATEST` | `LATEST` / `OLDEST` |

- 제약을 벗어난 값은 `COMMON_001`(400) 이다.
- 정렬은 서버가 처리한다. 앱은 받은 순서대로 그리면 된다.

## 5) 온보딩 가드 정의

| 항목 | 내용 |
| --- | --- |
| 완료 조건 | 필수 약관 2종에 모두 동의 **그리고** 닉네임 등록 |
| 차단 대상 | 엔드포인트 목록의 **온보딩** 열이 ✔ 인 API (`/api/v1/letters` 3종, `/api/v1/home`) |
| 통과 대상 | 로그인 · 로그아웃 · 토큰 재발급, 약관 동의, 닉네임 설정, 계정 조회, 회원 탈퇴, 버전 조회 |
| 위반 시 | `USER_005` (400) |

- 온보딩(필수 약관 동의 + 닉네임 등록)을 마치지 않은 유저는 **편지 · 홈 API 를 호출할 수 없다.**
- 이 가드 덕분에 홈 응답의 `nickname` 은 **항상 값이 있다.**
