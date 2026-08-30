package com.dearjolly.server.domain.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class FeedbackWorkerTest {
    private static final Long LETTER_ID = 1L;

    @Mock
    private FeedbackRequester feedbackRequester;

    @Mock
    private FeedbackStateService feedbackStateService;

    @Mock
    private TaskScheduler feedbackRetryScheduler;

    @InjectMocks
    private FeedbackWorker feedbackWorker;

    @DisplayName("실패하면 예약된 시각에 재시도를 인메모리로 등록한다.")
    @Test
    void scheduleRetryInMemoryOnFailure() {
        // given
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(30);
        when(feedbackStateService.start(LETTER_ID)).thenReturn(true);
        doThrow(new IllegalStateException("timeout")).when(feedbackRequester).requestFeedback(LETTER_ID);
        when(feedbackStateService.handleFailure(LETTER_ID))
                .thenReturn(FeedbackFailureResult.retryScheduled(1, nextRetryAt, 30));

        // when
        feedbackWorker.process(LETTER_ID);

        // then
        verify(feedbackRetryScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @DisplayName("재시도 예산을 소진한 실패는 다시 예약하지 않는다.")
    @Test
    void doNotRescheduleExhaustedFailure() {
        // given
        when(feedbackStateService.start(LETTER_ID)).thenReturn(true);
        doThrow(new IllegalStateException("timeout")).when(feedbackRequester).requestFeedback(LETTER_ID);
        when(feedbackStateService.handleFailure(LETTER_ID)).thenReturn(FeedbackFailureResult.failed(5));

        // when
        feedbackWorker.process(LETTER_ID);

        // then
        verify(feedbackRetryScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @DisplayName("다른 워커가 이미 선점했으면 LLM 을 호출하지 않는다.")
    @Test
    void skipWhenClaimFails() {
        // given
        when(feedbackStateService.start(LETTER_ID)).thenReturn(false);

        // when
        feedbackWorker.process(LETTER_ID);

        // then
        verify(feedbackRequester, never()).requestFeedback(LETTER_ID);
        verify(feedbackStateService, never()).handleFailure(LETTER_ID);
    }

    @DisplayName("최종 실패 로그에 사용자와 원인을 남긴다.")
    @Test
    void logFailureReason(CapturedOutput output) {
        // given
        when(feedbackStateService.getLogContext(LETTER_ID))
                .thenReturn(new FeedbackLogContext(LETTER_ID, 7L, "jolly", "SUBMITTED", 5, null))
                .thenReturn(new FeedbackLogContext(LETTER_ID, 7L, "jolly", "FEEDBACK_FAILED", 5, null));
        when(feedbackStateService.start(LETTER_ID)).thenReturn(true);
        doThrow(new IllegalStateException("OpenAI 호출 실패")).when(feedbackRequester).requestFeedback(LETTER_ID);
        when(feedbackStateService.handleFailure(LETTER_ID)).thenReturn(FeedbackFailureResult.failed(5));

        // when
        feedbackWorker.process(LETTER_ID);

        // then
        assertThat(output).contains("feedback_job_failed", "userId=7", "nickname=jolly", "OpenAI 호출 실패");
    }

    @DisplayName("재시도 예약 로그에 다음 시각과 대기 초를 남긴다.")
    @Test
    void logRetryReservation(CapturedOutput output) {
        // given
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(30);
        when(feedbackStateService.getLogContext(LETTER_ID))
                .thenReturn(new FeedbackLogContext(LETTER_ID, 7L, "jolly", "FEEDBACK_IN_PROGRESS", 0, null));
        when(feedbackStateService.start(LETTER_ID)).thenReturn(true);
        doThrow(new IllegalStateException("OpenAI 호출 실패")).when(feedbackRequester).requestFeedback(LETTER_ID);
        when(feedbackStateService.handleFailure(LETTER_ID))
                .thenReturn(FeedbackFailureResult.retryScheduled(1, nextRetryAt, 30));

        // when
        feedbackWorker.process(LETTER_ID);

        // then
        assertThat(output).contains("feedback_retry_registered", "retryCount=1", "delaySeconds=30");
    }
}
