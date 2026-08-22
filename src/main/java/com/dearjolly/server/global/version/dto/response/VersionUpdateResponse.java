package com.dearjolly.server.global.version.dto.response;

import com.dearjolly.server.global.version.entity.AppVersions;
import com.dearjolly.server.global.version.enums.Platform;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최소 지원 버전 변경 결과")
public record VersionUpdateResponse(

        @Schema(description = "변경된 플랫폼", example = "IOS", requiredMode = Schema.RequiredMode.REQUIRED)
        Platform platform,

        @Schema(description = "변경된 최소 지원 버전", example = "1.2.0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String minSupportedVersion
) {
    public static VersionUpdateResponse from(AppVersions appVersion) {
        return new VersionUpdateResponse(appVersion.getPlatform(), appVersion.getMinSupportedVersion());
    }
}
