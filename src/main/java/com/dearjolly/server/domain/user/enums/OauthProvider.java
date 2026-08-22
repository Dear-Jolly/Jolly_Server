package com.dearjolly.server.domain.user.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "로그인 수단. 유저는 (provider + provider 회원 식별자) 로 구분한다")
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
