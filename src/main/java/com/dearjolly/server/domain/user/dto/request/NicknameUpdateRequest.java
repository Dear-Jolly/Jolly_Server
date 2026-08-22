package com.dearjolly.server.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "닉네임 등록 · 변경 요청")
public record NicknameUpdateRequest(

        @Schema(
                description = "변경할 닉네임. 영문 + 숫자 1~20자 (문자 수). 공백 · 특수기호 · 한글은 불가하며 중복은 허용한다",
                example = "iloveJolly",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname
) {
}
