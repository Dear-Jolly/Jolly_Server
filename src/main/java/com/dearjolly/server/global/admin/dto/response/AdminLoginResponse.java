package com.dearjolly.server.global.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 로그인 응답. 소셜 로그인이 주는 토큰과 형식이 같다")
public record AdminLoginResponse(

        @Schema(description = "Access Token. Authorization 헤더에 그대로 넣는다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,

        @Schema(description = "Refresh Token. 토큰 재발급 API 에 그대로 쓴다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken
) {
    public static AdminLoginResponse of(String accessToken, String refreshToken) {
        return new AdminLoginResponse(accessToken, refreshToken);
    }
}
