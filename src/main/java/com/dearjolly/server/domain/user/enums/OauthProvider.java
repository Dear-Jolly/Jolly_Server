package com.dearjolly.server.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OauthProvider {
    KAKAO("카카오 로그인"),
    APPLE("애플 로그인");

    private final String description;
}
