package com.dearjolly.server.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearjolly.server.domain.user.entity.TermsAgreements;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.domain.user.enums.TermsType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TermsAgreementReaderTest {
    private final Users user = Users.create(OauthProvider.KAKAO, "kakao-1", "jolly@example.com");

    @DisplayName("같은 항목이 여러 번 쌓이면 가장 최신 행을 현재 상태로 본다.")
    @Test
    void toCurrentState() {
        List<TermsAgreements> history = List.of(
                TermsAgreements.create(user, TermsType.MARKETING, false, "1.0.0"),
                TermsAgreements.create(user, TermsType.MARKETING, true, "1.0.0")
        );

        Map<TermsType, Boolean> current = TermsAgreementReader.toCurrentState(history);

        assertThat(current.get(TermsType.MARKETING)).isFalse();
        assertThat(TermsAgreementReader.isMarketingAgreed(current)).isFalse();
    }

    @DisplayName("필수 약관 2건이 모두 동의 상태여야 통과한다.")
    @Test
    void isRequiredTermsAgreed() {
        List<TermsAgreements> history = List.of(
                TermsAgreements.create(user, TermsType.SERVICE, true, "1.0.0"),
                TermsAgreements.create(user, TermsType.PRIVACY, true, "1.0.0")
        );

        assertThat(TermsAgreementReader.isRequiredTermsAgreed(
                TermsAgreementReader.toCurrentState(history))).isTrue();
    }

    @DisplayName("필수 약관 중 하나라도 빠지면 통과하지 못한다.")
    @Test
    void isRequiredTermsAgreedWithMissingType() {
        List<TermsAgreements> history = List.of(
                TermsAgreements.create(user, TermsType.SERVICE, true, "1.0.0")
        );

        assertThat(TermsAgreementReader.isRequiredTermsAgreed(
                TermsAgreementReader.toCurrentState(history))).isFalse();
    }

    @DisplayName("필수 약관을 철회하면 통과하지 못한다.")
    @Test
    void isRequiredTermsAgreedAfterRevoke() {
        List<TermsAgreements> history = List.of(
                TermsAgreements.create(user, TermsType.SERVICE, false, "1.0.0"),
                TermsAgreements.create(user, TermsType.SERVICE, true, "1.0.0"),
                TermsAgreements.create(user, TermsType.PRIVACY, true, "1.0.0")
        );

        assertThat(TermsAgreementReader.isRequiredTermsAgreed(
                TermsAgreementReader.toCurrentState(history))).isFalse();
    }
}
