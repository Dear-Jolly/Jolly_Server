package com.dearjolly.server.domain.feedback.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackWorker {
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10)
    );

    private final FeedbackRequester feedbackRequester;
    private final FeedbackStateService feedbackStateService;
    private final TaskScheduler feedbackRetryScheduler;

    public void process(Long letterId) {
        if (!feedbackStateService.start(letterId)) {
            return;
        }
        try {
            feedbackRequester.requestFeedback(letterId);
        } catch (RuntimeException exception) {
            handleFailure(letterId, exception);
        }
    }

    private void handleFailure(Long letterId, RuntimeException exception) {
        boolean retryable = !(exception instanceof NonTransientAiException)
                && !(exception instanceof NonRetryableFeedbackException);
        FeedbackFailureResult result = feedbackStateService.handleFailure(letterId, retryable);
        if (!result.shouldRetry()) {
            if (result.failed()) {
                log.error("피드백 생성에 최종 실패했다. letterId={}, retryCount={}, cause={}",
                        letterId, result.retryCount(), exception.getClass().getSimpleName(), exception);
            } else {
                log.warn("LLM 재시도를 소진해 보완 복구를 기다린다. letterId={}, retryCount={}, cause={}",
                        letterId, result.retryCount(), exception.getClass().getSimpleName());
            }
            return;
        }

        Duration delay = RETRY_DELAYS.get(result.retryCount() - 1);
        try {
            feedbackRetryScheduler.schedule(() -> process(letterId), Instant.now().plus(delay));
            log.warn("피드백 생성을 재시도한다. letterId={}, retryCount={}, delaySeconds={}, cause={}",
                    letterId, result.retryCount(), delay.toSeconds(), exception.getClass().getSimpleName());
        } catch (RuntimeException schedulingException) {
            feedbackStateService.fail(letterId);
            log.error("피드백 재시도 예약에 실패했다. letterId={}, retryCount={}",
                    letterId, result.retryCount(), schedulingException);
        }
    }
}
