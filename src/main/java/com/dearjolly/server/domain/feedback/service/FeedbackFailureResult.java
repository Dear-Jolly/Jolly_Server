package com.dearjolly.server.domain.feedback.service;

public record FeedbackFailureResult(
        boolean shouldRetry,
        boolean failed,
        int retryCount
) {
    public static FeedbackFailureResult retry(int retryCount) {
        return new FeedbackFailureResult(true, false, retryCount);
    }

    public static FeedbackFailureResult awaitingRecovery(int retryCount) {
        return new FeedbackFailureResult(false, false, retryCount);
    }

    public static FeedbackFailureResult failed(int retryCount) {
        return new FeedbackFailureResult(false, true, retryCount);
    }
}
