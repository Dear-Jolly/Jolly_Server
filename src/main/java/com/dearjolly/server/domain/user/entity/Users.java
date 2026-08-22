package com.dearjolly.server.domain.user.entity;

import static com.dearjolly.server.domain.user.enums.Role.ROLE_ADMIN;
import static com.dearjolly.server.domain.user.enums.Role.ROLE_USER;
import static com.dearjolly.server.domain.user.enums.UserStatus.ACTIVE;
import static com.dearjolly.server.domain.user.enums.UserStatus.WITHDRAWN;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.domain.user.enums.Role;
import com.dearjolly.server.domain.user.enums.UserStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "USERS",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_oauth",
                columnNames = {"oauth_provider", "oauth_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Users {

    private static final int OAUTH_ID_MAX_LENGTH = 255;
    private static final String WITHDRAWN_OAUTH_ID_SUFFIX = "#withdrawn#";
    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false, length = 10)
    private OauthProvider oauthProvider;

    @Column(name = "oauth_id", nullable = false, length = OAUTH_ID_MAX_LENGTH)
    private String oauthId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "nickname", length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    /** 소셜 provider 가 발급한 refresh token. Apple 연결 해제(revoke) 에 필요하다. */
    @Column(name = "oauth_refresh_token", length = 500)
    private String oauthRefreshToken;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TermsAgreements> termsAgreements = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Letters> letters = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========= 생성 메서드 =========

    /**
     * 소셜 로그인 최초 진입 시 생성한다. 닉네임은 온보딩에서 채우므로 null 로 시작한다.
     * 이메일은 provider 가 주지 않을 수 있어 null 을 허용한다.
     */
    public static Users create(OauthProvider oauthProvider, String oauthId, String email) {
        return new Users(oauthProvider, oauthId, email, ROLE_USER);
    }

    public static Users createAdmin(OauthProvider oauthProvider, String oauthId, String email) {
        return new Users(oauthProvider, oauthId, email, ROLE_ADMIN);
    }

    // ========= 연관관계 메서드 =========
    public void addTermsAgreement(TermsAgreements termsAgreement) {
        this.termsAgreements.add(termsAgreement);
    }

    public void addLetter(Letters letter) {
        this.letters.add(letter);
    }

    // ========= 비즈니스 로직 메서드 =========
    public void updateNickname(String nickname) {
        validateNickname(nickname);
        this.nickname = nickname;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void clearRefreshToken() {
        this.refreshToken = null;
    }

    public void updateOauthRefreshToken(String oauthRefreshToken) {
        this.oauthRefreshToken = oauthRefreshToken;
    }

    /**
     * 탈퇴 요청 시점의 처리다. 데이터는 남기고 접근만 차단한다.
     * 실제 행 삭제는 유예기간(30일)이 지난 뒤 배치가 수행한다.
     */
    public void withdraw() {
        this.status = WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
        this.refreshToken = null;
        this.oauthRefreshToken = null;
    }

    /**
     * 유예기간 중인 계정이 점유한 (oauth_provider, oauth_id) UNIQUE 를 비운다.
     * 같은 소셜 계정으로 재로그인할 때 신규 가입 행을 만들 수 있게 하기 위한 처리다.
     */
    public void releaseOauthIdForRejoin() {
        if (!isWithdrawn()) {
            throw new IllegalStateException("탈퇴 상태가 아닌 계정의 식별자는 치환할 수 없습니다.");
        }
        String suffix = WITHDRAWN_OAUTH_ID_SUFFIX + deletedAt.atZone(SERVER_ZONE).toInstant().toEpochMilli();
        int prefixLength = Math.min(this.oauthId.length(), OAUTH_ID_MAX_LENGTH - suffix.length());
        this.oauthId = this.oauthId.substring(0, prefixLength) + suffix;
    }

    public boolean isWithdrawn() {
        return this.status == WITHDRAWN;
    }

    public boolean isNicknameRegistered() {
        return this.nickname != null;
    }

    // ========= 생성자 =========
    private Users(OauthProvider oauthProvider, String oauthId, String email, Role role) {
        validateOauthProvider(oauthProvider);
        validateOauthId(oauthId);
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
        this.email = email;
        this.role = role;
        this.status = ACTIVE;
    }

    // ========= 검증 메서드 =========
    private void validateOauthProvider(OauthProvider oauthProvider) {
        if (oauthProvider == null) {
            throw new IllegalArgumentException("소셜 로그인 제공자는 필수입니다.");
        }
    }

    private void validateOauthId(String oauthId) {
        if (oauthId == null || oauthId.isBlank()) {
            throw new IllegalArgumentException("소셜 회원 식별자는 필수입니다.");
        }
    }

    private void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("닉네임은 필수입니다.");
        }
    }
}
