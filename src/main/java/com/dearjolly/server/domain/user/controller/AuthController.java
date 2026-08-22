package com.dearjolly.server.domain.user.controller;

import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.domain.user.dto.request.ReissueRequest;
import com.dearjolly.server.domain.user.dto.response.OauthLoginResult;
import com.dearjolly.server.domain.user.dto.response.ReissueResponse;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.domain.user.service.AuthService;
import com.dearjolly.server.global.auth.oauth.OauthProperties;
import com.dearjolly.server.global.auth.principal.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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

@Tag(name = "인증", description = "소셜 로그인, 토큰 재발급, 로그아웃 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final OauthProperties oauthProperties;

    @Operation(summary = "소셜 로그인 시작 - provider 로그인 페이지로 리다이렉트")
    @GetMapping("/{provider}")
    public ResponseEntity<Void> authorize(
            @Parameter(description = "로그인 수단 (KAKAO, APPLE)", required = true)
            @PathVariable OauthProvider provider
    ) {
        String state = UUID.randomUUID().toString();
        return ResponseEntity
                .status(org.springframework.http.HttpStatus.FOUND)
                .location(URI.create(authService.buildAuthorizationUri(provider, state)))
                .build();
    }

    @Operation(summary = "카카오 로그인 콜백 - 코드 교환 후 앱 딥링크로 리다이렉트")
    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(
            @Parameter(description = "카카오가 발급한 인가 코드", required = true)
            @RequestParam String code
    ) {
        return redirectToApp(authService.handleCallback(OauthProvider.KAKAO, code, null));
    }

    @Operation(summary = "애플 로그인 콜백 - 코드 교환 후 앱 딥링크로 리다이렉트")
    @PostMapping("/apple/callback")
    public ResponseEntity<Void> appleCallback(
            @Parameter(description = "애플이 발급한 인가 코드", required = true)
            @RequestParam String code,
            @Parameter(description = "애플이 함께 보내는 identity token", required = true)
            @RequestParam(name = "id_token") String idToken
    ) {
        return redirectToApp(authService.handleCallback(OauthProvider.APPLE, code, idToken));
    }

    @Operation(summary = "토큰 재발급")
    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(
            @Parameter(description = "재발급 요청 객체", required = true)
            @Valid @RequestBody ReissueRequest request
    ) {
        return ResponseEntity
                .status(OK)
                .body(authService.reissue(request));
    }

    @Operation(summary = "로그아웃")
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
                .status(org.springframework.http.HttpStatus.FOUND)
                .location(target)
                .build();
    }
}
