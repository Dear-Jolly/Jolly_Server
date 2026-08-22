package com.dearjolly.server.domain.user.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.support.ApiTestSupport;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class AuthApiTest extends ApiTestSupport {

    @DisplayName("GET /api/v1/auth/{provider} : 카카오 로그인 페이지로 302 리다이렉트한다")
    @Test
    void authorizeKakao() {
        // when & then
        given().redirects().follow(false)
                .when().get("/api/v1/auth/KAKAO")
                .then()
                .statusCode(HttpStatus.FOUND.value())
                .header(HttpHeaders.LOCATION, containsString("kauth.kakao.com/oauth/authorize"))
                .header(HttpHeaders.LOCATION, containsString("response_type=code"));
    }

    @DisplayName("GET /api/v1/auth/{provider} : 애플은 form_post 로 콜백받도록 요청한다")
    @Test
    void authorizeApple() {
        // when & then
        given().redirects().follow(false)
                .when().get("/api/v1/auth/APPLE")
                .then()
                .statusCode(HttpStatus.FOUND.value())
                .header(HttpHeaders.LOCATION, containsString("appleid.apple.com/auth/authorize"))
                .header(HttpHeaders.LOCATION, containsString("response_mode=form_post"));
    }

    @DisplayName("GET /api/v1/auth/{provider} : 지원하지 않는 provider 면 COMMON_001")
    @Test
    void authorizeUnsupportedProvider() {
        // when & then
        given().redirects().follow(false)
                .when().get("/api/v1/auth/NAVER")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON_001"));
    }

    @DisplayName("POST /api/v1/auth/reissue : 토큰 재발급 API - 회전된 값이 저장된다")
    @Test
    void reissue() {
        // given
        Users user = 유저를_저장한다("kakao-r1");
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getRole());
        user.updateRefreshToken(refreshToken);
        userRepository.save(user);

        // when
        String reissued = given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshToken))
                .when().post("/api/v1/auth/reissue")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .extract().path("refreshToken");

        // then - 저장값이 새 토큰으로 교체된다
        assertThat(userRepository.findById(user.getId()).orElseThrow().getRefreshToken()).isEqualTo(reissued);
    }

    @DisplayName("POST /api/v1/auth/reissue : 저장값과 다른 토큰이면 AUTH_004")
    @Test
    void reissueWithMismatchedToken() {
        // given - 발급은 됐지만 서버에 저장되지 않은 토큰 (탈취된 이전 토큰 상황)
        Users user = 유저를_저장한다("kakao-r2");
        String stale = jwtProvider.createRefreshToken(user.getId(), user.getRole());
        user.updateRefreshToken(jwtProvider.createRefreshToken(user.getId(), user.getRole()));
        userRepository.save(user);

        // when & then
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
        // given
        Users user = 유저를_저장한다("kakao-r3");
        user.updateRefreshToken(jwtProvider.createRefreshToken(user.getId(), user.getRole()));
        userRepository.save(user);

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().post("/api/v1/auth/logout")
                .then()
                .statusCode(HttpStatus.NO_CONTENT.value());

        // then
        assertThat(userRepository.findById(user.getId()).orElseThrow().getRefreshToken()).isNull();
    }
}
