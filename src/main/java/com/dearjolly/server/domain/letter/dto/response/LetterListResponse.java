package com.dearjolly.server.domain.letter.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "편지 목록 응답")
public record LetterListResponse(

        @Schema(description = "편지 목록. 편지가 한 통도 없으면 빈 배열이다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<LetterSummaryResponse> letters,

        @Schema(description = "다음 페이지 존재 여부", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasNext
) {
    public static LetterListResponse of(List<LetterSummaryResponse> letters, boolean hasNext) {
        return new LetterListResponse(letters, hasNext);
    }
}
