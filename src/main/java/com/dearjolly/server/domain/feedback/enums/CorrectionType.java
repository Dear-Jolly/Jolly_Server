package com.dearjolly.server.domain.feedback.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CorrectionType {
    UNCHANGED("교정 없음"),
    MODIFIED("교정됨");

    private final String description;
}
