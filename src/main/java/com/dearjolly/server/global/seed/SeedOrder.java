package com.dearjolly.server.global.seed;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SeedOrder {
    // 목 사용자 편지는 완료 우표를 STAMPS 에서 찾아 붙이므로 우표 시드가 먼저 끝나 있어야 한다.
    public static final int STAMP = 1;

    public static final int MOCK_USER = 2;
}
