package com.dearjolly.server.domain.letter.repository;

import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_IN_PROGRESS;
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

    @DisplayName("예약 시각이 지난 편지만 재시도 대상으로 조회한다.")
    @Test
    void findOnlyDueRetryReservations() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Letters due = saveLetter("retry-due");
        Letters future = saveLetter("retry-future");
        entityManager.flush();

        setNextRetryAt(due.getId(), now.minusSeconds(1));
        setNextRetryAt(future.getId(), now.plusHours(1));
        entityManager.clear();

        // when
        List<Long> targets = letterRepository.findDueFeedbackIds(SUBMITTED, now);

        // then
        assertThat(targets).contains(due.getId()).doesNotContain(future.getId());
    }

    @DisplayName("기준 시각보다 오래 멈춘 FEEDBACK_IN_PROGRESS 편지만 복구 대상으로 조회한다.")
    @Test
    void findOnlyStalledInProgressLetters() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Letters stalled = saveLetter("stalled");
        Letters running = saveLetter("running");
        entityManager.flush();

        setInProgressUpdatedAt(stalled.getId(), now.minusMinutes(16));
        setInProgressUpdatedAt(running.getId(), now.minusMinutes(1));
        entityManager.clear();

        // when
        List<Long> targets = letterRepository.findStalledFeedbackIds(
                FEEDBACK_IN_PROGRESS, now.minusMinutes(15)
        );

        // then
        assertThat(targets).contains(stalled.getId()).doesNotContain(running.getId());
    }

    @DisplayName("예약 시각이 남은 편지는 워커가 선점하지 못한다.")
    @Test
    void doNotClaimLetterBeforeItsRetryTime() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Letters future = saveLetter("claim-too-early");
        entityManager.flush();

        setNextRetryAt(future.getId(), now.plusHours(1));
        entityManager.clear();

        // when
        int claimed = letterRepository.startFeedback(future.getId(), SUBMITTED, FEEDBACK_IN_PROGRESS, now);

        // then
        assertThat(claimed).isZero();
    }

    private Letters saveLetter(String oauthId) {
        Users user = Users.create(OauthProvider.KAKAO, oauthId, oauthId + "@example.com");
        entityManager.persist(user);
        Letters letter = Letters.create(user, "letter", LocalDate.now(), ZoneId.of("Asia/Seoul"), null);
        entityManager.persist(letter);
        return letter;
    }

    private void setNextRetryAt(Long letterId, LocalDateTime nextRetryAt) {
        jdbcTemplate.update("UPDATE LETTERS SET next_retry_at = ? WHERE letter_id = ?", nextRetryAt, letterId);
    }

    private void setInProgressUpdatedAt(Long letterId, LocalDateTime updatedAt) {
        jdbcTemplate.update(
                "UPDATE LETTERS SET status = ?, next_retry_at = NULL, updated_at = ? WHERE letter_id = ?",
                FEEDBACK_IN_PROGRESS.name(), updatedAt, letterId
        );
    }
}
