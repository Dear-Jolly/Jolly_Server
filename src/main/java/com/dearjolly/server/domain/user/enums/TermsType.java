package com.dearjolly.server.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TermsType {

    SERVICE("서비스 이용약관", true),
    PRIVACY("개인정보 처리방침", true),
    MARKETING("마케팅 수신 동의", false);

    private final String description;
    private final boolean required;
}
