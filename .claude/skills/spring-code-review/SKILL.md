---
name: spring-code-review
description: 백엔드 코드를 리뷰하거나 PR을 점검할 때 사용. 리뷰 관점(컨벤션 준수, 가독성, 예외 처리, 테스트 커버리지, 설계·확장성, API/DB/보안), 리뷰 코멘트 작성 방식, 계층별 자가 점검 체크리스트를 다룬다. "리뷰해줘", "PR 확인", "이 코드 괜찮아?" 요청에 해당.
---

# 코드 리뷰 기준

## 리뷰 관점

1. **컨벤션 준수** — 계층별 스킬(`spring-controller`, `spring-service`, `spring-entity`, `spring-dto`, `spring-exception`, `spring-test`)의 규칙을 기준으로 본다.
2. **가독성** — 네이밍, 메서드 길이, 중복, 불필요한 로직.
3. **예외 처리** — 공통 예외 처리 체계(`ErrorCode` / `BusinessException`)를 벗어난 곳이 없는지.
4. **테스트** — 누락된 테스트 케이스가 있으면 **어떤 종류의 테스트가 필요한지 구체적으로 제안**한다 (API 테스트 / Service 단위 테스트 / Repository 테스트).
5. **설계** — 객체지향 원칙, 서비스·도메인 책임 분리, 확장성, 유지보수성.
6. **API / DB / 보안** — 엔드포인트 설계, 인덱스·제약조건, 민감 정보 노출.

## 코멘트 작성 방식

- 각 리뷰 포인트마다 **문제점 → 대안 → 장단점** 순으로 논리적으로 제시하고, 필요하면 예시 코드를 덧붙인다.
- 지적은 **해당 라인 범위**를 명시해서 남긴다.
- 리뷰가 과도하면 피로감을 준다. **꼭 필요한 부분에 집중**하고 나머지는 간단한 요약으로 넘긴다.
- 취향 차이는 지적하지 않는다. 컨벤션 문서에 근거가 있거나 실제 결함일 때만 지적한다.

## 계층별 자가 점검 체크리스트

### Controller
- [ ] 어노테이션 순서: `@Tag` → `@RestController` → `@RequestMapping` → `@RequiredArgsConstructor`
- [ ] `@Operation`, `@Parameter` 로 Swagger 문서화가 되어 있는가
- [ ] Request Body에 `@Valid`가 붙어 있는가
- [ ] `ResponseEntity.status(...).body(...)` Fluent API로 상태 코드를 명시했는가
- [ ] 비즈니스 로직이 새어 들어오지 않았는가 / Entity를 직접 반환하지 않는가

### Service
- [ ] 어노테이션 순서: `@Service` → `@RequiredArgsConstructor` → `@Transactional(readOnly = true)`
- [ ] **CUD 메서드에 `@Transactional`이 개별 적용되어 있는가** (가장 흔한 누락)
- [ ] 예외를 `BusinessException` + `ErrorCode`로 던지는가
- [ ] public → private 메서드 순서가 지켜졌는가

### Entity
- [ ] 어노테이션 순서와 메서드 배치 순서(PrePersist → 생성 → 연관관계 → 비즈니스 → 생성자 → 검증)를 지켰는가
- [ ] `@Column`에 `nullable` / `unique`를 지정했는가
- [ ] `String` 필드에 `length`를 지정했는가
- [ ] 테이블은 대문자 스네이크, 컬럼은 스네이크 케이스인가
- [ ] `@Setter`가 없는가 / 양방향 연관관계 편의 메서드가 한 쪽에만 있는가

### DTO
- [ ] `record`로 선언했는가
- [ ] Request에 검증 어노테이션이 적용됐는가 / 매직 넘버 대신 상수를 쓰는가
- [ ] Response에 `from()` 정적 팩토리가 있는가

### 예외
- [ ] 새 예외 코드를 `ErrorCode` Enum에 정의했는가 (기존 코드 재사용 여부 확인)
- [ ] 도메인 그룹 주석 블록 안에 배치했는가

### 테스트
- [ ] 추가/수정된 API마다 통합 테스트가 있는가 (**필수**)
- [ ] 상태 코드뿐 아니라 응답 값·DTO까지 검증하는가
- [ ] 실패 케이스가 포함됐는가
- [ ] 복잡한 비즈니스 로직 / 직접 작성한 쿼리에 해당 계층 테스트가 있는가
