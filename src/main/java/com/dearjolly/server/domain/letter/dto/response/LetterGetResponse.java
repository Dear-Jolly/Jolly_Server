package com.dearjolly.server.domain.letter.dto.response;

import com.dearjolly.server.domain.feedback.dto.response.FeedbackGetResponse;
import com.dearjolly.server.domain.feedback.entity.Feedbacks;
import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "편지 상세 · 피드백 응답")
public record LetterGetResponse(

        @Schema(description = "편지 ID", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
        Long letterId,

        @Schema(description = "편지 날짜", example = "2025-11-01", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate date,

        @Schema(description = "원본 편지 내용", example = "I got flowers from a friend today.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String originalContent,

        @Schema(
                description = "편지 상태",
                allowableValues = {"SUBMITTED", "FEEDBACK_IN_PROGRESS", "FEEDBACK_COMPLETED"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        Status status,

        @Schema(
                description = "우표 이미지 URL. 항상 값이 있으며, 피드백 완료 전에는 soon(준비 중) 우표다",
                example = "http://localhost:9000/dear-jolly-stamps/stamp/soon.png",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String stampImage,

        @Schema(description = "피드백 정보. 피드백 완료 전에는 null 이다")
        FeedbackGetResponse feedback
) {
    public static LetterGetResponse of(Letters letter, String stampImage) {
        return new LetterGetResponse(
                letter.getId(),
                letter.getLetterDate(),
                letter.getContent(),
                letter.toResponseStatus(),
                stampImage,
                feedbackOf(letter)
        );
    }

    private static FeedbackGetResponse feedbackOf(Letters letter) {
        Feedbacks feedback = letter.getFeedback();
        if (!letter.isFeedbackCompleted() || feedback == null) {
            return null;
        }
        return FeedbackGetResponse.from(feedback);
    }
}
