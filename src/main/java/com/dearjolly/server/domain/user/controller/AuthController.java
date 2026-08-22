package com.dearjolly.server.domain.user.controller;

import static org.springframework.http.HttpStatus.FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.domain.user.dto.request.ReissueRequest;
import com.dearjolly.server.domain.user.dto.response.OauthLoginResult;
import com.dearjolly.server.domain.user.dto.response.ReissueResponse;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.domain.user.service.AuthService;
import com.dearjolly.server.global.auth.oauth.OauthProperties;
import com.dearjolly.server.global.auth.principal.LoginUser;
import com.dearjolly.server.global.exception.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "인증", description = "소셜 로그인, 토큰 재발급, 로그아웃 API. 로그인은 서버 리다이렉트 방식이며 콜백 2종은 앱이 직접 호출하지 않는다.")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final OauthProperties oauthProperties;

    @Operation(
            summary = "소셜 로그인 시작 - provider 로그인 페이지로 리다이렉트",
            description = """
                    앱은 이 주소를 외부 브라우저 또는 웹뷰로 열기만 한다. 코드 교환 · 회원 생성 · 토큰 발급은 서버가 처리한다.

                    - `provider` 는 대소문자를 가리지 않는다.
                    - 유저는 `(provider + provider 회원 식별자)` 로 구분한다. 이메일이 같아도 카카오와 애플은 별개 계정이다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "provider 로그인 페이지로 리다이렉트", content = @Content),
            @ApiResponse(
                    responseCode = "400",
                    description = "`COMMON_001` 지원하지 않는 provider",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @GetMapping("/{provider}")
    public ResponseEntity<Void> authorize(
            @Parameter(description = "로그인 수단 (KAKAO, APPLE) - 대소문자를 가리지 않는다", required = true)
            @PathVariable OauthProvider provider
    ) {
        return ResponseEntity
                .status(FOUND)
                .location(URI.create(authService.buildAuthorizationUri(provider)))
                .build();
    }

    @Operation(
            summary = "카카오 로그인 콜백 - 코드 교환 후 앱 딥링크로 리다이렉트",
            description = """
                    **카카오가 호출하는 주소다. 앱이 직접 호출하지 않는다.**
                    앱 딥링크에 `accessToken` · `refreshToken` · `userId` · `isNewUser` · `termsAgreed` · `nicknameRegistered` 를 실어 보낸다.

                    - 앱 진입 화면은 `termsAgreed == false` → 약관동의, `nicknameRegistered == false` → 닉네임, 둘 다 `true` → 홈이다.
                    - **토큰이 URL 에 실려 오므로** 받은 즉시 보안 저장소로 옮긴다.
                    - 로그인은 **기기 1대만 유지**된다. 탈퇴한 계정으로 다시 로그인하면 항상 신규 가입이다.
                    - `state` 가 없거나 이 서버가 발급한 값이 아니거나 발급 후 10분이 지났으면 `AUTH_002` 다.
                    - 이 단계의 실패는 딥링크가 아니라 JSON 에러 응답으로 나간다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "앱 딥링크로 리다이렉트", content = @Content),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_002` state 검증 실패 · 인가 코드 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "502",
                    description = "`AUTH_003` 카카오 서버와 통신 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(
            @Parameter(description = "카카오가 발급한 인가 코드", required = true)
            @RequestParam String code,
            @Parameter(description = "로그인 시작 때 서버가 붙여 보낸 state", required = true)
            @RequestParam(required = false) String state
    ) {
        return redirectToApp(authService.handleCallback(OauthProvider.KAKAO, code, null, state));
    }

    @Operation(
            summary = "애플 로그인 콜백 - 코드 교환 후 앱 딥링크로 리다이렉트",
            description = """
                    **애플이 호출하는 주소다. 앱이 직접 호출하지 않는다.** 애플이 `form_post` 로 보내기 때문에 `POST` 다.

                    - 응답 형태와 앱 분기 규칙은 카카오 콜백과 같다.
                    - 애플에서 이메일 제공을 거부한 유저는 이메일이 `null` 이다.
                    - `state` 가 없거나 이 서버가 발급한 값이 아니거나 발급 후 10분이 지났으면 `AUTH_002` 다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "앱 딥링크로 리다이렉트", content = @Content),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_002` state 검증 실패 · 인가 코드 · id_token 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "502",
                    description = "`AUTH_003` 애플 서버와 통신 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/apple/callback")
    public ResponseEntity<Void> appleCallback(
            @Parameter(description = "애플이 발급한 인가 코드", required = true)
            @RequestParam String code,
            @Parameter(description = "애플이 함께 보내는 identity token", required = true)
            @RequestParam(name = "id_token") String idToken,
            @Parameter(description = "로그인 시작 때 서버가 붙여 보낸 state", required = true)
            @RequestParam(required = false) String state
    ) {
        return redirectToApp(authService.handleCallback(OauthProvider.APPLE, code, idToken, state));
    }

    @Operation(
            summary = "토큰 재발급",
            description = """
                    - **리프레시 토큰도 함께 새로 발급되고 이전 토큰은 즉시 무효**가 된다. 앱은 응답의 두 토큰을 모두 갈아 끼운다.
                    - 만료된 액세스 토큰이 헤더에 실려 있어도 정상 동작한다.
                    - 앱은 `AUTH_004` 를 받으면 저장된 토큰을 지우고 로그인 화면으로 이동한다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_004` 만료 · 위조 · 이미 사용된 리프레시 토큰",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(
            @Parameter(description = "재발급 요청 객체", required = true)
            @Valid @RequestBody ReissueRequest request
    ) {
        return ResponseEntity
                .status(OK)
                .body(authService.reissue(request));
    }

    @Operation(
            summary = "로그아웃",
            description = """
                    **세션만 끊고 편지 · 계정 데이터는 그대로 보존**된다.

                    - 카카오 세션 종료 등 소셜 로그아웃은 앱 SDK 에서 처리한다.
                    - 온보딩 미완료 상태에서도 호출할 수 있다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 유효하지 않은 토큰 / `AUTH_007` 탈퇴한 계정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@LoginUser Long userId) {
        authService.logout(userId);
        return ResponseEntity
                .status(NO_CONTENT)
                .build();
    }

    private ResponseEntity<Void> redirectToApp(OauthLoginResult result) {
        URI target = UriComponentsBuilder.fromUriString(oauthProperties.appRedirectUri())
                .queryParam("accessToken", result.accessToken())
                .queryParam("refreshToken", result.refreshToken())
                .queryParam("userId", result.userId())
                .queryParam("isNewUser", result.isNewUser())
                .queryParam("termsAgreed", result.termsAgreed())
                .queryParam("nicknameRegistered", result.nicknameRegistered())
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

        return ResponseEntity
                .status(FOUND)
                .location(target)
                .build();
    }
}
