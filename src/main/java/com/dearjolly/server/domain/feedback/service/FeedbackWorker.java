package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.global.logging.LogValueSanitizer.sanitize;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackWorker {
    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");

    private final FeedbackRequester feedbackRequester;
    private final FeedbackStateService feedbackStateService;
    private final TaskScheduler feedbackRetryScheduler;

    public void process(Long letterId) {
        FeedbackLogContext context = contextOf(letterId);
        if (!feedbackStateService.start(letterId)) {
            log.info(
                    "feedback_job_skipped userId={} nickname={} letterId={} status={} reason=already_claimed_or_not_submitted",
                    context.userId(), sanitize(context.nickname()), letterId, context.status()
            );
            return;
        }
        log.info(
                "feedback_job_started userId={} nickname={} letterId={} attempt={} retryCount={}",
                context.userId(), sanitize(context.nickname()), letterId, context.retryCount() + 1,
                context.retryCount()
        );
        try {
            feedbackRequester.requestFeedback(letterId);
        } catch (RuntimeException exception) {
            handleFailure(letterId, context, exception);
        }
    }

    private void handleFailure(Long letterId, FeedbackLogContext context, RuntimeException exception) {
        FeedbackFailureResult result = feedbackStateService.handleFailure(letterId);
        FeedbackLogContext current = contextOf(letterId);
        Throwable rootCause = rootCauseOf(exception);
        if (result.failed()) {
            log.error(
                    "feedback_job_failed userId={} nickname={} letterId={} status={} retryCount={} "
                            + "causeType={} causeMessage={} rootCauseType={} rootCauseMessage={}",
                    context.userId(), sanitize(context.nickname()), letterId, current.status(), result.retryCount(),
                    exception.getClass().getSimpleName(), sanitize(exception.getMessage()),
                    rootCause.getClass().getSimpleName(), sanitize(rootCause.getMessage()), exception
            );
            return;
        }
        if (result.retryScheduled()) {
            scheduleRetry(letterId, result.nextRetryAt());
            log.warn(
                    "feedback_retry_registered userId={} nickname={} letterId={} status={} retryCount={} "
                            + "nextRetryAt={} delaySeconds={} causeType={} causeMessage={} rootCauseType={} "
                            + "rootCauseMessage={}",
                    context.userId(), sanitize(context.nickname()), letterId, current.status(), result.retryCount(),
                    result.nextRetryAt(), result.delaySeconds(), exception.getClass().getSimpleName(),
                    sanitize(exception.getMessage()), rootCause.getClass().getSimpleName(),
                    sanitize(rootCause.getMessage())
            );
            return;
        }
        log.info("feedback_failure_ignored letterId={} reason=state_changed", letterId);
    }

    private void scheduleRetry(Long letterId, java.time.LocalDateTime nextRetryAt) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        Instant executionTime = nextRetryAt.atZone(SERVER_ZONE).toInstant();
        try {
            feedbackRetryScheduler.schedule(
                    () -> runWithMdc(mdcContext, () -> process(letterId)),
                    executionTime
            );
        } catch (RuntimeException exception) {
            log.error(
                    "feedback_in_memory_retry_lost letterId={} nextRetryAt={} causeType={} causeMessage={} "
                            + "recovery=next_midnight",
                    letterId, nextRetryAt, exception.getClass().getSimpleName(), sanitize(exception.getMessage()),
                    exception
            );
        }
    }

    private FeedbackLogContext contextOf(Long letterId) {
        FeedbackLogContext context = feedbackStateService.getLogContext(letterId);
        return context == null ? FeedbackLogContext.unknown(letterId) : context;
    }

    private Throwable rootCauseOf(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    private void runWithMdc(Map<String, String> context, Runnable task) {
        try {
            if (context != null) {
                MDC.setContextMap(context);
            }
            task.run();
        } finally {
            MDC.clear();
        }
    }

}
