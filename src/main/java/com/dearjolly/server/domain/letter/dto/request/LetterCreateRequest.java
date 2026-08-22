package com.dearjolly.server.domain.letter.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "편지 작성 요청")
public record LetterCreateRequest(

        @Schema(
                description = "편지 내용. 영어 전용, 1~500자 (문자 수, 공백 포함)",
                example = "I got flowers from a friend today. It really touched me.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String content,

        @Schema(
                description = "기기 로컬 기준 작성 시각. 서버 현재 시각 기준 ±24시간 이내",
                example = "2025-11-01T21:00:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime writtenAt,

        @Schema(
                description = "기기 타임존 ID",
                example = "Asia/Seoul",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String timeZone
) {
}
