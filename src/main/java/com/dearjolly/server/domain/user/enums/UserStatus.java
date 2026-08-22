package com.dearjolly.server.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("정상 이용 중"),
    WITHDRAWN("탈퇴 처리됨");

    private final String description;
}
