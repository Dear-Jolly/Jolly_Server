package com.dearjolly.server.domain.user.dto.response;

import com.dearjolly.server.domain.user.entity.Users;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "닉네임 등록 · 변경 응답")
public record NicknameUpdateResponse(

        @Schema(description = "변경된 닉네임", example = "iloveJolly", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname
) {
    public static NicknameUpdateResponse from(Users user) {
        return new NicknameUpdateResponse(user.getNickname());
    }
}
