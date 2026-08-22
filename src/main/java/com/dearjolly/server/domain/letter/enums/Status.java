package com.dearjolly.server.domain.letter.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
