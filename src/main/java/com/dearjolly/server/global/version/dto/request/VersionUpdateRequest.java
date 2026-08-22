package com.dearjolly.server.global.version.dto.request;

import static com.dearjolly.server.global.version.constants.VersionValidationConstants.VERSION_REGEX;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "최소 지원 버전 변경 요청")
public record VersionUpdateRequest(

        @Schema(description = "새 최소 지원 버전 (x.y.z)", example = "1.2.0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "최소 지원 버전은 필수입니다.")
        @Pattern(regexp = VERSION_REGEX, message = "최소 지원 버전은 x.y.z 형식이어야 합니다.")
        String minSupportedVersion
) {
}
