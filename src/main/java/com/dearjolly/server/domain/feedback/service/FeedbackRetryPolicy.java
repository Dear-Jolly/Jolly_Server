package com.dearjolly.server.domain.feedback.service;

import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FeedbackRetryPolicy {
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(1)
    );

    public boolean isExhausted(int retryCount) {
        return retryCount >= RETRY_DELAYS.size();
    }

    public Duration nextDelay(int retryCount) {
        if (isExhausted(retryCount)) {
            throw new IllegalArgumentException("재시도 횟수를 모두 소진했습니다.");
        }
        return RETRY_DELAYS.get(retryCount);
    }
}
