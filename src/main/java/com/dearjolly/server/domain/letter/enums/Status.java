package com.dearjolly.server.domain.letter.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "편지 상태. 앱은 SUBMITTED 와 FEEDBACK_IN_PROGRESS 를 동일하게 렌더링한다")
@Getter
@RequiredArgsConstructor
public enum Status {
    SUBMITTED("제출됨, 워커 픽업 대기"),
    FEEDBACK_IN_PROGRESS("워커가 픽업해 LLM 호출 중"),
    FEEDBACK_COMPLETED("피드백 완료"),
    FEEDBACK_FAILED("피드백 실패 (내부 전용)");

    private final String description;

    public boolean isCompleted() {
        return this == FEEDBACK_COMPLETED;
    }
}
