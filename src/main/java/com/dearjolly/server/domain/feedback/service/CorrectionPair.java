package com.dearjolly.server.domain.feedback.service;

public record CorrectionPair(
        String originalText,
        String correctedText
) {
}
