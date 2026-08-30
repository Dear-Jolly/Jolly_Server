package com.dearjolly.server.domain.feedback.service;

public class NonRetryableFeedbackException extends RuntimeException {
    public NonRetryableFeedbackException(String message) {
        super(message);
    }
}
