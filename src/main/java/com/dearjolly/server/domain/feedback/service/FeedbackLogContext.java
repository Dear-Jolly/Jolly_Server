package com.dearjolly.server.domain.feedback.service;

import com.dearjolly.server.domain.letter.entity.Letters;
import java.time.LocalDateTime;

public record FeedbackLogContext(
        Long letterId,
        Long userId,
        String nickname,
        String status,
        int retryCount,
        LocalDateTime nextRetryAt
) {
    public static FeedbackLogContext from(Letters letter) {
        return new FeedbackLogContext(
                letter.getId(),
                letter.getUser().getId(),
                letter.getUser().getNickname(),
                letter.getStatus().name(),
                letter.getRetryCount(),
                letter.getNextRetryAt()
        );
    }

    public static FeedbackLogContext unknown(Long letterId) {
        return new FeedbackLogContext(letterId, null, "unknown", "unknown", 0, null);
    }
}
