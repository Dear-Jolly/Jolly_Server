---
name: spring-test
description: 테스트 코드를 작성할 때 사용. 계층별 테스트 전략(통합/서비스/레포지터리), 어떤 테스트를 언제 쓸지 판단 기준, RestAssured 통합 테스트·Mockito 단위 테스트·H2 레포지터리 테스트 작성 방법을 다룬다. API를 새로 추가했다면 반드시 이 스킬을 함께 적용한다.
---

# 테스트 작성 규칙

## 계층별 테스트 전략

| 테스트 유형 | 도구/방법 | 작성 기준 | 검증 내용 |
|---|---|---|---|
| 통합 테스트 | RestAssured (E2E) | **API 하나당 필수** | 상태 코드, 응답값, DTO |
| 서비스 테스트 | Repository 목킹 (Mockito) | 복잡한 비즈니스 로직이 있을 때 | 비즈니스 로직 |
| 레포지터리 테스트 | H2 DB | 복잡한 쿼리(JPQL 등)가 있을 때 | 쿼리 정확성 |

판단 기준:
- **API를 추가/수정했다면 통합 테스트는 예외 없이 작성한다.**
- 단순 위임 수준의 로직이면 서비스 테스트·레포지터리 테스트는 생략한다.
- 분기·검증·계산이 얽힌 로직, 직접 작성한 JPQL/QueryDSL 쿼리가 있으면 해당 계층 테스트를 추가한다.
- 테스트 DB는 H2를 사용한다.

## 공통 규칙

- 클래스명: `{대상}ApiTest`, `{대상}ServiceTest`, `{대상}RepositoryTest`
- 모든 테스트 메서드에 `@DisplayName`으로 한국어 설명을 붙인다.
  - 통합 테스트: `"GET /projects : 프로젝트 목록 조회 API"` (메서드 + 경로 + 설명)
  - 단위 테스트: `"프로젝트를 저장한다."` (평서형)
- 본문은 `// given` / `// when` / `// then` 주석으로 구획을 나눈다.
- 테스트 클래스/메서드에 `public` 제어자를 붙이지 않는다.
- 성공 케이스만이 아니라 **실패 케이스(검증 실패, 존재하지 않는 리소스)도 함께 검증**한다.

## 통합 테스트 (RestAssured)

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectApiTest {

    @LocalServerPort
    private int port;

    @Autowired
    ProjectRepository projectRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @DisplayName("GET /projects : 프로젝트 목록 조회 API")
    @Test
    void findAllProjects() {
        // given - 테스트 데이터 준비
        // when - API 호출
        // then - 응답 검증 (상태 코드, 응답값, DTO)
    }
}
```

- `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)`로 테스트 간 격리를 보장한다.
- 상태 코드만 확인하고 끝내지 않는다. **응답 본문 필드까지 검증**한다.
- 실패 응답은 `ErrorResponse` 포맷(`status`, `code`, `message`)을 검증한다.

## 서비스 단위 테스트 (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    @DisplayName("프로젝트를 저장한다.")
    @Test
    void save() {
        // given - Mock 설정
        // when - 서비스 메서드 호출
        // then - 비즈니스 로직 검증
    }
}
```

- 예외 검증은 `assertThatThrownBy(...).isInstanceOf(BusinessException.class)` 형태로 `ErrorCode`까지 확인한다.
- `@SpringBootTest`를 쓰지 않는다. 순수 단위 테스트로 유지한다.

## 레포지터리 테스트

- `@DataJpaTest` + H2로 작성한다.
- 직접 작성한 JPQL/네이티브 쿼리, 복잡한 조건 조회에만 작성한다. Spring Data가 생성해 주는 기본 메서드는 테스트하지 않는다.

## 의존성 확인

RestAssured / H2 테스트를 새로 작성한다면 `build.gradle`에 아래가 있는지 먼저 확인하고, 없으면 추가한다.

```gradle
testImplementation 'io.rest-assured:rest-assured'
testRuntimeOnly 'com.h2database:h2'
```
