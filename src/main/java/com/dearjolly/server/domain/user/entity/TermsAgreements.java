package com.dearjolly.server.domain.user.entity;

import com.dearjolly.server.domain.user.enums.TermsType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약관 동의 이력. UPDATE 하지 않고 INSERT 만 하며, 현재 동의 상태는
 * (user_id, type) 별 agreed_at 이 가장 최신인 행의 agreed 값이다.
 */
@Entity
@Table(
        name = "TERMS_AGREEMENTS",
        indexes = @Index(name = "idx_terms_latest", columnList = "user_id, type, agreed_at DESC")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreements {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terms_agreement_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TermsType type;

    @Column(name = "agreed", nullable = false)
    private boolean agreed;

    @Column(name = "terms_version", nullable = false, length = 20)
    private String termsVersion;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    @PrePersist
    protected void onCreate() {
        this.agreedAt = LocalDateTime.now();
    }

    // ========= 생성 메서드 =========
    public static TermsAgreements create(Users user, TermsType type, boolean agreed, String termsVersion) {
        TermsAgreements agreement = new TermsAgreements(user, type, agreed, termsVersion);
        user.addTermsAgreement(agreement);
        return agreement;
    }

    // ========= 비즈니스 로직 메서드 =========
    public boolean isRequiredAndAgreed() {
        return this.type.isRequired() && this.agreed;
    }

    // ========= 생성자 =========
    private TermsAgreements(Users user, TermsType type, boolean agreed, String termsVersion) {
        validateType(type);
        validateTermsVersion(termsVersion);
        this.user = user;
        this.type = type;
        this.agreed = agreed;
        this.termsVersion = termsVersion;
    }

    // ========= 검증 메서드 =========
    private void validateType(TermsType type) {
        if (type == null) {
            throw new IllegalArgumentException("약관 종류는 필수입니다.");
        }
    }

    private void validateTermsVersion(String termsVersion) {
        if (termsVersion == null || termsVersion.isBlank()) {
            throw new IllegalArgumentException("약관 버전은 필수입니다.");
        }
    }
}
