package com.dearjolly.server.domain.feedback.dto.response;

import com.dearjolly.server.domain.feedback.entity.FeedbackTips;
import com.dearjolly.server.domain.feedback.entity.Feedbacks;
import java.util.List;

public record FeedbackGetResponse(
        Long feedbackId,
        String correctedContent,
        List<String> tips,
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
