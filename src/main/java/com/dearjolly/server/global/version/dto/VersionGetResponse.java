package com.dearjolly.server.global.version.dto;

import com.dearjolly.server.global.version.VersionProperties;
import com.dearjolly.server.global.version.enums.Platform;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "앱 최소 지원 버전과 정책 페이지 URL")
public record VersionGetResponse(

        @Schema(description = "최신 배포 버전", example = "1.0.0", requiredMode = Schema.RequiredMode.REQUIRED)
        String latestVersion,

        @Schema(description = "이 버전 미만이면 강제 업데이트. 판정은 앱이 한다", example = "1.0.0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String minSupportedVersion,

        @Schema(description = "강제 업데이트 여부. 앱은 자기 버전과 minSupportedVersion 을 직접 비교하고 이 값은 보조 신호로 쓴다",
                example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean forceUpdate,

        @Schema(description = "개인정보처리방침 URL", example = "https://dearjolly.com/privacy")
        String privacyPolicyUrl,

        @Schema(description = "서비스 이용약관 URL", example = "https://dearjolly.com/terms")
        String termsOfServiceUrl,

        @Schema(description = "공지사항 URL", example = "https://dearjolly.com/notice")
        String noticeUrl
) {
    public static VersionGetResponse of(VersionProperties properties, Platform platform) {
        return new VersionGetResponse(
                properties.latestFor(platform),
                properties.minSupportedFor(platform),
                properties.forceUpdateFor(platform),
                properties.privacyPolicyUrl(),
                properties.termsOfServiceUrl(),
                properties.noticeUrl()
        );
    }
}
