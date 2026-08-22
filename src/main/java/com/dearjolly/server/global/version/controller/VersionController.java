package com.dearjolly.server.global.version.controller;

import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.global.version.VersionProperties;
import com.dearjolly.server.global.version.dto.VersionGetResponse;
import com.dearjolly.server.global.version.enums.Platform;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 유저·편지 어느 애그리거트에도 속하지 않는 앱 메타 정보라 domain 이 아니라 global 하위에 둔다
 * (기능명세 §2.1). 로그인 전에 호출할 수 있어야 하므로 인증도 온보딩 가드도 걸지 않는다.
 */
@Tag(name = "버전", description = "최소 지원 버전 및 정책 URL 조회 API")
@RestController
@RequestMapping("/api/v1/version")
@RequiredArgsConstructor
public class VersionController {

    private final VersionProperties versionProperties;

    @Operation(summary = "최소 지원 버전 조회")
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
