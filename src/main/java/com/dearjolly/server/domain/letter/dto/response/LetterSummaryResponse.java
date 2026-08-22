package com.dearjolly.server.domain.letter.dto.response;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "편지 목록의 카드 한 장")
public record LetterSummaryResponse(

        @Schema(description = "편지 ID", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
        Long letterId,

        @Schema(description = "편지 날짜", example = "2025-11-01", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate date,

        @Schema(
                description = "편지 미리보기. 원문 앞 50자이며 말줄임은 앱에서 처리한다",
                example = "I got flowers from a friend today. It really touch",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String summary,

        @Schema(
                description = "편지 상태",
                allowableValues = {"SUBMITTED", "FEEDBACK_IN_PROGRESS", "FEEDBACK_COMPLETED"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        Status status,

        @Schema(description = "피드백 열람 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isRead,

        @Schema(
                description = "우표 이미지 URL. 항상 값이 있으며, 피드백 완료 전에는 soon(준비 중) 우표다",
                example = "http://localhost:9000/dear-jolly-stamps/stamp/soon.png",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String stampImage
) {
    private static final int SUMMARY_LENGTH = 50;

    public static LetterSummaryResponse of(Letters letter, String stampImage) {
        return new LetterSummaryResponse(
                letter.getId(),
                letter.getLetterDate(),
                summarize(letter.getContent()),
                letter.toResponseStatus(),
                letter.isRead(),
                stampImage
        );
    }

    private static String summarize(String content) {
        int codePointCount = content.codePointCount(0, content.length());
        if (codePointCount <= SUMMARY_LENGTH) {
            return content;
        }
        return content.substring(0, content.offsetByCodePoints(0, SUMMARY_LENGTH));
    }
}
