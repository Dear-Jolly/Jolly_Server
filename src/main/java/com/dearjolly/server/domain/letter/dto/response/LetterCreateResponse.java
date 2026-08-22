package com.dearjolly.server.domain.letter.dto.response;

import com.dearjolly.server.domain.letter.entity.Letters;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "편지 작성 응답")
public record LetterCreateResponse(

        @Schema(description = "편지 ID", example = "16", requiredMode = Schema.RequiredMode.REQUIRED)
        Long letterId,

        @Schema(description = "편지 날짜 (writtenAt 의 날짜 부분)", example = "2025-11-01",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate date,

        @Schema(description = "저장 시각 (요청의 timeZone 기준, 초 단위까지)", example = "2025-11-01T21:00:03",
                requiredMode = Schema.RequiredMode.REQUIRED)
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
