package com.dearjolly.server.domain.feedback.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
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

    @DisplayName("일시적인 OpenAI 오류는 30초 후 첫 재시도를 예약한다.")
    @Test
    void scheduleRetryForTransientFailure() {
        // given
        when(feedbackStateService.start(LETTER_ID)).thenReturn(true);
        org.mockito.Mockito.doThrow(new TransientAiException("timeout"))
                .when(feedbackRequester).requestFeedback(LETTER_ID);
        when(feedbackStateService.handleFailure(LETTER_ID, true))
                .thenReturn(FeedbackFailureResult.retry(1));

        // when
        feedbackWorker.process(LETTER_ID);

        // then
        verify(feedbackRetryScheduler).schedule(any(Runnable.class), any(Instant.class));
        verify(feedbackStateService, never()).fail(LETTER_ID);
    }

    @DisplayName("재시도 불가능한 OpenAI 오류는 재예약하지 않는다.")
    @Test
    void finishForNonTransientFailure() {
        // given
        when(feedbackStateService.start(LETTER_ID)).thenReturn(true);
        org.mockito.Mockito.doThrow(new NonTransientAiException("invalid api key"))
                .when(feedbackRequester).requestFeedback(LETTER_ID);
        when(feedbackStateService.handleFailure(LETTER_ID, false))
                .thenReturn(FeedbackFailureResult.failed(0));

        // when
        feedbackWorker.process(LETTER_ID);

        // then
        verify(feedbackRetryScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @DisplayName("이미 다른 워커가 처리 중인 편지는 실행하지 않는다.")
    @Test
    void skipWhenClaimFails() {
        // given
        when(feedbackStateService.start(LETTER_ID)).thenReturn(false);

        // when
        feedbackWorker.process(LETTER_ID);

        // then
        verify(feedbackRequester, never()).requestFeedback(eq(LETTER_ID));
        verify(feedbackStateService, never()).handleFailure(eq(LETTER_ID), any(Boolean.class));
    }

    @DisplayName("피드백 실패 로그에 사용자와 실제 실패 메시지를 기록한다.")
    @Test
    void logFailureReason(CapturedOutput output) {
        // given
        when(feedbackStateService.getLogContext(LETTER_ID))
                .thenReturn(new FeedbackLogContext(LETTER_ID, 7L, "jolly", "SUBMITTED", 0, 0))
                .thenReturn(new FeedbackLogContext(LETTER_ID, 7L, "jolly", "FEEDBACK_FAILED", 0, 0));
        when(feedbackStateService.start(LETTER_ID)).thenReturn(true);
        org.mockito.Mockito.doThrow(new NonRetryableFeedbackException("선택 가능한 우표가 없습니다."))
                .when(feedbackRequester).requestFeedback(LETTER_ID);
        when(feedbackStateService.handleFailure(LETTER_ID, false))
                .thenReturn(FeedbackFailureResult.failed(0));

        // when
        feedbackWorker.process(LETTER_ID);

        // then
        assertThat(output).contains(
                "feedback_job_failed",
                "userId=7",
                "nickname=jolly",
                "causeMessage=선택 가능한 우표가 없습니다."
        );
    }
}
