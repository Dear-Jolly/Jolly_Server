# Dear Jolly — API 명세서 (MVP)

> 도메인/기능 배경은 [기능명세.md](./기능명세.md) 참고. 이 문서는 **요청/응답 계약**만 다룬다.
> letter 도메인 4개 API 는 팀 초안을 그대로 반영했고, user / version 도메인은 동일한 형식으로 확장한 **제안**이다.

| 항목 | 내용 |
| --- | --- |
| 문서 버전 | v1.1 |
| Base URL | `https://{host}` |
| 인증 | `Authorization: Bearer {accessToken}` |
| Content-Type | `application/json; charset=UTF-8` |
| 시간대 | `Asia/Seoul` (KST) |
| 날짜 포맷 | `LocalDate` → `yyyy-MM-dd` / `LocalDateTime` → `yyyy-MM-dd'T'HH:mm:ss` |

---

## 1. 엔드포인트 목록

| # | 도메인 | Method | 엔드포인트 | 설명 | 인증 | 상태 |
| --- | --- | --- | --- | --- | :---: | --- |
| 1 | user | `POST` | `/api/v1/auth/login` | 소셜 로그인 (회원가입 포함) | — | 임시 |
| 2 | user | `POST` | `/api/v1/users/terms` | 약관 동의 | ✔ | 제안 |
| 3 | user | `GET` | `/api/v1/users` | 계정 정보 조회 | ✔ | 제안 |
| 4 | user | `DELETE` | `/api/v1/users` | 회원 탈퇴 | ✔ | 제안 |
| 5 | user | `PATCH` | `/api/v1/users/nickname` | 닉네임 설정 | ✔ | 확정 |
| 6 | letter | `POST` | `/api/v1/letter` | 단일 편지 작성 + 피드백 요청 | ✔ | **초안 확정** |
| 7 | letter | `GET` | `/api/v1/letters/{letterId}` | 편지 및 피드백 상세 조회 | ✔ | **초안 확정** |
| 8 | letter | `GET` | `/api/v1/letters` | 전체 편지 리스트 조회 | ✔ | **초안 확정** |
| 9 | letter | `GET` | `/api/v1/home` | 닉네임, 모은 우표 수 조회 | ✔ | **초안 확정** |
| 10 | version | `GET` | `/api/v1/version` | 최소 지원 버전 조회 | — | 제안 |

⚠️ 초안 검토 중 발견한 충돌 사항이 있다. 구현 전 [8. 확인 필요 항목](#8-확인-필요-항목) 을 먼저 확정할 것.

---

## 2. 공통 규약

### 2.1 에러 응답 포맷

```json
{
    "status": 400,
    "code": "LETTER_001",
    "message": "편지 내용은 null일 수 없습니다."
}
```

- [필수] `status` (Integer): HTTP 상태 코드
- [필수] `code` (String): `{도메인}_{일련번호}` 형식
- [필수] `message` (String): 에러 메시지

전체 코드 목록은 [7. 에러 코드](#7-에러-코드) 참고.

### 2.2 인증

| 항목 | 값 |
| --- | --- |
| 헤더 | `Authorization: Bearer {accessToken}` |
| Access Token 만료 | 30분 |
| Refresh Token 만료 | 14일 |
| 인증 불필요 | `POST /api/v1/auth/login`, `GET /api/v1/version` |

---

## 3. user

### 3.1 `POST /api/v1/auth/login`

> **API 설명**
> - 카카오 / 애플 소셜 로그인을 수행합니다.
> - 가입되지 않은 유저라면 회원가입을 함께 처리합니다.

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
- [필수] `termsAgreed` (Boolean): 필수 약관 동의 완료 여부
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

### 3.2 `POST /api/v1/users/terms`

> **API 설명**
> - 온보딩 약관 동의 내역을 저장합니다.
> - 필수 약관(`SERVICE`, `PRIVACY`)에 모두 동의해야 통과합니다.

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

- [필수] `agreements` (Array): 약관 동의 목록
    - [필수] `type` (String): 약관 종류 (`SERVICE`, `PRIVACY`, `MARKETING`)
    - [필수] `agreed` (Boolean): 동의 여부

#### `성공` Response 200

```json
{
    "termsAgreed": true
}
```

- [필수] `termsAgreed` (Boolean): 필수 약관 동의 완료 여부

#### `실패` Error Response

```json
{
    "status": 400,
    "code": "USER_002",
    "message": "필수 약관에 모두 동의해야 합니다."
}
```

---

### 3.3 `GET /api/v1/users`

> **API 설명**
> - 설정 화면에 표시할 계정 정보를 조회합니다.

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
    "marketingAgreed": true,
    "appVersion": "1.0.0"
}
```

- [필수] `nickname` (String): 유저 닉네임
- [필수] `provider` (String): 로그인 수단 (`KAKAO`, `APPLE`) — 앱에서 아이콘 매핑
- [선택] `email` (String): 소셜 계정 이메일 (Apple private relay 등 미제공 시 `null`)
- [필수] `marketingAgreed` (Boolean): 마케팅 수신 동의 여부
- [선택] `appVersion` (String): 표시용 서비스 버전

#### `실패` Error Response

```json
{
    "status": 404,
    "code": "USER_001",
    "message": "사용자를 찾을 수 없습니다."
}
```

---

### 3.4 `DELETE /api/v1/users`

> **API 설명**
> - 회원 탈퇴를 처리합니다.
> - 모든 편지와 계정 정보가 삭제되며 복구할 수 없습니다.

#### Request

**Endpoint**

```
/api/v1/users
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Body** (Apple 로그인 유저만 필요)

```json
{
    "authorizationCode": "c1a2b3d4e5..."
}
```

- [선택] `authorizationCode` (String): Apple 토큰 revoke 용 인가 코드
    - **Apple 로그인 유저는 필수.** App Store 심사 요건(연동 해제)에 해당한다.

#### `성공` Response 204

```
(No Content)
```

**처리 순서**

1. 소셜 연결 해제 (Kakao `unlink` / Apple `revoke`) — 실패해도 로그만 남기고 탈퇴는 계속 진행
2. `correction_segments` → `feedbacks` → `letters` → `terms_agreements` → `users` 순서로 삭제

#### `실패` Error Response

```json
{
    "status": 404,
    "code": "USER_001",
    "message": "사용자를 찾을 수 없습니다."
}
```

---

### 3.5 `PATCH /api/v1/users/nickname`

> **API 설명**
> - 닉네임을 등록하거나 변경합니다.
> - 온보딩(이름 입력)과 설정(이름 변경)이 동일한 API 를 사용합니다.

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

| 규칙 | 값 | code |
| --- | --- | --- |
| 길이 | 1 ~ 20자 (문자 수) | `USER_004` |
| 허용 문자 | `^[A-Za-z0-9]{1,20}$` | `USER_003` |
| 공백 · 특수기호 · 한글 | 불가 | `USER_003` |

- 앱은 사유별 문구(`공백을 포함할 수 없어요` / `특수 기호를 포함할 수 없어요` / `한글을 포함할 수 없어요`)를 클라이언트에서 판별해 표시한다. 서버는 최종 방어선이다.
- 닉네임 **중복은 허용**한다 (표시용 이름).

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

---

## 4. letter

### 4.1 `POST /api/v1/letter`

> **API 설명**
> - 새로운 편지를 작성합니다.
> - 편지가 저장되면 AI 피드백 프로세스가 트리거됩니다.

#### Request

**Endpoint**

```
/api/v1/letter
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6ImV1bmplb25nIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3MzEyMjg1ODIsImV4cCI6MTczMTIyODYxOH0.ZCvhG9hWexsMg-dpJrtFQBpYbXIwmWgZrYk6AlugQVU
```

**Body**

```json
{
    "content": "I got flowers from a friend today...",
    "letterDate": "2025-11-01"
}
```

- [필수] `content` (String): 편지 내용 (최대 500자)
- [필수] `letterDate` (LocalDate): 일기 날짜 (`YYYY-MM-DD`)

**검증 규칙**

| 규칙 | 상세 | code |
| --- | --- | --- |
| 필수 | `null` / 공백만 있는 값 불가 | `LETTER_001` |
| 길이 | 1 ~ 500자 (문자 수, 공백 포함) | `LETTER_003` |
| 언어 | `[가-힣ㄱ-ㅎㅏ-ㅣ]` 포함 시 거부 | `LETTER_004` |
| 날짜 | 서버 KST 기준 **오늘**이어야 함 ([Q2](#8-확인-필요-항목)) | `LETTER_005` |

- 편지 작성 화면의 `DATE:` 는 터치 · 변경이 불가하므로 `letterDate` 는 항상 오늘 날짜가 들어온다. 서버는 이를 검증한다.
- 편지는 전달 후 **수정 · 삭제 불가**다.
- 피드백은 **비동기 처리**되며, 이 API 는 LLM 응답을 기다리지 않고 즉시 반환한다.

#### `성공` Response 200

```json
{
    "letterId": 16,
    "createdAt": "2025-11-01T21:00:00"
}
```

- [필수] `letterId` (Long): 편지 ID
- [필수] `createdAt` (LocalDateTime): 저장시각

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
| `LETTER_001` | 400 | `content` 가 `null` / 빈 값 |
| `LETTER_003` | 400 | 500자 초과 |
| `LETTER_004` | 400 | 한글 포함 |
| `LETTER_005` | 400 | `letterDate` 가 오늘이 아님 |

---

### 4.2 `GET /api/v1/letters/{letterId}`

> **API 설명**
> - 특정 편지의 상세 내용과 도착한 피드백(교정문, 팁 등)을 조회합니다.

#### Request

**Endpoint**

```
/api/v1/letters/{letterId}
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6ImV1bmplb25nIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3MzEyMjg1ODIsImV4cCI6MTczMTIyODYxOH0.ZCvhG9hWexsMg-dpJrtFQBpYbXIwmWgZrYk6AlugQVU
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
- [필수] `date` (LocalDate): 작성 날짜 (`yyyy-MM-dd`)
- [필수] `originalContent` (String): 원본 편지 내용
- [필수] `status` (String): 편지 상태 (`SUBMITTED`, `FEEDBACK_COMPLETED`) — [Q1](#8-확인-필요-항목)
- [선택] `feedback` (Object): 피드백 정보 (**피드백 생성 전일 경우 `null`**)
    - [필수] `feedbackId` (Long): 피드백 ID
    - [필수] `correctedContent` (String): 교정된 전체 내용
    - [필수] `tips` (Array&lt;String&gt;): 피드백 팁 목록 (0~3개, 없으면 `[]`) — [Q3](#8-확인-필요-항목)
    - [필수] `correctionSegments` (Array): 교정 세그먼트 리스트
        - [필수] `sequence` (Integer): 문장 내 순서 (1부터 시작)
        - [필수] `originalText` (String): 원본 텍스트 조각
        - [필수] `correctedText` (String): 교정된 텍스트 조각
        - [필수] `type` (String): 수정 여부 (`UNCHANGED`, `MODIFIED`)

**렌더링 계약**

- `correctionSegments` 를 `sequence` 순서대로 이어붙이면 **`originalText` 는 원문 전체**, **`correctedText` 는 `correctedContent` 전체**와 정확히 일치한다. 앱은 별도 인덱스 계산 없이 순차 렌더링만 하면 된다.
- `type == "UNCHANGED"` → 검은 글씨 그대로 출력
- `type == "MODIFIED"` → `originalText` 를 빨간 취소선, 바로 뒤에 `correctedText` 를 초록 하이라이트로 출력
- 공백은 세그먼트 문자열에 포함해 내려준다 (앱에서 공백을 임의로 추가하지 않음).
- 삭제 제안은 `type == "MODIFIED"` + `correctedText == ""` 로 표현한다.

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

**부수 효과**: 조회 성공 시 해당 편지를 **읽음 처리(`isRead = true`)** 한다. 별도의 읽음 처리 API 는 두지 않는다.

---

### 4.3 `GET /api/v1/letters`

> **API 설명**
> - 홈 화면에 진입했을 때 보여질 편지 리스트를 조회합니다.

#### Request

**Endpoint**

```
/api/v1/letters
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6ImV1bmplb25nIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3MzEyMjg1ODIsImV4cCI6MTczMTIyODYxOH0.ZCvhG9hWexsMg-dpJrtFQBpYbXIwmWgZrYk6AlugQVU
```

**Query Parameter**

- [선택] `page`: 페이지 번호 (기본값 0)
- [선택] `size`: 페이지 크기 (기본값 10)
- [선택] `sort`: 정렬 기준 (`LATEST` 기본값, `OLDEST`) — 홈 화면의 `최신순 / 오래된 순` 토글용 ([Q4](#8-확인-필요-항목))

#### `성공` Response 200

```json
{
    "nickname": "Sally",
    "totalStampCount": 3,
    "letters": [
        {
            "letterId": 15,
            "date": "2025-11-01",
            "summary": "I got flowers from a friend today. It really ...",
            "status": "FEEDBACK_COMPLETED",
            "isRead": false,
            "stampImage": "https://s3.../flower_stamp.png"
        },
        {
            "letterId": 12,
            "date": "2025-10-30",
            "summary": "Hi! Jolly. I made a new friend at a Halloween ...",
            "status": "FEEDBACK_COMPLETED",
            "isRead": true,
            "stampImage": "https://s3.../pumpkin_stamp.png"
        }
    ],
    "hasNext": true
}
```

- [필수] `nickname` (String): 유저 닉네임
- [필수] `totalStampCount` (Integer): 모은 우표 총 개수
- [필수] `letters` (Array): 편지 목록
    - [필수] `letterId` (Long): 편지 ID
    - [필수] `date` (LocalDate): 작성 날짜 (`yyyy-MM-dd`)
    - [필수] `summary` (String): 편지 내용 요약 (목록 노출용, 원문 앞 30자)
    - [필수] `status` (String): 편지 상태
        - `SUBMITTED`: 제출됨 (피드백 대기중)
        - `FEEDBACK_COMPLETED`: 피드백 완료
    - [필수] `isRead` (Boolean): 피드백 확인 여부 (`false`면 NEW 뱃지 노출)
    - [선택] `stampImage` (String): 우표 이미지 URL (피드백 완료 전에는 `null`)
- [필수] `hasNext` (Boolean): 다음 페이지 존재 여부

**클라이언트 렌더링 규칙**

| 조건 | 표시 |
| --- | --- |
| `status != FEEDBACK_COMPLETED` | 회색 `soon` 우표, `ic_more_sm` 미노출, **터치 불가** |
| `status == FEEDBACK_COMPLETED` | `stampImage` 표시, `ic_more_sm` 노출, **카드 전체 영역** 터치 시 상세 이동 |
| `status == FEEDBACK_COMPLETED && isRead == false` | 날짜 앞 빨간 점 |

- `totalStampCount` 는 **작성한 편지 수가 아니라** 피드백이 완료되어 우표가 도착한 편지 수다. (편지 3건 작성 + 1건 피드백 대기 → `totalStampCount = 2`)
- `SUBMITTED` 항목이 있으면 앱이 화면 재진입 · 새로고침 시 재조회해 상태 변화를 확인한다 (폴링 전용 API 없음).

#### `실패` Error Response

```json
{
    "status": 404,
    "code": "USER_001",
    "message": "사용자를 찾을 수 없습니다."
}
```

---

### 4.4 `GET /api/v1/home`

> **API 설명**
> - 홈 화면에 진입했을 때 보여질 유저 정보(닉네임, 모은 우표 수)를 조회합니다.

#### Request

**Endpoint**

```
/api/v1/home
```

**Authorization 헤더**

```
Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6ImV1bmplb25nIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3MzEyMjg1ODIsImV4cCI6MTczMTIyODYxOH0.ZCvhG9hWexsMg-dpJrtFQBpYbXIwmWgZrYk6AlugQVU
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

> ⚠️ 초안에서 이 API 의 Endpoint 가 `/api/v1/letters` 로, Response 가 편지 리스트 응답으로 복사되어 있었다. 위 내용으로 정정했다. `/api/v1/letters` 와 응답 필드가 중복되는 문제는 [Q5](#8-확인-필요-항목) 참고.

---

## 5. version

### 5.1 `GET /api/v1/version`

> **API 설명**
> - 앱 최소 지원 버전과 정책 페이지 URL 을 조회합니다.

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
- 공지사항 · 개인정보처리방침은 별도 API 없이 **웹뷰 링크**로 처리한다.

---

## 6. Enum 정의

| Enum | 값 | 비고 |
| --- | --- | --- |
| `Provider` | `KAKAO`, `APPLE` | |
| `TermsType` | `SERVICE`, `PRIVACY`, `MARKETING` | `MARKETING` 만 선택 동의 |
| `Status` (letter) | `SUBMITTED`, `FEEDBACK_COMPLETED` | 내부 상태 `FEEDBACK_FAILED` 는 앱에 `SUBMITTED` 로 내려보냄 — [Q1](#8-확인-필요-항목) |
| `SegmentType` | `UNCHANGED`, `MODIFIED` | |
| `Sort` | `LATEST`, `OLDEST` | |

---

## 7. 에러 코드

### 7.1 AUTH

| code | status | message |
| --- | --- | --- |
| `AUTH_001` | 400 | 지원하지 않는 로그인 방식입니다. |
| `AUTH_002` | 401 | 소셜 로그인 인증에 실패했습니다. |
| `AUTH_003` | 502 | 소셜 로그인 서버와 통신하지 못했습니다. |
| `AUTH_004` | 401 | 만료된 토큰입니다. |
| `AUTH_005` | 401 | 유효하지 않은 토큰입니다. |
| `AUTH_006` | 403 | 접근 권한이 없습니다. |

### 7.2 USER

| code | status | message |
| --- | --- | --- |
| `USER_001` | 404 | 사용자를 찾을 수 없습니다. |
| `USER_002` | 400 | 필수 약관에 모두 동의해야 합니다. |
| `USER_003` | 400 | 닉네임은 공백, 특수기호, 한글 없이 작성해야 합니다. |
| `USER_004` | 400 | 닉네임은 1자 이상 20자 이하여야 합니다. |
| `USER_005` | 400 | 온보딩을 먼저 완료해야 합니다. |

### 7.3 LETTER

| code | status | message |
| --- | --- | --- |
| `LETTER_001` | 400 | 편지 내용은 null일 수 없습니다. |
| `LETTER_002` | 404 | 존재하지 않는 편지입니다. |
| `LETTER_003` | 400 | 편지 내용은 500자를 초과할 수 없습니다. |
| `LETTER_004` | 400 | 편지는 영어로만 작성할 수 있습니다. |
| `LETTER_005` | 400 | 편지 날짜가 올바르지 않습니다. |

### 7.4 COMMON

| code | status | message |
| --- | --- | --- |
| `COMMON_001` | 400 | 잘못된 요청입니다. |
| `COMMON_002` | 404 | 요청하신 경로를 찾을 수 없습니다. |
| `COMMON_003` | 405 | 지원하지 않는 요청 방식입니다. |
| `COMMON_004` | 429 | 요청이 너무 많습니다. 잠시 후 다시 시도해주세요. |
| `COMMON_005` | 500 | 일시적인 오류가 발생했습니다. |

---

## 8. 확인 필요 항목

초안 검토 중 발견한 충돌 · 결정 필요 사항. **구현 착수 전 확정 필요**.

### Q1. `status` enum 값이 초안 내에서 충돌한다 ⚠️

- JSON 예시: `"status": "FEEDBACK_COMPLETED"`
- 필드 설명: `DRAFT`(작성중), `SUBMITTED`(제출됨), `ANALYZED`(피드백완료)

→ 같은 필드에 두 가지 값 체계가 섞여 있다. **본 문서는 JSON 예시를 따라 `FEEDBACK_COMPLETED` 로 통일**했다.

추가로, MVP 에는 **임시저장 기능이 없고**(작성 → 전달하기 → 수정 불가) 편지는 생성 즉시 피드백이 트리거되므로 `DRAFT` 는 사용되지 않는다. 제안하는 최종 값:

| 값 | 의미 | 앱 표시 |
| --- | --- | --- |
| `SUBMITTED` | 제출됨, 피드백 대기 | 회색 `soon` 우표 |
| `FEEDBACK_COMPLETED` | 피드백 완료 | 우표 노출, 상세 진입 가능 |
| `FEEDBACK_FAILED` | (내부용) 피드백 실패 | 앱에는 `SUBMITTED` 로 내려 실패를 노출하지 않음 |

### Q2. `letterDate` 를 클라이언트가 보내는 게 맞는가?

작성 화면의 `DATE:` 는 **터치 · 변경이 불가**하다(기획 주석). 즉 항상 오늘 날짜다.

- **권장**: 요청 필드는 유지하되 **서버가 KST 기준 오늘과 일치하는지 검증**(`LETTER_005`)한다. 과거/미래 날짜로 기록을 조작할 수 없게 된다.
- 대안: 필드를 아예 받지 않고 서버 시각으로 설정. 단, 자정 근처에서 앱 표시 날짜와 어긋날 수 있다.

### Q3. `tip` 이 단일 String 이면 디자인을 구현할 수 없다 ⚠️

디자인에 **`검토하기-팁없음` / `팁단일` / `팁여러개`** 세 가지 상태가 있고, 팁여러개 시안은 팁 카드가 2개 분리되어 있다.

- 초안: `tip` (String)
- **본 문서: `tips` (Array&lt;String&gt;)** 로 변경 — 0개면 `[]`(팁 영역 미표시), n개면 카드 n개

단일 String 을 유지하면 앱이 개행 기준으로 쪼개야 해서 취약하다.

### Q4. 정렬 파라미터가 초안에 없다

홈 화면에 `최신순 / 오래된 순` 토글이 있다. `sort` 쿼리 파라미터를 추가했다. 클라이언트 정렬로 처리할 경우 페이징과 충돌하므로 **서버 정렬 권장**.

### Q5. `/api/v1/letters` 와 `/api/v1/home` 의 응답이 중복된다

초안에서는 `/letters` 응답에도 `nickname`, `totalStampCount` 가 포함되어 있다. 두 가지 선택지:

| 안 | 내용 | 장단점 |
| --- | --- | --- |
| **A (현재 문서)** | `/letters` 에 헤더 정보 포함 + `/home` 도 유지 | 홈 진입 시 **1회 호출**로 끝남. `/home` 은 위젯 등 확장용으로 남김 |
| B | `/letters` 는 목록만, `/home` 은 헤더만 | 책임 분리는 깔끔하나 홈 진입 시 항상 2회 호출 |

→ 화면 성능상 **A 권장**. B 로 간다면 `/letters` 응답에서 `nickname`, `totalStampCount` 를 제거해야 한다.

### Q6. 토큰 재발급 · 로그아웃 API 가 없다 ⚠️

- **재발급**: Access Token 만료가 30분이므로 `POST /api/v1/auth/reissue` 가 없으면 30분마다 재로그인해야 한다. **MVP 필수로 판단**.
- **로그아웃**: 설정 화면에 버튼이 있다. 서버에서 Refresh Token 을 무효화하려면 `POST /api/v1/auth/logout` 이 필요하고, 클라이언트 토큰 폐기만으로 처리한다면 API 는 불필요하다.

### Q7. 기타

| 항목 | 내용 |
| --- | --- |
| `POST /letter` 응답 코드 | 초안은 `200`. REST 관례상 `201 Created` 이나, **초안대로 200 유지**했다 |
| `/letter` vs `/letters` | 생성만 단수라 혼동 여지가 있다. `POST /api/v1/letters` 통일 검토 |
| `summary` 길이 | 원문 앞 **30자**로 가정했다. 디자인 확정 시 조정 필요 |
| `stampImage` | S3 URL 방식이면 CDN 캐싱 헤더 설정 필요. 앱 로컬 리소스(enum) 방식 대비 네트워크 비용이 있다 |
| 하루 작성 개수 제한 | 현재 명세는 **제한 없음**. 하루 1통 제한 시 `LETTER_006` 추가 필요 |
