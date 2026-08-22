package com.dearjolly.server.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 재발급 응답. 앱은 두 토큰을 모두 갈아 끼운다")
public record ReissueResponse(

        @Schema(description = "새 액세스 토큰 (30분)", example = "eyJhbGciOiJIUzI1NiJ9...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,

        @Schema(description = "새 리프레시 토큰 (14일). 기존 값을 이 값으로 교체 저장한다",
                example = "eyJhbGciOiJIUzI1NiJ9...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken
) {
    public static ReissueResponse of(String accessToken, String refreshToken) {
        return new ReissueResponse(accessToken, refreshToken);
    }
}
