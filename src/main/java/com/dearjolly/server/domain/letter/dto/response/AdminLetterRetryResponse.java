package com.dearjolly.server.domain.letter.dto.response;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 피드백 재시도 결과")
public record AdminLetterRetryResponse(

        @Schema(description = "편지 ID", example = "16", requiredMode = Schema.RequiredMode.REQUIRED)
        Long letterId,

        @Schema(description = "재시도 요청 후 상태. 항상 SUBMITTED 다", example = "SUBMITTED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Status status,

        @Schema(description = "초기화된 재시도 횟수. 항상 0 이다", example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int retryCount,

        @Schema(description = "재시도 예약 시각", example = "2026-08-30T18:30:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime nextRetryAt
) {
    public static AdminLetterRetryResponse from(Letters letter) {
        return new AdminLetterRetryResponse(
                letter.getId(),
                letter.getStatus(),
                letter.getRetryCount(),
                letter.getNextRetryAt()
        );
    }
}
