---
name: spring-exception
description: 예외를 던지거나 새 에러 코드가 필요할 때 사용. ErrorCode Enum 정의 규칙, BusinessException 사용법, ErrorResponse 통일 응답 포맷, GlobalExceptionHandler 확장 방법을 다룬다. "예외 처리", "에러 코드 추가", "404 응답", "validation 실패 응답" 요청에 해당.
---

# 예외 처리 규칙

## 3원칙

1. 모든 예외 코드는 `global`의 **`ErrorCode` Enum에 정의**해서 관리한다. 새 예외가 필요하면 이 Enum에 상수를 추가한다.
2. 비즈니스 로직에서 예외 발생 시 **`BusinessException`에 `ErrorCode`를 담아 던진다**.
3. 응답은 **`ErrorResponse`를 통해 통일된 JSON 포맷**으로 나간다.

## 위치

```
global/exception/
├── exception/BusinessException.java
├── response/ErrorCode.java
├── response/ErrorResponse.java
├── handler/GlobalExceptionHandler.java
└── controller/CustomErrorController.java
```

## ErrorCode 추가

`(HttpStatus, 코드, 메시지)` 3요소를 갖는다. 코드는 `{도메인}_{일련번호}` 규칙 (`AUTH_001`, `USER_001`, `LETTER_001`, `COMMON_001`).
도메인별 주석 블록(`auth` / `user` / `letter` / `common`)으로 그룹을 나눠 두었으니, **새 상수는 해당 도메인 블록 맨 뒤에 다음 번호로 추가**한다.

> **코드·상태·메시지의 정본은 [docs/API명세.md](../../../docs/API명세.md) §7 이다.**
> 새 코드가 필요하면 문서를 먼저 고치고 Enum 을 맞춘다. 번호는 한 번 부여하면 재사용하지 않는다.

```java
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // auth
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH_001", "지원하지 않는 로그인 방식입니다."),
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_005", "유효하지 않은 토큰입니다."),

    // user
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
    ONBOARDING_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "USER_005", "온보딩을 먼저 완료해야 합니다."),

    // letter
    LETTER_NOT_FOUND(HttpStatus.NOT_FOUND, "LETTER_002", "존재하지 않는 편지입니다."),

    // common
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_005", "일시적인 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
```

**추가 전에 반드시 기존 상수를 먼저 확인**한다. 의미가 같은 코드가 이미 있으면 재사용한다.

## 예외 던지기

```java
// 기본
throw new BusinessException(ErrorCode.USER_NOT_FOUND);

// 상세 메시지 추가 (ErrorCode 메시지 뒤에 " : {detail}"로 붙는다)
throw new BusinessException(ErrorCode.LETTER_NOT_FOUND, "letterId=15");

// Optional 조회 관용구
Users user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
```

- 던지는 위치는 **Service 계층**이 기본이다.
- Entity 내부 검증 메서드에서는 `IllegalArgumentException`을 쓴다 (도메인 불변식 위반).
- Controller에서 try-catch로 예외를 잡지 않는다. `GlobalExceptionHandler`가 처리한다.

## 응답 포맷

```json
{
  "status": 404,
  "code": "USER_001",
  "message": "사용자를 찾을 수 없습니다."
}
```

## GlobalExceptionHandler

`@RestControllerAdvice`로 이미 아래를 처리하고 있다. 새 핸들러가 필요할 때만 추가한다.

| 예외 | 처리 |
|---|---|
| `BusinessException` | 담긴 `ErrorCode` 그대로 응답 |
| `MethodArgumentNotValidException` | `@Valid` 실패 → `INVALID_REQUEST` + 필드별 메시지 조합 |
| `MethodArgumentTypeMismatchException` | 파라미터 타입 불일치 → `INVALID_REQUEST` |
| `HttpMessageNotReadableException` | JSON 파싱 실패 → `INVALID_REQUEST` |
| `HttpRequestMethodNotSupportedException` | 미지원 메서드 → `METHOD_NOT_ALLOWED` |
| `NoResourceFoundException` | 미정의 경로 → `PATH_NOT_FOUND` |
| `RuntimeException` | 그 외 전부 → `INTERNAL_SERVER_ERROR` |

핸들러 추가 시 규칙:
- 반환 타입은 `ResponseEntity<ErrorResponse>`, 생성은 `ErrorResponse.toResponseEntity(...)`로 통일.
- `log.error(...)`를 남긴다. 예상 가능한 예외는 메시지만, 예상 못한 예외는 스택 트레이스까지.
- 더 구체적인 예외 핸들러가 위, 포괄적인 것(`RuntimeException`)이 아래에 오도록 배치한다.
