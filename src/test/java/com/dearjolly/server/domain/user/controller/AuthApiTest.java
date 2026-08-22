package com.dearjolly.server.domain.user.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.global.auth.oauth.OauthStateProvider;
import com.dearjolly.server.support.ApiTestSupport;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class AuthApiTest extends ApiTestSupport {
    @Autowired
    private OauthStateProvider oauthStateProvider;

    @DisplayName("GET /api/v1/auth/{provider} : 카카오 로그인 페이지로 302 리다이렉트한다")
    @Test
    void authorizeKakao() {
        given().redirects().follow(false)
                .when().get("/api/v1/auth/KAKAO")
                .then()
                .statusCode(HttpStatus.FOUND.value())
                .header(HttpHeaders.LOCATION, containsString("kauth.kakao.com/oauth/authorize"))
                .header(HttpHeaders.LOCATION, containsString("response_type=code"))
                .header(HttpHeaders.LOCATION, containsString("state="));
    }

    @DisplayName("GET /api/v1/auth/{provider} : 애플은 form_post 로 콜백받도록 요청한다")
    @Test
    void authorizeApple() {
        given().redirects().follow(false)
                .when().get("/api/v1/auth/APPLE")
                .then()
                .statusCode(HttpStatus.FOUND.value())
                .header(HttpHeaders.LOCATION, containsString("appleid.apple.com/auth/authorize"))
                .header(HttpHeaders.LOCATION, containsString("response_mode=form_post"));
    }

    @DisplayName("GET /api/v1/auth/{provider} : provider 는 대소문자를 가리지 않는다")
    @ValueSource(strings = {"kakao", "Kakao", "kAkAo"})
    @ParameterizedTest
    void authorizeIgnoresProviderCase(String provider) {
        given().redirects().follow(false)
                .when().get("/api/v1/auth/" + provider)
                .then()
                .statusCode(HttpStatus.FOUND.value())
                .header(HttpHeaders.LOCATION, containsString("kauth.kakao.com/oauth/authorize"));
    }

    @DisplayName("GET /api/v1/auth/{provider} : 지원하지 않는 provider 면 COMMON_001")
    @Test
    void authorizeUnsupportedProvider() {
        given().redirects().follow(false)
                .when().get("/api/v1/auth/NAVER")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON_001"));
    }

    @DisplayName("GET /api/v1/auth/kakao/callback : state 가 없으면 AUTH_002")
    @Test
    void kakaoCallbackWithoutState() {
        given().redirects().follow(false)
                .queryParam("code", "any-code")
                .when().get("/api/v1/auth/kakao/callback")
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_002"));
    }

    @DisplayName("GET /api/v1/auth/kakao/callback : 서버가 발급하지 않은 state 면 AUTH_002")
    @Test
    void kakaoCallbackWithForgedState() {
        given().redirects().follow(false)
                .queryParam("code", "any-code")
                .queryParam("state", "forged-state")
                .when().get("/api/v1/auth/kakao/callback")
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_002"));
    }

    @DisplayName("GET /api/v1/auth/kakao/callback : 다른 provider 로 발급한 state 면 AUTH_002")
    @Test
    void kakaoCallbackWithOtherProviderState() {
        given().redirects().follow(false)
                .queryParam("code", "any-code")
                .queryParam("state", oauthStateProvider.issue(OauthProvider.APPLE))
                .when().get("/api/v1/auth/kakao/callback")
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_002"));
    }

    @DisplayName("POST /api/v1/auth/apple/callback : state 가 없으면 AUTH_002")
    @Test
    void appleCallbackWithoutState() {
        given().redirects().follow(false)
                .contentType(ContentType.URLENC)
                .formParam("code", "any-code")
                .formParam("id_token", "any-id-token")
                .when().post("/api/v1/auth/apple/callback")
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_002"));
    }

    @DisplayName("POST /api/v1/auth/reissue : 토큰 재발급 API - 회전된 값이 저장된다")
    @Test
    void reissue() {
        Users user = 유저를_저장한다("kakao-r1");
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getRole());
        user.updateRefreshToken(refreshToken);
        userRepository.save(user);

        String reissued = given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshToken))
                .when().post("/api/v1/auth/reissue")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .extract().path("refreshToken");

        assertThat(userRepository.findById(user.getId()).orElseThrow().getRefreshToken()).isEqualTo(reissued);
    }

    @DisplayName("POST /api/v1/auth/reissue : 만료된 Access Token 을 헤더에 달고 와도 재발급된다")
    @Test
    void reissueWithExpiredAccessTokenHeader() {
        Users user = 유저를_저장한다("kakao-r4");
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getRole());
        user.updateRefreshToken(refreshToken);
        userRepository.save(user);

        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired.access.token")
                .body(Map.of("refreshToken", refreshToken))
                .when().post("/api/v1/auth/reissue")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue());
    }

    @DisplayName("POST /api/v1/auth/reissue : 저장값과 다른 토큰이면 AUTH_004")
    @Test
    void reissueWithMismatchedToken() {
        Users user = 유저를_저장한다("kakao-r2");
        String stale = jwtProvider.createRefreshToken(user.getId(), user.getRole());
        user.updateRefreshToken(jwtProvider.createRefreshToken(user.getId(), user.getRole()));
        userRepository.save(user);

        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", stale))
                .when().post("/api/v1/auth/reissue")
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_004"));
    }

    @DisplayName("POST /api/v1/auth/logout : 로그아웃 API - Refresh Token 이 무효화된다")
    @Test
    void logout() {
        Users user = 유저를_저장한다("kakao-r3");
        user.updateRefreshToken(jwtProvider.createRefreshToken(user.getId(), user.getRole()));
        userRepository.save(user);

        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().post("/api/v1/auth/logout")
                .then()
                .statusCode(HttpStatus.NO_CONTENT.value());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getRefreshToken()).isNull();
    }
}
