package com.dearjolly.server.domain.letter.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StampConstants {
    // 편지 등록 시점에 붙는 "준비 중" 우표. 피드백이 완료되면 LLM 이 고른 우표로 교체된다.
    // 시드에서 항상 첫 행(stamp_id = 1)으로 들어가고, LLM 선택 후보에서는 제외된다.
    public static final String DEFAULT_STAMP_NAME = "soon";
}
