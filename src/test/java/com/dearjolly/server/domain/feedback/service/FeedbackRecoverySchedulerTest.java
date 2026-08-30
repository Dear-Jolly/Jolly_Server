package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_IN_PROGRESS;
import static com.dearjolly.server.domain.letter.enums.Status.SUBMITTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.service.LetterCreatedEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class FeedbackRecoverySchedulerTest {
    private static final Long LETTER_ID = 1L;

    @Mock
    private LetterRepository letterRepository;

    @Mock
    private FeedbackStateService feedbackStateService;

    @Mock
    private FeedbackWorker feedbackWorker;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TaskScheduler feedbackRetryScheduler;

    @InjectMocks
    private FeedbackRecoveryScheduler feedbackRecoveryScheduler;

    @DisplayName("예약 시각이 지난 편지는 인메모리 예약을 잃었더라도 0시 배치가 다시 발행한다.")
    @Test
    void publishDueLetterWhoseReservationWasLost() {
        // given
        when(letterRepository.findDueFeedbackIds(eq(SUBMITTED), any(LocalDateTime.class)))
                .thenReturn(List.of(LETTER_ID));
        when(letterRepository.findStalledFeedbackIds(eq(FEEDBACK_IN_PROGRESS), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // when
        feedbackRecoveryScheduler.recoverLostFeedback();

        // then
        verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof LetterCreatedEvent created && created.letterId().equals(LETTER_ID)));
    }

    @DisplayName("오래 멈춰 있던 편지는 재시도 예산을 써서 다시 예약한다.")
    @Test
    void rescheduleStalledLetter() {
        // given
        LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(2);
        when(letterRepository.findDueFeedbackIds(eq(SUBMITTED), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(letterRepository.findStalledFeedbackIds(eq(FEEDBACK_IN_PROGRESS), any(LocalDateTime.class)))
                .thenReturn(List.of(LETTER_ID));
        when(feedbackStateService.recoverStalled(eq(LETTER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(FeedbackFailureResult.retryScheduled(2, nextRetryAt, 120));
        when(feedbackStateService.getLogContext(LETTER_ID)).thenReturn(FeedbackLogContext.unknown(LETTER_ID));

        // when
        feedbackRecoveryScheduler.recoverLostFeedback();

        // then
        verify(feedbackRetryScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @DisplayName("복구 대상이 재시도 예산을 소진했으면 다시 예약하지 않는다.")
    @Test
    void doNotRescheduleExhaustedStalledLetter() {
        // given
        when(letterRepository.findDueFeedbackIds(eq(SUBMITTED), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(letterRepository.findStalledFeedbackIds(eq(FEEDBACK_IN_PROGRESS), any(LocalDateTime.class)))
                .thenReturn(List.of(LETTER_ID));
        when(feedbackStateService.recoverStalled(eq(LETTER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(FeedbackFailureResult.failed(5));
        when(feedbackStateService.getLogContext(LETTER_ID)).thenReturn(FeedbackLogContext.unknown(LETTER_ID));

        // when
        feedbackRecoveryScheduler.recoverLostFeedback();

        // then
        verify(feedbackRetryScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
