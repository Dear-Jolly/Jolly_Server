package com.dearjolly.server.domain.feedback.service;

import java.time.LocalDateTime;

public record FeedbackFailureResult(
        boolean retryScheduled,
        boolean failed,
        int retryCount,
        LocalDateTime nextRetryAt,
        long delaySeconds
) {
    public static FeedbackFailureResult retryScheduled(
            int retryCount, LocalDateTime nextRetryAt, long delaySeconds
    ) {
        return new FeedbackFailureResult(true, false, retryCount, nextRetryAt, delaySeconds);
    }

    public static FeedbackFailureResult failed(int retryCount) {
        return new FeedbackFailureResult(false, true, retryCount, null, 0);
    }

    public static FeedbackFailureResult ignored(int retryCount) {
        return new FeedbackFailureResult(false, false, retryCount, null, 0);
    }
}
