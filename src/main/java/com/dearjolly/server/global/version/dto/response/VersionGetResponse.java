package com.dearjolly.server.global.version.dto.response;

import com.dearjolly.server.global.version.PolicyProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최소 지원 버전 · 강제 업데이트 판정 · 정책 페이지 URL")
public record VersionGetResponse(

        @Schema(description = "이 버전 미만이면 강제 업데이트 대상이다", example = "1.0.0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String minSupportedVersion,

        @Schema(description = "요청한 appVersion 이 minSupportedVersion 미만이면 true. 서버가 계산한다",
                example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean forceUpdate,

        @Schema(description = "개인정보처리방침 URL", example = "https://dearjolly.com/privacy")
        String privacyPolicyUrl,

        @Schema(description = "서비스 이용약관 URL", example = "https://dearjolly.com/terms")
        String termsOfServiceUrl,

        @Schema(description = "공지사항 URL", example = "https://dearjolly.com/notice")
        String noticeUrl
) {
    public static VersionGetResponse of(String minSupportedVersion, boolean forceUpdate, PolicyProperties policy) {
        return new VersionGetResponse(
                minSupportedVersion,
                forceUpdate,
                policy.privacyPolicyUrl(),
                policy.termsOfServiceUrl(),
                policy.noticeUrl()
        );
    }
}
