package com.dearjolly.server.domain.feedback.dto.response;

import com.dearjolly.server.domain.feedback.entity.CorrectionSegments;
import com.dearjolly.server.domain.feedback.enums.CorrectionType;

public record CorrectionSegmentResponse(
        int sequence,
        String originalText,
        String correctedText,
        CorrectionType type
) {
    public static CorrectionSegmentResponse from(CorrectionSegments segment) {
        return new CorrectionSegmentResponse(
                segment.getSequence(),
                segment.getOriginalText(),
                segment.getCorrectedText(),
                segment.getCorrectionType()
        );
    }
}
