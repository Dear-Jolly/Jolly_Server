package com.dearjolly.server.global.admin.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.global.version.enums.Platform;
import com.dearjolly.server.support.ApiTestSupport;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class AdminApiTest extends ApiTestSupport {
    private static final String USERNAME = "test-admin";
    private static final String PASSWORD = "test-admin-password";

    @DisplayName("POST /api/v1/admin/login : 아이디와 비밀번호가 맞으면 관리자 토큰을 발급한다")
    @Test
    void login() {
        관리자_유저를_저장한다("seed-admin", "jolly");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", USERNAME, "password", PASSWORD))
                .when().post("/api/v1/admin/login")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("accessToken", not(emptyOrNullString()))
                .body("refreshToken", not(emptyOrNullString()));
    }

    @DisplayName("POST /api/v1/admin/login : 발급한 토큰으로 관리자 전용 API 를 호출할 수 있다")
    @Test
    void loginIssuesUsableAdminToken() {
        관리자_유저를_저장한다("seed-admin", "jolly");
        최소지원버전을_저장한다(Platform.IOS, "1.0.0");

        String accessToken = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", USERNAME, "password", PASSWORD))
                .when().post("/api/v1/admin/login")
                .then().statusCode(HttpStatus.OK.value())
                .extract().path("accessToken");

        given()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .queryParam("platform", "IOS")
                .body(Map.of("minSupportedVersion", "1.5.0"))
                .when().patch("/api/v1/admin/version")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("minSupportedVersion", equalTo("1.5.0"));
    }

    @DisplayName("POST /api/v1/admin/login : 시드가 만든 관리자 계정으로 토큰을 발급한다")
    @Test
    void loginBindsToSeededAdminUser() {
        Users admin = 관리자_유저를_저장한다("seed-admin", "jolly");

        String accessToken = 관리자_토큰을_발급받는다();

        assertThat(jwtProvider.getUserId(accessToken)).isEqualTo(admin.getId());
    }

    @DisplayName("POST /api/v1/admin/login : 관리자는 회원가입을 하지 않는다. 계정이 없으면 AUTH_008")
    @Test
    void loginWithoutSeededAdmin() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", USERNAME, "password", PASSWORD))
                .when().post("/api/v1/admin/login")
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_008"));

        assertThat(userRepository.count()).isZero();
    }

    @DisplayName("관리자 토큰은 소셜 로그인 토큰과 똑같이 앱 사용자 API 에도 쓰인다")
    @Test
    void adminTokenCallsUserApi() {
        관리자_유저를_저장한다("seed-admin", "jolly");

        given()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + 관리자_토큰을_발급받는다())
                .when().get("/api/v1/home")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("nickname", equalTo("jolly"));
    }

    @DisplayName("POST /api/v1/admin/login : 발급한 Refresh Token 으로 토큰을 재발급할 수 있다")
    @Test
    void adminRefreshTokenWorks() {
        관리자_유저를_저장한다("seed-admin", "jolly");

        String refreshToken = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", USERNAME, "password", PASSWORD))
                .when().post("/api/v1/admin/login")
                .then().statusCode(HttpStatus.OK.value())
                .extract().path("refreshToken");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshToken))
                .when().post("/api/v1/auth/reissue")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("accessToken", not(emptyOrNullString()));
    }

    @DisplayName("POST /api/v1/admin/login : 비밀번호가 틀리면 AUTH_008")
    @Test
    void loginWithWrongPassword() {
        관리자_유저를_저장한다("seed-admin", "jolly");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", USERNAME, "password", "wrong-password"))
                .when().post("/api/v1/admin/login")
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_008"));
    }

    @DisplayName("POST /api/v1/admin/login : 아이디가 틀리면 AUTH_008")
    @Test
    void loginWithWrongUsername() {
        관리자_유저를_저장한다("seed-admin", "jolly");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "nobody", "password", PASSWORD))
                .when().post("/api/v1/admin/login")
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_008"));
    }

    private String 관리자_토큰을_발급받는다() {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", USERNAME, "password", PASSWORD))
                .when().post("/api/v1/admin/login")
                .then().statusCode(HttpStatus.OK.value())
                .extract().path("accessToken");
    }

    @DisplayName("POST /api/v1/admin/login : 아이디나 비밀번호가 비면 COMMON_001")
    @Test
    void loginWithBlankCredentials() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "", "password", PASSWORD))
                .when().post("/api/v1/admin/login")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON_001"));
    }
}
