package com.dearjolly.server.domain.feedback.service;

import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FeedbackRetryPolicy {
    private static final Duration STALLED_THRESHOLD = Duration.ofMinutes(15);

    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(1)
    );

    // 이 시간을 넘겨 FEEDBACK_IN_PROGRESS 에 머물면 워커가 죽은 것으로 본다.
    // 복구 배치와 관리자 재시도가 같은 기준을 써야 판정이 어긋나지 않는다.
    public Duration stalledThreshold() {
        return STALLED_THRESHOLD;
    }

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
