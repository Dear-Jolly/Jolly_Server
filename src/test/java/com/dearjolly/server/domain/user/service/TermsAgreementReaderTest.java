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

/**
 * 동의 이력에서 현재 상태를 뽑는 규칙을 검증한다.
 * 이력 테이블이라 같은 (user, type) 조합이 여러 행 존재하는 것이 정상이고,
 * 그중 가장 최신 행만 현재 상태로 인정해야 한다.
 */
class TermsAgreementReaderTest {

    private final Users user = Users.create(OauthProvider.KAKAO, "kakao-1", "jolly@example.com");

    @DisplayName("같은 항목이 여러 번 쌓이면 가장 최신 행을 현재 상태로 본다.")
    @Test
    void toCurrentState() {
        // given - agreed_at DESC 로 정렬된 이력 (철회가 동의보다 최신)
        List<TermsAgreements> history = List.of(
                TermsAgreements.create(user, TermsType.MARKETING, false, "1.0.0"),
                TermsAgreements.create(user, TermsType.MARKETING, true, "1.0.0")
        );

        // when
        Map<TermsType, Boolean> current = TermsAgreementReader.toCurrentState(history);

        // then
        assertThat(current.get(TermsType.MARKETING)).isFalse();
        assertThat(TermsAgreementReader.isMarketingAgreed(current)).isFalse();
    }

    @DisplayName("필수 약관 2건이 모두 동의 상태여야 통과한다.")
    @Test
    void isRequiredTermsAgreed() {
        // given
        List<TermsAgreements> history = List.of(
                TermsAgreements.create(user, TermsType.SERVICE, true, "1.0.0"),
                TermsAgreements.create(user, TermsType.PRIVACY, true, "1.0.0")
        );

        // when & then
        assertThat(TermsAgreementReader.isRequiredTermsAgreed(
                TermsAgreementReader.toCurrentState(history))).isTrue();
    }

    @DisplayName("필수 약관 중 하나라도 빠지면 통과하지 못한다.")
    @Test
    void isRequiredTermsAgreedWithMissingType() {
        // given - PRIVACY 이력이 아예 없다
        List<TermsAgreements> history = List.of(
                TermsAgreements.create(user, TermsType.SERVICE, true, "1.0.0")
        );

        // when & then
        assertThat(TermsAgreementReader.isRequiredTermsAgreed(
                TermsAgreementReader.toCurrentState(history))).isFalse();
    }

    @DisplayName("필수 약관을 철회하면 통과하지 못한다.")
    @Test
    void isRequiredTermsAgreedAfterRevoke() {
        // given - SERVICE 를 철회한 행이 더 최신이다
        List<TermsAgreements> history = List.of(
                TermsAgreements.create(user, TermsType.SERVICE, false, "1.0.0"),
                TermsAgreements.create(user, TermsType.SERVICE, true, "1.0.0"),
                TermsAgreements.create(user, TermsType.PRIVACY, true, "1.0.0")
        );

        // when & then
        assertThat(TermsAgreementReader.isRequiredTermsAgreed(
                TermsAgreementReader.toCurrentState(history))).isFalse();
    }
}
