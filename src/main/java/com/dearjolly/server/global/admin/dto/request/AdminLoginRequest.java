package com.dearjolly.server.global.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 로그인 요청")
public record AdminLoginRequest(

        @Schema(description = "관리자 아이디", example = "your-admin-username", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "아이디는 필수입니다.")
        String username,

        @Schema(description = "관리자 비밀번호", example = "your-admin-password", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
