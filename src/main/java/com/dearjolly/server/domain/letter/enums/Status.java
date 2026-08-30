package com.dearjolly.server.domain.letter.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = """
        편지 피드백 상태.
        SUBMITTED와 FEEDBACK_IN_PROGRESS는 아직 한 번도 실패하지 않은 준비 중 상태로, soon 우표와 함께 내려간다.
        FEEDBACK_COMPLETED는 피드백을 표시한다.
        FEEDBACK_FAILED는 한 번이라도 실패한 상태로 feedback이 null이며 fail 우표와 함께 내려간다.
        서버가 내부적으로 재시도를 이어가므로 FEEDBACK_COMPLETED로 바뀔 수 있다.
        """)
@Getter
@RequiredArgsConstructor
public enum Status {
    SUBMITTED("제출됨, 워커 픽업 대기"),
    FEEDBACK_IN_PROGRESS("워커가 픽업해 LLM 호출 중"),
    FEEDBACK_COMPLETED("피드백 완료"),
    FEEDBACK_FAILED("한 번 이상 실패. 서버는 재시도를 이어갈 수 있다");

    private final String description;

    public boolean isCompleted() {
        return this == FEEDBACK_COMPLETED;
    }
}
