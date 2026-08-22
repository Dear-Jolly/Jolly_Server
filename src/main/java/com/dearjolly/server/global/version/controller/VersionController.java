package com.dearjolly.server.global.version.controller;

import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.global.exception.response.ErrorResponse;
import com.dearjolly.server.global.version.dto.request.VersionUpdateRequest;
import com.dearjolly.server.global.version.dto.response.VersionGetResponse;
import com.dearjolly.server.global.version.dto.response.VersionUpdateResponse;
import com.dearjolly.server.global.version.enums.Platform;
import com.dearjolly.server.global.version.service.VersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "버전", description = "최소 지원 버전 조회·변경 API")
@RestController
@RequestMapping("/api/v1/version")
@RequiredArgsConstructor
public class VersionController {
    private final VersionService versionService;

    @Operation(
            summary = "최소 지원 버전 조회 및 강제 업데이트 판정",
            description = """
                    - **강제 업데이트 판정은 서버가 한다.** 앱이 자기 버전(`appVersion`)을 보내면
                      서버가 플랫폼별 `minSupportedVersion` 과 비교해 `forceUpdate` 를 계산해 준다.
                    - 비교는 `x.y.z` 를 major → minor → patch 순으로 본다. `appVersion` 이 더 낮을 때만 `true` 다.
                    - **로그인 전에도 호출할 수 있다.**
                    - **공지사항 · 개인정보처리방침 · 이용약관은 별도 API 가 없다.** 이 응답의 링크를 웹뷰로 연다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "`COMMON_001` 정의되지 않은 platform · 파라미터 누락 / `VERSION_001` appVersion 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "`VERSION_002` 해당 플랫폼의 최소 지원 버전 미설정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<VersionGetResponse> getVersion(
            @Parameter(description = "플랫폼", example = "IOS", required = true)
            @RequestParam Platform platform,

            @Parameter(description = "앱이 실행 중인 자기 버전 (x.y.z)", example = "1.0.0", required = true)
            @RequestParam String appVersion
    ) {
        return ResponseEntity
                .status(OK)
                .body(versionService.getVersion(platform, appVersion));
    }

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
