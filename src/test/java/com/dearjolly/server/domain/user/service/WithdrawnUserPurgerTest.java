package com.dearjolly.server.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.support.ApiTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WithdrawnUserPurgerTest extends ApiTestSupport {
    @Autowired
    private WithdrawnUserPurger withdrawnUserPurger;

    @DisplayName("탈퇴 계정과 그 편지 · 피드백 · 약관 동의를 함께 지운다.")
    @Test
    void purge() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-purge", "jolly");
        Letters letter = 피드백완료_편지를_저장한다(user, "A letter to purge.", LocalDate.of(2025, 11, 1), "purge-stamp");
        피드백을_붙여_저장한다(letter, "A letter to purge.",
                List.<String[]>of(new String[]{"A letter", "A letter"}), List.of("팁 하나"));

        // when
        withdrawnUserPurger.purge(List.of(user.getId()));

        // then
        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(행수("letters")).isZero();
        assertThat(행수("feedbacks")).isZero();
        assertThat(행수("feedback_tips")).isZero();
        assertThat(행수("correction_segments")).isZero();
        assertThat(행수("terms_agreements")).isZero();
    }

    @DisplayName("계정이 늘어도 삭제 쿼리 수는 늘지 않는다.")
    @Test
    void purgeDoesNotIssueQueryPerUser() {
        // given
        Users one = 온보딩을_마친_유저를_저장한다("kakao-bulk-1", "jolly");
        피드백완료_편지를_저장한다(one, "One.", LocalDate.of(2025, 11, 1), "bulk-stamp-1");
        long 계정_하나일_때 = 실행된_쿼리수(() -> withdrawnUserPurger.purge(List.of(one.getId())));

        List<Long> many = new ArrayList<>();
        for (int i = 2; i <= 6; i++) {
            Users user = 온보딩을_마친_유저를_저장한다("kakao-bulk-" + i, "jolly" + i);
            피드백완료_편지를_저장한다(user, "Letter " + i + ".", LocalDate.of(2025, 11, i), "bulk-stamp-" + i);
            many.add(user.getId());
        }

        // when
        long 계정_다섯일_때 = 실행된_쿼리수(() -> withdrawnUserPurger.purge(many));

        // then
        assertThat(계정_다섯일_때).isEqualTo(계정_하나일_때);
    }

    private long 행수(String table) {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM " + table)
                .getSingleResult()).longValue();
    }
}
