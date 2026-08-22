package com.dearjolly.server.domain.user.enums;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OauthProvider {
    KAKAO("카카오 로그인"),
    APPLE("애플 로그인");

    private final String description;

    public static OauthProvider from(String value) {
        return Arrays.stream(values())
                .filter(provider -> provider.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 로그인 수단이다: " + value));
    }
}
