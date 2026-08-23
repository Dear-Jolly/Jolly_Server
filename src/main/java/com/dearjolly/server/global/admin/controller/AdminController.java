package com.dearjolly.server.global.admin.controller;

import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.global.admin.dto.request.AdminLoginRequest;
import com.dearjolly.server.global.admin.dto.response.AdminLoginResponse;
import com.dearjolly.server.global.admin.service.AdminAuthService;
import com.dearjolly.server.global.exception.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자", description = "운영자용 API. 관리자 권한 토큰이 필요하다.")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminAuthService adminAuthService;

    @Operation(
            summary = "관리자 로그인",
            description = """
                    - 아이디와 비밀번호로 로그인해 **소셜 로그인과 똑같은 토큰 한 쌍**을 받는다.
                    - **회원가입 API 는 없다.** 관리자 계정은 서버가 미리 만들어 둔 것 하나뿐이고, 이 API 는 로그인만 한다.
                    - 관리자도 **일반 사용자와 똑같은 계정**이라, 이 토큰으로 편지 · 홈 · 계정 API 를 그대로 호출할 수 있다.
                    - 발급된 토큰은 `PATCH /api/v1/admin/version` 같은 관리자 전용 API 에도 쓴다.
                    - `refreshToken` 은 `POST /api/v1/auth/reissue` 에 그대로 쓴다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "`COMMON_001` 아이디 또는 비밀번호 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_008` 아이디 또는 비밀번호 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity
                .status(OK)
                .body(adminAuthService.login(request));
    }
}
