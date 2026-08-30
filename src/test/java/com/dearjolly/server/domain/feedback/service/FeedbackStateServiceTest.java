package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.domain.letter.constants.StampConstants.FAILED_STAMP_NAME;
import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_IN_PROGRESS;
import static com.dearjolly.server.domain.letter.enums.Status.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.entity.Stamps;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.repository.StampRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedbackStateServiceTest {
    private static final Long LETTER_ID = 1L;

    @Mock
    private LetterRepository letterRepository;

    @Mock
    private StampRepository stampRepository;

    @Mock
    private FeedbackRetryPolicy retryPolicy;

    @Mock
    private Letters letter;

    @Mock
    private Stamps failedStamp;

    @InjectMocks
    private FeedbackStateService feedbackStateService;

    @DisplayName("SUBMITTED 편지를 조건부 갱신으로 선점한다.")
    @Test
    void claimSubmittedLetter() {
        // given
        when(letterRepository.startFeedback(
                eq(LETTER_ID), eq(SUBMITTED), eq(FEEDBACK_IN_PROGRESS), any(LocalDateTime.class)
        )).thenReturn(1);

        // when
        boolean started = feedbackStateService.start(LETTER_ID);

        // then
        assertThat(started).isTrue();
    }

    @DisplayName("첫 실패는 실패 우표를 붙이고 다음 재시도를 예약한다.")
    @Test
    void scheduleFirstRetryWithFailedStamp() {
        // given
        when(letterRepository.findByIdForFeedback(LETTER_ID)).thenReturn(Optional.of(letter));
        when(letter.getStatus()).thenReturn(FEEDBACK_IN_PROGRESS);
        when(letter.getRetryCount()).thenReturn(0, 1);
        when(retryPolicy.isExhausted(0)).thenReturn(false);
        when(retryPolicy.nextDelay(0)).thenReturn(Duration.ofSeconds(30));
        when(stampRepository.findByName(FAILED_STAMP_NAME)).thenReturn(Optional.of(failedStamp));

        // when
        FeedbackFailureResult result = feedbackStateService.handleFailure(LETTER_ID);

        // then
        verify(letter).scheduleRetry(any(LocalDateTime.class), eq(failedStamp));
        verify(letter, never()).failFeedback();
        assertThat(result.retryScheduled()).isTrue();
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(result.delaySeconds()).isEqualTo(30);
    }

    @DisplayName("실패 우표 시드가 없어도 재시도 예약은 남긴다.")
    @Test
    void scheduleRetryWithoutFailedStampSeed() {
        // given
        when(letterRepository.findByIdForFeedback(LETTER_ID)).thenReturn(Optional.of(letter));
        when(letter.getStatus()).thenReturn(FEEDBACK_IN_PROGRESS);
        when(letter.getRetryCount()).thenReturn(0, 1);
        when(retryPolicy.isExhausted(0)).thenReturn(false);
        when(retryPolicy.nextDelay(0)).thenReturn(Duration.ofSeconds(30));
        when(stampRepository.findByName(FAILED_STAMP_NAME)).thenReturn(Optional.empty());

        // when
        FeedbackFailureResult result = feedbackStateService.handleFailure(LETTER_ID);

        // then
        verify(letter).scheduleRetry(any(LocalDateTime.class), isNull());
        assertThat(result.retryScheduled()).isTrue();
    }

    @DisplayName("5차 재시도까지 소진하면 FEEDBACK_FAILED 로 확정한다.")
    @Test
    void failAfterFifthRetry() {
        // given
        when(letterRepository.findByIdForFeedback(LETTER_ID)).thenReturn(Optional.of(letter));
        when(letter.getStatus()).thenReturn(FEEDBACK_IN_PROGRESS);
        when(letter.getRetryCount()).thenReturn(5);
        when(retryPolicy.isExhausted(5)).thenReturn(true);

        // when
        FeedbackFailureResult result = feedbackStateService.handleFailure(LETTER_ID);

        // then
        verify(letter).failFeedback();
        verify(letter, never()).scheduleRetry(any(), any());
        assertThat(result).isEqualTo(FeedbackFailureResult.failed(5));
    }

    @DisplayName("FEEDBACK_IN_PROGRESS 가 아닌 편지의 실패는 무시한다.")
    @Test
    void ignoreFailureOfLetterAlreadyMovedOn() {
        // given
        when(letterRepository.findByIdForFeedback(LETTER_ID)).thenReturn(Optional.of(letter));
        when(letter.getStatus()).thenReturn(SUBMITTED);
        when(letter.getRetryCount()).thenReturn(2);

        // when
        FeedbackFailureResult result = feedbackStateService.handleFailure(LETTER_ID);

        // then
        verify(letter, never()).scheduleRetry(any(), any());
        verify(letter, never()).failFeedback();
        assertThat(result).isEqualTo(FeedbackFailureResult.ignored(2));
    }

    @DisplayName("멈춘 편지의 복구는 실패와 같은 재시도 예산을 쓴다.")
    @Test
    void recoverStalledJobUsingSameRetryBudget() {
        // given
        LocalDateTime now = LocalDateTime.now();
        when(letterRepository.findByIdForFeedback(LETTER_ID)).thenReturn(Optional.of(letter));
        when(letter.getStatus()).thenReturn(FEEDBACK_IN_PROGRESS);
        when(letter.getUpdatedAt()).thenReturn(now.minusMinutes(16));
        when(letter.getRetryCount()).thenReturn(1, 2);
        when(retryPolicy.isExhausted(1)).thenReturn(false);
        when(retryPolicy.nextDelay(1)).thenReturn(Duration.ofMinutes(2));
        when(stampRepository.findByName(FAILED_STAMP_NAME)).thenReturn(Optional.of(failedStamp));

        // when
        FeedbackFailureResult result = feedbackStateService.recoverStalled(LETTER_ID, now.minusMinutes(15), now);

        // then
        verify(letter).scheduleRetry(any(LocalDateTime.class), eq(failedStamp));
        assertThat(result.retryScheduled()).isTrue();
        assertThat(result.retryCount()).isEqualTo(2);
    }

    @DisplayName("기준 시각 이후에 갱신된 편지는 멈춘 것으로 보지 않는다.")
    @Test
    void ignoreRecentlyUpdatedLetterOnRecovery() {
        // given
        LocalDateTime now = LocalDateTime.now();
        when(letterRepository.findByIdForFeedback(LETTER_ID)).thenReturn(Optional.of(letter));
        when(letter.getStatus()).thenReturn(FEEDBACK_IN_PROGRESS);
        when(letter.getUpdatedAt()).thenReturn(now.minusMinutes(1));
        when(letter.getRetryCount()).thenReturn(1);

        // when
        FeedbackFailureResult result = feedbackStateService.recoverStalled(LETTER_ID, now.minusMinutes(15), now);

        // then
        verify(letter, never()).scheduleRetry(any(), any());
        assertThat(result).isEqualTo(FeedbackFailureResult.ignored(1));
    }
}
