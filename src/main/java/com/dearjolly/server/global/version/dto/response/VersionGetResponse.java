package com.dearjolly.server.global.version.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최소 지원 버전과 강제 업데이트 판정")
public record VersionGetResponse(

        @Schema(description = "이 버전 미만이면 강제 업데이트 대상이다", example = "1.0.0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String minSupportedVersion,

        @Schema(description = "요청한 appVersion 이 minSupportedVersion 미만이면 true. 서버가 계산한다",
                example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean forceUpdate
) {
    public static VersionGetResponse of(String minSupportedVersion, boolean forceUpdate) {
        return new VersionGetResponse(minSupportedVersion, forceUpdate);
    }
}
