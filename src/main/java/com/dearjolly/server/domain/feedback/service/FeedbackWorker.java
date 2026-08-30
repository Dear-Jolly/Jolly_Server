package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.global.logging.LogValueSanitizer.sanitize;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
            Duration.ofMinutes(5)
    );

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
                "feedback_job_started userId={} nickname={} letterId={} attempt={} retryCount={} recoveryCount={}",
                context.userId(), sanitize(context.nickname()), letterId, context.retryCount() + 1,
                context.retryCount(), context.recoveryCount()
        );
        try {
            feedbackRequester.requestFeedback(letterId);
        } catch (RuntimeException exception) {
            handleFailure(letterId, context, exception);
        }
    }

    private void handleFailure(Long letterId, FeedbackLogContext context, RuntimeException exception) {
        boolean retryable = !(exception instanceof NonTransientAiException)
                && !(exception instanceof NonRetryableFeedbackException);
        FeedbackFailureResult result = feedbackStateService.handleFailure(letterId, retryable);
        FeedbackLogContext current = contextOf(letterId);
        Throwable rootCause = rootCauseOf(exception);
        if (!result.shouldRetry()) {
            if (result.failed()) {
                log.error(
                        "feedback_job_failed userId={} nickname={} letterId={} status={} retryCount={} "
                                + "recoveryCount={} retryable={} causeType={} causeMessage={} rootCauseType={} "
                                + "rootCauseMessage={}",
                        context.userId(), sanitize(context.nickname()), letterId, current.status(), result.retryCount(),
                        current.recoveryCount(), retryable, exception.getClass().getSimpleName(),
                        sanitize(exception.getMessage()), rootCause.getClass().getSimpleName(),
                        sanitize(rootCause.getMessage()), exception
                );
            } else {
                log.warn(
                        "feedback_job_awaiting_recovery userId={} nickname={} letterId={} status={} retryCount={} "
                                + "recoveryCount={} causeType={} causeMessage={} rootCauseType={} rootCauseMessage={}",
                        context.userId(), sanitize(context.nickname()), letterId, current.status(), result.retryCount(),
                        current.recoveryCount(), exception.getClass().getSimpleName(), sanitize(exception.getMessage()),
                        rootCause.getClass().getSimpleName(), sanitize(rootCause.getMessage())
                );
            }
            return;
        }

        Duration delay = RETRY_DELAYS.get(result.retryCount() - 1);
        try {
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            feedbackRetryScheduler.schedule(
                    () -> runWithMdc(mdcContext, () -> process(letterId)),
                    Instant.now().plus(delay)
            );
            log.warn(
                    "feedback_retry_scheduled userId={} nickname={} letterId={} status={} retryCount={} "
                            + "recoveryCount={} delaySeconds={} causeType={} causeMessage={} rootCauseType={} "
                            + "rootCauseMessage={}",
                    context.userId(), sanitize(context.nickname()), letterId, current.status(), result.retryCount(),
                    current.recoveryCount(), delay.toSeconds(), exception.getClass().getSimpleName(),
                    sanitize(exception.getMessage()), rootCause.getClass().getSimpleName(), sanitize(rootCause.getMessage())
            );
        } catch (RuntimeException schedulingException) {
            feedbackStateService.fail(letterId);
            log.error(
                    "feedback_retry_scheduling_failed userId={} nickname={} letterId={} retryCount={} "
                            + "causeType={} causeMessage={}",
                    context.userId(), sanitize(context.nickname()), letterId, result.retryCount(),
                    schedulingException.getClass().getSimpleName(), sanitize(schedulingException.getMessage()),
                    schedulingException
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
