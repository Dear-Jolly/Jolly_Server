package com.dearjolly.server.domain.letter.dto.request;

import java.time.LocalDateTime;

public record LetterCreateRequest(
        String content,
        LocalDateTime writtenAt,
        String timeZone
) {
}
