package com.dearjolly.server.domain.feedback.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "교정 조각의 수정 여부")
@Getter
@RequiredArgsConstructor
public enum CorrectionType {
    UNCHANGED("교정 없음"),
    MODIFIED("교정됨");

    private final String description;
}
