package com.dearjolly.server.global.version.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 최소 지원 버전을 플랫폼별로 다르게 내려야 할 때 쓴다.
 * MVP 는 두 플랫폼이 같은 값을 쓰지만, 한쪽만 심사에 걸려 버전이 벌어지는 일이 흔하다.
 */
@Getter
@RequiredArgsConstructor
public enum Platform {

    IOS("iOS"),
    AOS("Android");

    private final String description;
}
