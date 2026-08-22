package com.dearjolly.server.global.version.controller;

import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.global.exception.response.ErrorResponse;
import com.dearjolly.server.global.version.dto.request.VersionUpdateRequest;
import com.dearjolly.server.global.version.dto.response.VersionUpdateResponse;
import com.dearjolly.server.global.version.enums.Platform;
import com.dearjolly.server.global.version.service.VersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자", description = "운영자용 API. 관리자 권한 토큰이 필요하다.")
@RestController
@RequestMapping("/api/v1/admin/version")
@RequiredArgsConstructor
public class AdminVersionController {
    private final VersionService versionService;

    @Operation(
            summary = "최소 지원 버전 변경 (관리자)",
            description = """
                    - 플랫폼 하나의 `minSupportedVersion` 을 바꾼다. 즉시 조회 API 에 반영된다.
                    - **관리자 토큰이 필요하다.** `POST /api/v1/admin/login` 으로 발급받는다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "`COMMON_001` 정의되지 않은 platform · 버전 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 토큰 없음 또는 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "`AUTH_006` 관리자 권한 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "`VERSION_002` 해당 플랫폼의 최소 지원 버전 미설정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping
    public ResponseEntity<VersionUpdateResponse> updateMinSupportedVersion(
            @Parameter(description = "플랫폼", example = "IOS", required = true)
            @RequestParam Platform platform,

            @Valid @RequestBody VersionUpdateRequest request
    ) {
        return ResponseEntity
                .status(OK)
                .body(versionService.updateMinSupportedVersion(platform, request));
    }
}
