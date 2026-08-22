package com.dearjolly.server.global.version.controller;

import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.global.exception.response.ErrorResponse;
import com.dearjolly.server.global.version.VersionProperties;
import com.dearjolly.server.global.version.dto.VersionGetResponse;
import com.dearjolly.server.global.version.enums.Platform;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "버전", description = "최소 지원 버전 및 정책 URL 조회 API. 로그인 전에도 호출할 수 있다.")
@RestController
@RequestMapping("/api/v1/version")
@RequiredArgsConstructor
public class VersionController {
    private final VersionProperties versionProperties;

    @Operation(
            summary = "최소 지원 버전 조회",
            description = """
                    - 강제 업데이트 **판정은 앱이 한다.** 서버는 앱 버전을 받지 않으며, `forceUpdate` 는 보조 신호다.
                    - **공지사항 · 개인정보처리방침 · 이용약관은 별도 API 가 없다.** 이 응답의 링크를 웹뷰로 연다.
                    - `platform` 을 생략하면 공통 값을, 주면 그 플랫폼에 설정된 값만 덮어쓴 결과를 반환한다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "`COMMON_001` 정의되지 않은 platform",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<VersionGetResponse> getVersion(
            @Parameter(description = "플랫폼 (IOS, AOS). 생략하면 공통 값을 반환한다")
            @RequestParam(required = false) Platform platform
    ) {
        return ResponseEntity
                .status(OK)
                .body(VersionGetResponse.of(versionProperties, platform));
    }
}
