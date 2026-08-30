package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_IN_PROGRESS;
import static com.dearjolly.server.domain.letter.enums.Status.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
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
    private Letters letter;

    @InjectMocks
    private FeedbackStateService feedbackStateService;

    @DisplayName("SUBMITTED 편지를 조건부 갱신으로 선점한다.")
    @Test
    void claimSubmittedLetter() {
        // given
        when(letterRepository.startFeedback(
                org.mockito.ArgumentMatchers.eq(LETTER_ID),
                org.mockito.ArgumentMatchers.eq(SUBMITTED),
                org.mockito.ArgumentMatchers.eq(FEEDBACK_IN_PROGRESS),
                any(LocalDateTime.class)
        )).thenReturn(1);

        // when
        boolean started = feedbackStateService.start(LETTER_ID);

        // then
        assertThat(started).isTrue();
    }

    @DisplayName("일시 오류는 재시도 횟수를 올리고 SUBMITTED로 되돌린다.")
    @Test
    void retryTransientFailure() {
        // given
        when(letterRepository.findById(LETTER_ID)).thenReturn(Optional.of(letter));
        when(letter.getStatus()).thenReturn(FEEDBACK_IN_PROGRESS);
        when(letter.isRetryExhausted()).thenReturn(false);
        when(letter.getRetryCount()).thenReturn(1);

        // when
        FeedbackFailureResult result = feedbackStateService.handleFailure(LETTER_ID, true);

        // then
        verify(letter).retryFeedback();
        verify(letter, never()).failFeedback();
        assertThat(result).isEqualTo(FeedbackFailureResult.retry(1));
    }

    @DisplayName("재시도 불가능한 오류는 즉시 FEEDBACK_FAILED로 전환한다.")
    @Test
    void failNonRetryableFailure() {
        // given
        when(letterRepository.findById(LETTER_ID)).thenReturn(Optional.of(letter));
        when(letter.getStatus()).thenReturn(FEEDBACK_IN_PROGRESS);
        when(letter.getRetryCount()).thenReturn(0);

        // when
        FeedbackFailureResult result = feedbackStateService.handleFailure(LETTER_ID, false);

        // then
        verify(letter).failFeedback();
        verify(letter, never()).retryFeedback();
        assertThat(result).isEqualTo(FeedbackFailureResult.failed(0));
    }

    @DisplayName("세 번 재시도한 편지는 다음 실패에서 FEEDBACK_FAILED로 전환한다.")
    @Test
    void failAfterRetriesExhausted() {
        // given
        when(letterRepository.findById(LETTER_ID)).thenReturn(Optional.of(letter));
        when(letter.getStatus()).thenReturn(FEEDBACK_IN_PROGRESS);
        when(letter.isRetryExhausted()).thenReturn(true);
        when(letter.isRecoveryExhausted()).thenReturn(true);
        when(letter.getRetryCount()).thenReturn(3);

        // when
        FeedbackFailureResult result = feedbackStateService.handleFailure(LETTER_ID, true);

        // then
        verify(letter).failFeedback();
        verify(letter, never()).retryFeedback();
        assertThat(result).isEqualTo(FeedbackFailureResult.failed(3));
    }

    @DisplayName("LLM 재시도만 소진한 편지는 실패시키지 않고 보완 복구를 기다린다.")
    @Test
    void awaitRecoveryAfterLlmRetriesExhausted() {
        // given
        when(letterRepository.findById(LETTER_ID)).thenReturn(Optional.of(letter));
        when(letter.getStatus()).thenReturn(FEEDBACK_IN_PROGRESS);
        when(letter.isRetryExhausted()).thenReturn(true);
        when(letter.isRecoveryExhausted()).thenReturn(false);
        when(letter.getRetryCount()).thenReturn(3);

        // when
        FeedbackFailureResult result = feedbackStateService.handleFailure(LETTER_ID, true);

        // then
        verify(letter).awaitRecovery();
        verify(letter, never()).failFeedback();
        assertThat(result).isEqualTo(FeedbackFailureResult.awaitingRecovery(3));
    }
}
