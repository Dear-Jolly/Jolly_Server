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
                description = """
                        피드백 상태.
                        SUBMITTED·FEEDBACK_IN_PROGRESS는 아직 한 번도 실패하지 않은 준비 중 상태이며 feedback이 null이다.
                        FEEDBACK_COMPLETED는 feedback이 있는 완료 상태,
                        FEEDBACK_FAILED는 한 번 이상 실패한 상태로 feedback이 null이다.
                        서버가 내부적으로 재시도를 이어가므로 FEEDBACK_FAILED가 FEEDBACK_COMPLETED로 바뀔 수 있다
                        """,
                allowableValues = {"SUBMITTED", "FEEDBACK_IN_PROGRESS", "FEEDBACK_COMPLETED", "FEEDBACK_FAILED"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        Status status,

        @Schema(
                description = """
                        우표 이미지 URL. 항상 값이 있다.
                        SUBMITTED·FEEDBACK_IN_PROGRESS는 soon(준비 중), FEEDBACK_FAILED는 fail(실패),
                        FEEDBACK_COMPLETED는 AI가 고른 우표다
                        """,
                example = "http://localhost:9000/dear-jolly-stamps/stamp/soon.png",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String stampImage,

        @Schema(description = "피드백 정보. FEEDBACK_COMPLETED에서만 값이 있고 준비 중·실패 상태에서는 null이다")
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
