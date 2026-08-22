package com.dearjolly.server.domain.user.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "약관 종류. SERVICE · PRIVACY 는 필수 동의, MARKETING 은 선택 동의다")
@Getter
@RequiredArgsConstructor
public enum TermsType {
    SERVICE("서비스 이용약관", true),
    PRIVACY("개인정보 처리방침", true),
    MARKETING("마케팅 수신 동의", false);

    private final String description;
    private final boolean required;
}
