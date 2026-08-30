package com.dearjolly.server.domain.letter.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "피드백에 실패한 편지 목록 (관리자)")
public record AdminFailedLetterListResponse(

        @Schema(description = "실패한 편지 목록. 없으면 빈 배열이다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<AdminFailedLetterResponse> letters,

        @Schema(description = "다음 페이지 존재 여부", example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasNext
) {
    public static AdminFailedLetterListResponse of(List<AdminFailedLetterResponse> letters, boolean hasNext) {
        return new AdminFailedLetterListResponse(letters, hasNext);
    }
}
