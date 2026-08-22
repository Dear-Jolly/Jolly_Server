package com.dearjolly.server.domain.letter.dto.response;

import com.dearjolly.server.domain.letter.entity.Letters;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LetterCreateResponse(
        Long letterId,
        LocalDate date,
        LocalDateTime createdAt
) {
    public static LetterCreateResponse from(Letters letter) {
        return new LetterCreateResponse(
                letter.getId(),
                letter.getLetterDate(),
                letter.createdAtInWrittenZone()
        );
    }
}
