package com.dearjolly.server.domain.letter.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StampConstants {
    // 편지 등록 시점에 붙는 "준비 중" 우표. 피드백이 완료되면 LLM 이 고른 우표로 교체된다.
    // 시드에서 항상 첫 행(stamp_id = 1)으로 들어가고, LLM 선택 후보에서는 제외된다.
    public static final String DEFAULT_STAMP_NAME = "soon";

    // 첫 실패 시점에 붙는 "실패" 우표. 내부 재시도가 성공하면 LLM 이 고른 우표로 교체된다.
    // 앱에는 실패 이후 재시도 중이라는 사실을 노출하지 않으므로, 이 우표가 붙은 뒤로는 상태도 FEEDBACK_FAILED 로 내려간다.
    // LLM 선택 후보에서는 기본 우표와 함께 제외된다.
    public static final String FAILED_STAMP_NAME = "fail";
}
