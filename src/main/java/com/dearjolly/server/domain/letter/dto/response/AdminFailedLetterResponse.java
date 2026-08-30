package com.dearjolly.server.domain.letter.dto.response;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "피드백에 실패한 편지 한 건 (관리자)")
public record AdminFailedLetterResponse(

        @Schema(description = "편지 ID", example = "16", requiredMode = Schema.RequiredMode.REQUIRED)
        Long letterId,

        @Schema(description = "작성자 ID", example = "6", requiredMode = Schema.RequiredMode.REQUIRED)
        Long userId,

        @Schema(description = "작성자 닉네임", example = "jolly", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname,

        @Schema(description = "편지 날짜", example = "2026-08-30", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate date,

        @Schema(description = "편지 원문", example = "I go to school yesterday.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String content,

        @Schema(description = "서버 내부 상태. 재시도 대기 중이면 SUBMITTED 다", example = "SUBMITTED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Status status,

        @Schema(description = "사용한 재시도 횟수", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        int retryCount,

        @Schema(description = "다음 재시도 예약 시각. 예산을 모두 쓴 편지는 null 이다",
                example = "2026-08-30T18:19:46")
        LocalDateTime nextRetryAt,

        @Schema(description = "마지막 상태 변경 시각", example = "2026-08-30T18:14:46",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime updatedAt
) {
    public static AdminFailedLetterResponse from(Letters letter) {
        return new AdminFailedLetterResponse(
                letter.getId(),
                letter.getUser().getId(),
                letter.getUser().getNickname(),
                letter.getLetterDate(),
                letter.getContent(),
                letter.getStatus(),
                letter.getRetryCount(),
                letter.getNextRetryAt(),
                letter.getUpdatedAt()
        );
    }
}
