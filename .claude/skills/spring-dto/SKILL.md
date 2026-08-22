---
name: spring-dto
description: Request/Response DTO를 만들거나 수정할 때 사용. record 사용, 검증 어노테이션 적용, toEntity()/from() 정적 팩토리 메서드 규칙, 검증 상수 처리 방법을 다룬다. "요청 DTO", "응답 DTO", "validation 추가" 요청에 해당.
---

# DTO 작성 규칙

## 공통

- DTO는 **반드시 `record`로 선언**한다. 클래스 + Lombok 조합을 쓰지 않는다.
- 위치: `domain/{도메인}/dto/request/`, `domain/{도메인}/dto/response/`
- 네이밍: `{도메인}{동작}Request`, `{도메인}{동작}Response` (예: `UserCreateRequest`, `UserGetResponse`)
- 여러 도메인이 공유하는 DTO를 만들지 않는다. 도메인별로 각자 정의한다.

## Request DTO

- **Validation 어노테이션을 적용**한다. Controller에서 `@Valid`와 짝을 이룬다.
- 메시지는 한국어로 사용자에게 보여줄 수 있는 문구로 작성한다.
- 길이/정규식 같은 값은 매직 넘버로 두지 말고 도메인의 `constants` 클래스 상수를 static import 해서 쓴다.
- 필요 시 `toEntity()` 인스턴스 메서드로 엔티티 변환을 제공한다.

```java
public record ProjectCreateRequest(
        @NotBlank(message = "이메일 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Length(
                min = NICKNAME_MIN_LENGTH,
                max = NICKNAME_MAX_LENGTH,
                message = "닉네임은 {min}자 이상 {max}자 이하여야 합니다.")
        @Pattern(regexp = NICKNAME_REGEX, message = "올바른 닉네임 형식이 아닙니다.")
        String nickname,

        @NotBlank
        String description
) {
    public Users toEntity() {
        return Users.create("temp_social_id", "temp", email, nickname);
    }
}
```

### 자주 쓰는 검증 어노테이션

| 어노테이션 | 용도 |
|---|---|
| `@NotBlank` | 문자열 필수 (공백 문자열도 거부) |
| `@NotNull` | 객체/숫자 필수 |
| `@Email` | 이메일 형식 |
| `@Length(min, max)` / `@Size` | 길이 제한 |
| `@Pattern(regexp)` | 정규식 |
| `@Positive`, `@Min`, `@Max` | 숫자 범위 |

## Response DTO

- 엔티티 → DTO 변환은 **`from()` 정적 팩토리 메서드**로 제공한다.
- 목록 응답은 `{도메인}ListResponse`로 감싸고, 내부에 `List<{도메인}SummaryResponse>`를 담는다.
- 엔티티를 필드로 그대로 들고 있지 않는다. 필요한 값만 평면화해서 담는다.

```java
public record ProjectSummaryResponse(
        long id,
        String title,
        String thumbnail
) {
    // 엔티티를 응답 DTO로 변환: from() 정적 팩토리 메서드
    public static ProjectSummaryResponse from(Project project) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getTitle(),
                project.getThumbnail()
        );
    }
}
```

```java
public record ProjectListResponse(
        List<ProjectSummaryResponse> projects
) {
    public static ProjectListResponse from(List<Project> projects) {
        return new ProjectListResponse(
                projects.stream()
                        .map(ProjectSummaryResponse::from)
                        .toList()
        );
    }
}
```

## 연계

- 검증 실패 응답 포맷 → `spring-exception` 스킬
- `@Valid` 적용 위치 → `spring-controller` 스킬
