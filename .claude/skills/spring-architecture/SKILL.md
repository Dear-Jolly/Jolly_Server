---
name: spring-architecture
description: 새 도메인/패키지를 만들거나 파일을 어디에 둘지 정할 때 사용. 도메인형 패키지 구조, 디렉터리 배치 규칙, 클래스 내 선언 순서(public → private), 네이밍 규칙을 다룬다. "새 도메인 추가", "패키지 어디에", "구조 잡아줘" 같은 요청에 해당.
---

# 아키텍처 & 패키지 구조

## 기술 스택 전제

- **언어**: Java 21
- **프레임워크**: Spring Boot 3.5.7, Spring Data JPA
- **DB**: MySQL (테스트는 H2)
- **테스트**: JUnit 5, Mockito, RestAssured
- **빌드**: Gradle

## 도메인형 패키지 구조

루트 패키지는 `com.dearjolly.server`. 그 아래 `domain`과 `global`로 나뉜다.

```
com.dearjolly.server/
├── domain/
│   ├── user/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   ├── enums/
│   │   ├── constants/        # 검증 상수 등 (필요 시)
│   │   └── dto/
│   │       ├── request/
│   │       └── response/
│   ├── letter/
│   ├── feedback/
│   └── skeleton/
└── global/
    └── exception/
        ├── controller/
        ├── exception/
        ├── handler/
        └── response/
```

### 배치 규칙

- 새 기능은 **먼저 어느 도메인에 속하는지** 정하고, 그 도메인 하위에 계층 패키지를 만든다.
- 계층 패키지는 필요한 것만 만든다. (조회 API가 없으면 `repository`를 미리 만들지 않는다)
- DTO는 반드시 도메인 내부 `dto` 아래 `request` / `response`로 나눈다. 공용 DTO를 만들지 말 것.
- 여러 도메인이 공유하는 것(예외, 설정, 공통 응답)만 `global`에 둔다.
- 매직 넘버/정규식 등 검증 상수는 도메인의 `constants` 패키지에 `final` 클래스로 모은다.

```java
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserValidationConstants {

    public static final int NICKNAME_MIN_LENGTH = 2;
    public static final int NICKNAME_MAX_LENGTH = 10;
    public static final String NICKNAME_REGEX = "^[a-zA-Z0-9]+$";
}
```

## 선언 순서

- **메서드**: `public` → `private` 순서로 작성한다. (Entity는 별도 규칙 → `spring-entity` 스킬 참고)
- **인터페이스 구현 클래스**: 인터페이스에 선언된 메서드 순서를 그대로 유지한다.
- **필드**: 주입 대상(`private final`)을 클래스 최상단에 모은다.

## 네이밍

| 대상 | 규칙 | 예시 |
|---|---|---|
| 클래스명 | 파스칼 케이스 | `Users`, `SkeletonService` |
| 변수/필드 | 카멜 케이스 | `socialId`, `createdAt` |
| DB 테이블 (`@Table(name=)`) | 대문자 스네이크 케이스 | `USERS`, `LETTERS` |
| DB 컬럼 (`@Column(name=)`) | 스네이크 케이스 | `social_id`, `created_at` |
| 상수 | 대문자 스네이크 케이스 | `NICKNAME_MAX_LENGTH` |

## 계층 간 의존 방향

`Controller → Service → Repository → Entity` 한 방향으로만 흐른다.

- Controller는 Repository를 직접 호출하지 않는다.
- Entity를 Controller 응답으로 그대로 반환하지 않는다. 항상 Response DTO로 변환한다.
- Service 메서드의 반환 타입은 Response DTO이거나 도메인 객체이며, Controller에 노출되는 것은 DTO다.
