package com.dearjolly.server.domain.letter.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = """
        편지 피드백 상태.
        SUBMITTED와 FEEDBACK_IN_PROGRESS는 준비 중으로 렌더링한다.
        FEEDBACK_COMPLETED는 피드백을 표시한다.
        FEEDBACK_FAILED는 자동 처리가 최종 실패한 상태로 feedback이 null이며 앱이 실패 안내를 표시한다.
        """)
@Getter
@RequiredArgsConstructor
public enum Status {
    SUBMITTED("제출됨, 워커 픽업 대기"),
    FEEDBACK_IN_PROGRESS("워커가 픽업해 LLM 호출 중"),
    FEEDBACK_COMPLETED("피드백 완료"),
    FEEDBACK_FAILED("자동 재시도와 보완 복구를 모두 소진한 최종 실패");

    private final String description;

    public boolean isCompleted() {
        return this == FEEDBACK_COMPLETED;
    }
}
