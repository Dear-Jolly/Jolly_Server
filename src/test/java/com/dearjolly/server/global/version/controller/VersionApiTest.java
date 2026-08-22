package com.dearjolly.server.global.version.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.dearjolly.server.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class VersionApiTest extends ApiTestSupport {

    @DisplayName("GET /api/v1/version : 인증 없이 공통 버전과 정책 URL 을 반환한다")
    @Test
    void getVersion() {
        given()
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("latestVersion", equalTo("1.2.0"))
                .body("minSupportedVersion", equalTo("1.0.0"))
                .body("forceUpdate", equalTo(false))
                .body("privacyPolicyUrl", equalTo("https://dearjolly.com/privacy"))
                .body("termsOfServiceUrl", equalTo("https://dearjolly.com/terms"))
                .body("noticeUrl", equalTo("https://dearjolly.com/notice"));
    }

    @DisplayName("GET /api/v1/version : platform 을 주면 그 플랫폼 재정의가 우선한다")
    @Test
    void getVersionForIos() {
        given().queryParam("platform", "IOS")
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.OK.value())
                // latest 는 재정의가 비어 있어 공통 값, minSupported 만 IOS 값이다
                .body("latestVersion", equalTo("1.2.0"))
                .body("minSupportedVersion", equalTo("1.1.0"));
    }

    @DisplayName("GET /api/v1/version : 재정의가 없는 플랫폼은 공통 값을 그대로 받는다")
    @Test
    void getVersionForAos() {
        given().queryParam("platform", "AOS")
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("minSupportedVersion", equalTo("1.0.0"));
    }

    @DisplayName("GET /api/v1/version : 알 수 없는 platform 은 COMMON_001")
    @Test
    void getVersionWithUnknownPlatform() {
        given().queryParam("platform", "WINDOWS")
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON_001"));
    }

    @DisplayName("GET /actuator/health : 인증 없이 UP 을 반환한다")
    @Test
    void health() {
        given()
                .when().get("/actuator/health")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("status", equalTo("UP"));
    }
}
