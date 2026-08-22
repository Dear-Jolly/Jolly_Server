package com.dearjolly.server.global.version.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "버전 조회 대상 플랫폼")
@Getter
@RequiredArgsConstructor
public enum Platform {
    IOS("iOS"),
    AOS("Android");

    private final String description;
}
