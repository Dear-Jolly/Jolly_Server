---
name: spring-entity
description: JPA 엔티티(도메인 클래스)를 만들거나 수정할 때 사용. 어노테이션 순서, 메서드 배치 순서, @Column의 nullable/unique/length 필수 지정, 테이블·컬럼 네이밍, 연관관계 편의 메서드 규칙을 다룬다. "엔티티 만들어줘", "테이블 추가", "연관관계 매핑" 요청에 해당.
---

# Entity (Domain) 작성 규칙

## 어노테이션 순서 (클래스 레벨)

```
@Entity → @Table(name = "XXX") → @Getter → @NoArgsConstructor(access = AccessLevel.PROTECTED)
```

`@Setter`는 절대 사용하지 않는다. 상태 변경은 의미 있는 이름의 비즈니스 메서드로만 한다.

## 메서드 배치 순서 (중요)

아래 순서를 그대로 지키고, 구분 주석으로 섹션을 나눈다.

1. `@PrePersist` / `@PreUpdate` 관련 (protected)
2. 생성 메서드 (public static)
3. 연관관계 편의 메서드 (public)
4. 비즈니스 메서드 (public)
5. 생성자 (private)
6. 검증 메서드 (private)

## 컬럼 규칙

- `@Column`의 **`nullable` 옵션은 필수로 고려하여 지정**한다.
- `unique` 제약이 필요한지 항상 검토하고, 필요하면 명시한다.
- **`String`(varchar) 타입은 `length`를 필수로 지정**한다. DB 저장 공간 최적화 목적.
- Enum 필드는 `@Enumerated(EnumType.STRING)` + `length` 지정. `ORDINAL` 금지.
- PK는 `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`, 필드명은 `id`, 컬럼명은 `{테이블단수}_id`.

## 네이밍

- 클래스명: 파스칼 케이스 (`Users`)
- 테이블명: 대문자 스네이크 케이스 (`@Table(name = "USERS")`)
- 필드명: 카멜 케이스 (`socialId`)
- 컬럼명: 스네이크 케이스 (`@Column(name = "social_id")`)

## 연관관계

- 양방향 연관관계일 경우 **연관관계 편의 메서드는 한 쪽에만** 배치한다 (보통 주인이 아닌 쪽/부모 쪽).
- 컬렉션 필드는 선언과 동시에 초기화한다 (`= new ArrayList<>()`).
- `@ManyToOne`은 기본적으로 `fetch = FetchType.LAZY`를 지정한다.

## 예시

```java
@Entity
@Table(name = "USERS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "social_id", nullable = false, length = 30)
    private String socialId;

    @Column(name = "provider", nullable = false, length = 10)
    private String provider;

    @Column(name = "email", nullable = false, length = 50)
    private String email;

    @Column(name = "nickname", nullable = false, length = 15)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Letters> letters = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ========= 생성 메서드 =========
    public static Users createMember(String socialId, String provider, String email, String nickname) {
        return new Users(socialId, provider, email, nickname, ROLE_USER);
    }

    public static Users createAdmin(String socialId, String provider, String email, String nickname) {
        return new Users(socialId, provider, email, nickname, ROLE_ADMIN);
    }

    // ========= 연관 관계 메서드 =========
    public void addLetter(Letters letter) {
        this.letters.add(letter);
    }

    // ========= 비즈니스 메서드 =========
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    // ========= 생성자 =========
    private Users(String socialId, String provider, String email, String nickname, Role role) {
        validateEmail(email);
        this.socialId = socialId;
        this.provider = provider;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
    }

    // ========= 검증 메서드 =========
    private void validateEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
    }
}
```

## 연계

- Enum은 도메인의 `enums` 패키지에 둔다 → `spring-architecture` 스킬
- 엔티티 ↔ DTO 변환 → `spring-dto` 스킬
