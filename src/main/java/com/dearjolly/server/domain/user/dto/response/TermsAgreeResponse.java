package com.dearjolly.server.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약관 동의 응답")
public record TermsAgreeResponse(

        @Schema(description = "이 요청 반영 후의 필수 약관 동의 완료 여부", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean termsAgreed
) {
    public static TermsAgreeResponse from(boolean termsAgreed) {
        return new TermsAgreeResponse(termsAgreed);
    }
}
