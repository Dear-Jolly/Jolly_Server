package com.dearjolly.server.domain.feedback.dto.response;

import com.dearjolly.server.domain.feedback.entity.CorrectionSegments;
import com.dearjolly.server.domain.feedback.enums.CorrectionType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "교정문을 그리는 조각 하나. 공백도 조각에 포함되므로 앱이 공백을 임의로 추가하지 않는다")
public record CorrectionSegmentResponse(

        @Schema(description = "순서 (1부터 시작)", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        int sequence,

        @Schema(description = "원본 텍스트 조각", example = "got", requiredMode = Schema.RequiredMode.REQUIRED)
        String originalText,

        @Schema(
                description = "교정된 텍스트 조각. MODIFIED 이면서 빈 문자열이면 삭제 제안이다",
                example = "received",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String correctedText,

        @Schema(
                description = "수정 여부. UNCHANGED 는 검은 글씨 그대로, "
                        + "MODIFIED 는 originalText 를 빨간 취소선으로 찍고 바로 뒤에 correctedText 를 초록 하이라이트로 찍는다",
                requiredMode = Schema.RequiredMode.REQUIRED)
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
