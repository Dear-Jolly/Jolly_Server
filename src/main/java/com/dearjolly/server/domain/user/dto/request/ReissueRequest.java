package com.dearjolly.server.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 재발급 요청")
public record ReissueRequest(

        @Schema(description = "로그인 시 발급받은 리프레시 토큰. 한 번 쓰면 무효가 된다",
                example = "eyJhbGciOiJIUzI1NiJ9...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        String refreshToken
) {
}
