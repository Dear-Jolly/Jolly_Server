package com.dearjolly.server.domain.feedback.dto.response;

import com.dearjolly.server.domain.feedback.entity.FeedbackTips;
import com.dearjolly.server.domain.feedback.entity.Feedbacks;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "편지에 도착한 AI 피드백")
public record FeedbackGetResponse(

        @Schema(description = "피드백 ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
        Long feedbackId,

        @Schema(
                description = "교정된 전체 내용. correctionSegments 의 correctedText 를 모두 이은 것과 정확히 일치한다",
                example = "I received flowers from a friend today.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String correctedContent,

        @Schema(
                description = "피드백 팁 목록 (0~3개). 비어 있으면 앱은 팁 영역을 표시하지 않는다",
                example = "[\"이 문맥에서는 'got'보다 'received'가 더 자연스러워요.\"]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> tips,

        @Schema(description = "교정 조각 목록 (1개 이상). sequence 순서대로 이어붙여 그린다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<CorrectionSegmentResponse> correctionSegments
) {
    public static FeedbackGetResponse from(Feedbacks feedback) {
        return new FeedbackGetResponse(
                feedback.getId(),
                feedback.getCorrectedContent(),
                feedback.getTips().stream()
                        .map(FeedbackTips::getContent)
                        .toList(),
                feedback.getCorrectionSegments().stream()
                        .map(CorrectionSegmentResponse::from)
                        .toList()
        );
    }
}
