package com.dearjolly.server.domain.feedback.service;

import java.util.List;

public record LlmFeedback(
        String correctedContent,
        List<String> tips,
        String stampName,
        String model
) {
}
