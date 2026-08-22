package com.dearjolly.server.global.version.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Platform {
    IOS("iOS"),
    AOS("Android");

    private final String description;
}
