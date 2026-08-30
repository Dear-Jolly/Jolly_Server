package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_FAILED;
import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_IN_PROGRESS;
import static com.dearjolly.server.domain.letter.enums.Status.SUBMITTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.service.LetterCreatedEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class FeedbackRecoverySchedulerTest {
    private static final Long LETTER_ID = 1L;

    @Mock
    private LetterRepository letterRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FeedbackRecoveryScheduler feedbackRecoveryScheduler;

    @DisplayName("멈춘 작업을 조건부 복구하면 recovery_count를 소비하고 다시 큐에 넣는다.")
    @Test
    void recoverStalledFeedbackWithinRecoveryLimit() {
        // given
        when(letterRepository.findIdsByStatusAndUpdatedAtBefore(eq(SUBMITTED), any(LocalDateTime.class)))
                .thenReturn(List.of(LETTER_ID));
        when(letterRepository.findIdsByStatusAndUpdatedAtBefore(eq(FEEDBACK_IN_PROGRESS), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(letterRepository.recoverFeedback(
                eq(LETTER_ID), eq(SUBMITTED), eq(SUBMITTED),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(2)
        )).thenReturn(1);

        // when
        feedbackRecoveryScheduler.recoverStalledFeedback();

        // then
        verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof LetterCreatedEvent letterEvent && letterEvent.letterId().equals(LETTER_ID)
        ));
        verify(letterRepository, never()).failExhaustedRecovery(
                any(), any(), any(), any(), any(), anyInt()
        );
    }

    @DisplayName("기존 워커가 먼저 완료해 복구 조건이 사라지면 새 이벤트를 발행하지 않는다.")
    @Test
    void skipWhenExistingWorkerAlreadyCompleted() {
        // given
        when(letterRepository.findIdsByStatusAndUpdatedAtBefore(eq(SUBMITTED), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(letterRepository.findIdsByStatusAndUpdatedAtBefore(eq(FEEDBACK_IN_PROGRESS), any(LocalDateTime.class)))
                .thenReturn(List.of(LETTER_ID));
        when(letterRepository.recoverFeedback(
                eq(LETTER_ID), eq(FEEDBACK_IN_PROGRESS), eq(SUBMITTED),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(2)
        )).thenReturn(0);
        when(letterRepository.failExhaustedRecovery(
                eq(LETTER_ID), eq(FEEDBACK_IN_PROGRESS), eq(FEEDBACK_FAILED),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(2)
        )).thenReturn(0);

        // when
        feedbackRecoveryScheduler.recoverStalledFeedback();

        // then
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
