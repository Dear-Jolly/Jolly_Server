---
name: spring-service
description: 비즈니스 로직(Service 계층)을 작성하거나 수정할 때 사용. 어노테이션 순서, @Transactional(readOnly = true) 기본 적용과 CUD 메서드 개별 @Transactional 규칙, 예외 처리 위치를 다룬다. "서비스 로직", "비즈니스 로직 추가", "트랜잭션" 요청에 해당.
---

# Service 작성 규칙

## 어노테이션 순서 (클래스 레벨)

```
@Service → @RequiredArgsConstructor → @Transactional(readOnly = true)
```

## 트랜잭션 규칙 (중요)

- 클래스 레벨에 **`@Transactional(readOnly = true)`를 기본으로 적용**한다.
- 데이터 변경(Create/Update/Delete)이 일어나는 메서드에만 **개별적으로 `@Transactional`을 추가**한다.
- 조회 전용 메서드에는 아무것도 붙이지 않는다 (클래스 레벨 설정이 적용됨).
- 변경 메서드에 `@Transactional`을 빠뜨리면 `readOnly = true`가 적용되어 쓰기가 실패하거나 무시된다. 반드시 확인할 것.

## 필수 규칙

- 의존성은 `private final` 필드 + `@RequiredArgsConstructor` 생성자 주입.
- 비즈니스 예외는 이 계층에서 던진다. `throw new BusinessException(ErrorCode.XXX)` 형태.
- 엔티티 → Response DTO 변환은 Response DTO의 `from()` 정적 팩토리로 위임한다.
- 엔티티 생성은 Entity의 정적 생성 메서드(`create...`)를 쓴다. 서비스에서 `new`로 직접 만들지 않는다.
- 변경 감지(dirty checking)를 활용한다. 조회 후 엔티티의 비즈니스 메서드를 호출하면 `save()` 재호출이 필요 없다.
- public 메서드를 먼저, private 헬퍼 메서드를 뒤에 배치한다.

## 예시

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkeletonService {

    private final SkeletonRepository skeletonRepository;

    /**
     * Skeleton 전체 조회
     */
    public SkeletonListResponse getSkeletons() {
        // 조회 로직 (클래스 레벨 readOnly = true 적용됨)
    }

    /**
     * Skeleton 생성
     */
    @Transactional
    public SkeletonGetResponse createSkeleton(SkeletonCreateRequest request) {
        if (skeletonRepository.existsByName(request.categoryName())) {
            throw new BusinessException(ErrorCode.RESOURCE_DUPLICATED);
        }

        Skeleton skeleton = skeletonRepository.save(request.toEntity());
        return SkeletonGetResponse.from(skeleton);
    }
}
```

## 조회 실패 처리 관용구

```java
Users user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
```

## 연계

- `ErrorCode` 추가/사용 → `spring-exception` 스킬
- 복잡한 비즈니스 로직이라면 Repository를 목킹한 서비스 단위 테스트 작성 → `spring-test` 스킬
