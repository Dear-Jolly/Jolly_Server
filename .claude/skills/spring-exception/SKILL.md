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

`(HttpStatus, 코드, 메시지)` 3요소를 갖는다. 코드는 `{도메인 앞글자}{HTTP 상태}` 규칙 (`U404`, `R409`, `G500`).
도메인별 주석 블록으로 그룹을 나눠 두었으니, **새 상수는 해당 도메인 블록 안에 추가**한다.

```java
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // global
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G500", "서버 내부에 문제가 발생했습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "G405", "허용되지 않는 메서드입니다."),

    // resource
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "R404", "요청한 리소스가 존재하지 않습니다."),
    RESOURCE_DUPLICATED(HttpStatus.CONFLICT, "R409", "중복해서 저장할 수 없습니다."),

    // user
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U404", "존재하지 않는 사용자입니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "U400", "올바르지 않은 값 또는 형식입니다.");

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
throw new BusinessException(ErrorCode.RESOURCE_DUPLICATED, "이미 사용 중인 닉네임입니다.");

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
  "code": "U404",
  "message": "존재하지 않는 사용자입니다."
}
```

## GlobalExceptionHandler

`@RestControllerAdvice`로 이미 아래를 처리하고 있다. 새 핸들러가 필요할 때만 추가한다.

| 예외 | 처리 |
|---|---|
| `BusinessException` | 담긴 `ErrorCode` 그대로 응답 |
| `MethodArgumentNotValidException` | `@Valid` 실패 → `INVALID_INPUT_VALUE` + 필드별 메시지 조합 |
| `MethodArgumentTypeMismatchException` | 파라미터 타입 불일치 → `INVALID_INPUT_VALUE` |
| `HttpMessageNotReadableException` | JSON 파싱 실패 → `INVALID_INPUT_VALUE` |
| `RuntimeException` | 그 외 전부 → `INTERNAL_SERVER_ERROR` |

핸들러 추가 시 규칙:
- 반환 타입은 `ResponseEntity<ErrorResponse>`, 생성은 `ErrorResponse.toResponseEntity(...)`로 통일.
- `log.error(...)`를 남긴다. 예상 가능한 예외는 메시지만, 예상 못한 예외는 스택 트레이스까지.
- 더 구체적인 예외 핸들러가 위, 포괄적인 것(`RuntimeException`)이 아래에 오도록 배치한다.
