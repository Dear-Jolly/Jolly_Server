---
name: spring-controller
description: REST API 엔드포인트(Controller)를 작성하거나 수정할 때 사용. 어노테이션 순서, Swagger 문서화(@Tag/@Operation/@Parameter), @Valid 적용, ResponseEntity Fluent API 반환 규칙을 다룬다. "API 추가", "엔드포인트 만들어줘", "컨트롤러" 요청에 해당.
---

# Controller 작성 규칙

## 어노테이션 순서 (클래스 레벨)

반드시 이 순서를 지킨다.

```
@Tag → @RestController → @RequestMapping → @RequiredArgsConstructor
```

## 필수 규칙

- **클래스 레벨**
  - `@Tag(name, description)`으로 Swagger API 그룹을 명시한다.
  - `@RequestMapping`으로 기본 경로를 설정한다. 경로는 `/api/v1/{도메인}` 형태.
  - 의존성은 `private final` 필드 + `@RequiredArgsConstructor` 생성자 주입. `@Autowired` 필드 주입 금지.
- **메서드 레벨**
  - `@Operation(summary = "...")`으로 각 API 기능을 한 줄 요약한다.
  - HTTP 매핑은 `@GetMapping` / `@PostMapping` 등 축약형을 쓴다.
- **파라미터**
  - `@Parameter(description = "...")`로 설명을 추가한다. 필수 값이면 `required = true`.
  - Request Body에는 반드시 `@Valid`를 붙인다.
- **반환값**
  - `ResponseEntity`의 Fluent API(`status().body()`)로 상태 코드와 데이터를 **명시적으로** 반환한다.
  - `HttpStatus`는 static import 해서 `OK`, `CREATED`처럼 쓴다.
  - 조회는 `OK`, 생성은 `CREATED`, 본문 없는 삭제/수정은 `NO_CONTENT`.
- **금지**
  - Controller에 비즈니스 로직을 두지 않는다. 요청 위임과 응답 변환만 한다.
  - Repository를 직접 주입받지 않는다.
  - Entity를 그대로 반환하지 않는다.

## 예시

```java
@Tag(name = "스켈레톤", description = "스켈레톤 생성, 조회 API")
@RestController
@RequestMapping("/api/v1/skeleton")
@RequiredArgsConstructor
public class SkeletonController {

    private final SkeletonService skeletonService;

    @Operation(summary = "저장된 모든 스켈레톤 목록 조회")
    @GetMapping
    public ResponseEntity<SkeletonListResponse> getSkeletons(
            @Parameter(description = "조회 기준 시각 (생략 시 현재)")
            @RequestParam(required = false) LocalDateTime clientAt
    ) {
        return ResponseEntity
                .status(OK)
                .body(skeletonService.getSkeletons(clientAt));
    }

    @Operation(summary = "새로운 스켈레톤 생성")
    @PostMapping
    public ResponseEntity<SkeletonGetResponse> createSkeleton(
            @Parameter(description = "스켈레톤 생성 요청 객체", required = true)
            @Valid @RequestBody SkeletonCreateRequest request
    ) {
        return ResponseEntity
                .status(CREATED)
                .body(skeletonService.createSkeleton(request));
    }
}
```

## 연계

- 요청/응답 DTO 작성 → `spring-dto` 스킬
- 예외 응답 포맷 → `spring-exception` 스킬
- API를 추가했다면 **통합 테스트는 필수** → `spring-test` 스킬
