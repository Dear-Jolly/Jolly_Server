package com.dearjolly.server.domain.letter.repository;

import static com.dearjolly.server.domain.letter.enums.Status.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class LetterRepositoryTest {
    @Autowired
    private LetterRepository letterRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DisplayName("첫 복구는 30분, 두 번째 복구는 1시간이 지난 편지만 조회한다.")
    @Test
    void findLettersAtRecoveryThreshold() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Letters firstReady = saveLetter("recovery-first-ready");
        Letters firstWaiting = saveLetter("recovery-first-waiting");
        Letters secondReady = saveLetter("recovery-second-ready");
        Letters secondWaiting = saveLetter("recovery-second-waiting");
        entityManager.flush();

        setRecoveryState(firstReady.getId(), 0, now.minusMinutes(31));
        setRecoveryState(firstWaiting.getId(), 0, now.minusMinutes(29));
        setRecoveryState(secondReady.getId(), 1, now.minusMinutes(61));
        setRecoveryState(secondWaiting.getId(), 1, now.minusMinutes(59));
        entityManager.clear();

        // when
        List<Long> targets = letterRepository.findIdsForFeedbackRecovery(
                SUBMITTED, now.minusMinutes(30), now.minusHours(1)
        );

        // then
        assertThat(targets).containsExactlyInAnyOrder(firstReady.getId(), secondReady.getId());
    }

    @DisplayName("복구 UPDATE도 recovery_count에 따라 30분과 1시간 기준을 적용한다.")
    @Test
    void recoverLetterAtRecoveryThreshold() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Letters firstWaiting = saveLetter("update-first-waiting");
        Letters secondWaiting = saveLetter("update-second-waiting");
        entityManager.flush();
        setRecoveryState(firstWaiting.getId(), 0, now.minusMinutes(29));
        setRecoveryState(secondWaiting.getId(), 1, now.minusMinutes(59));
        entityManager.clear();

        // when
        int firstUpdated = letterRepository.recoverFeedback(
                firstWaiting.getId(), SUBMITTED, SUBMITTED,
                now.minusMinutes(30), now.minusHours(1), now, 2
        );
        int secondUpdated = letterRepository.recoverFeedback(
                secondWaiting.getId(), SUBMITTED, SUBMITTED,
                now.minusMinutes(30), now.minusHours(1), now, 2
        );

        // then
        assertThat(firstUpdated).isZero();
        assertThat(secondUpdated).isZero();
    }

    private Letters saveLetter(String oauthId) {
        Users user = Users.create(OauthProvider.KAKAO, oauthId, oauthId + "@example.com");
        entityManager.persist(user);
        Letters letter = Letters.create(
                user, "I wrote a letter today.", LocalDate.now(), ZoneId.of("Asia/Seoul"), null
        );
        entityManager.persist(letter);
        return letter;
    }

    private void setRecoveryState(Long letterId, int recoveryCount, LocalDateTime updatedAt) {
        jdbcTemplate.update(
                "UPDATE LETTERS SET recovery_count = ?, updated_at = ? WHERE letter_id = ?",
                recoveryCount, updatedAt, letterId
        );
    }
}
