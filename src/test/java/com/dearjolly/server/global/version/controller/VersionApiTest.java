package com.dearjolly.server.global.version.controller;

import static com.dearjolly.server.global.version.enums.Platform.AOS;
import static com.dearjolly.server.global.version.enums.Platform.IOS;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.dearjolly.server.support.ApiTestSupport;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class VersionApiTest extends ApiTestSupport {
    @BeforeEach
    void setUpVersions() {
        최소지원버전을_저장한다(IOS, "1.2.0");
        최소지원버전을_저장한다(AOS, "1.0.0");
    }

    @DisplayName("GET /api/v1/version : 인증 없이 최소 지원 버전과 정책 URL 을 반환한다")
    @Test
    void getVersion() {
        given()
                .queryParam("platform", "AOS")
                .queryParam("appVersion", "1.0.0")
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("minSupportedVersion", equalTo("1.0.0"))
                .body("forceUpdate", equalTo(false))
                .body("privacyPolicyUrl", equalTo("https://dearjolly.com/privacy"))
                .body("termsOfServiceUrl", equalTo("https://dearjolly.com/terms"))
                .body("noticeUrl", equalTo("https://dearjolly.com/notice"));
    }

    @DisplayName("GET /api/v1/version : 플랫폼마다 최소 지원 버전이 따로 적용된다")
    @Test
    void getVersionPerPlatform() {
        given().queryParam("platform", "IOS").queryParam("appVersion", "1.1.0")
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("minSupportedVersion", equalTo("1.2.0"))
                .body("forceUpdate", equalTo(true));

        given().queryParam("platform", "AOS").queryParam("appVersion", "1.1.0")
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("minSupportedVersion", equalTo("1.0.0"))
                .body("forceUpdate", equalTo(false));
    }

    @DisplayName("GET /api/v1/version : 앱 버전이 최소 지원 버전보다 낮을 때만 forceUpdate 가 true 다")
    @ParameterizedTest(name = "appVersion={0} 이면 forceUpdate={1}")
    @CsvSource({
            "0.9.9, true",
            "1.1.9, true",
            "1.2.0, false",
            "1.2.1, false",
            "2.0.0, false",
            "1.10.0, false"
    })
    void getVersionCalculatesForceUpdate(String appVersion, boolean forceUpdate) {
        given().queryParam("platform", "IOS").queryParam("appVersion", appVersion)
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("forceUpdate", equalTo(forceUpdate));
    }

    @DisplayName("GET /api/v1/version : appVersion 형식이 잘못되면 VERSION_001")
    @Test
    void getVersionWithMalformedAppVersion() {
        given().queryParam("platform", "IOS").queryParam("appVersion", "1.2")
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("VERSION_001"));
    }

    @DisplayName("GET /api/v1/version : platform 이나 appVersion 이 빠지면 COMMON_001")
    @Test
    void getVersionWithMissingParameter() {
        given().queryParam("appVersion", "1.0.0")
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON_001"));

        given().queryParam("platform", "IOS")
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON_001"));
    }

    @DisplayName("GET /api/v1/version : 알 수 없는 platform 은 COMMON_001")
    @Test
    void getVersionWithUnknownPlatform() {
        given().queryParam("platform", "WINDOWS").queryParam("appVersion", "1.0.0")
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON_001"));
    }

    @DisplayName("PATCH /api/v1/version : 관리자 토큰으로 최소 지원 버전을 바꾼다")
    @Test
    void updateMinSupportedVersion() {
        given()
                .header(HttpHeaders.AUTHORIZATION, 관리자_액세스토큰())
                .contentType(ContentType.JSON)
                .queryParam("platform", "AOS")
                .body(Map.of("minSupportedVersion", "2.0.0"))
                .when().patch("/api/v1/version")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("platform", equalTo("AOS"))
                .body("minSupportedVersion", equalTo("2.0.0"));

        assertThat(appVersionRepository.findById(AOS).orElseThrow().getMinSupportedVersion())
                .isEqualTo("2.0.0");
    }

    @DisplayName("PATCH /api/v1/version : 변경 결과가 곧바로 조회 API 의 판정에 반영된다")
    @Test
    void updateAffectsGetImmediately() {
        given().header(HttpHeaders.AUTHORIZATION, 관리자_액세스토큰())
                .contentType(ContentType.JSON)
                .queryParam("platform", "AOS")
                .body(Map.of("minSupportedVersion", "3.0.0"))
                .when().patch("/api/v1/version")
                .then().statusCode(HttpStatus.OK.value());

        given().queryParam("platform", "AOS").queryParam("appVersion", "2.9.9")
                .when().get("/api/v1/version")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("minSupportedVersion", equalTo("3.0.0"))
                .body("forceUpdate", equalTo(true));
    }

    @DisplayName("PATCH /api/v1/version : 토큰이 없으면 AUTH_005")
    @Test
    void updateWithoutToken() {
        given()
                .contentType(ContentType.JSON)
                .queryParam("platform", "AOS")
                .body(Map.of("minSupportedVersion", "2.0.0"))
                .when().patch("/api/v1/version")
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_005"));
    }

    @DisplayName("PATCH /api/v1/version : 일반 사용자 토큰이면 AUTH_006")
    @Test
    void updateWithUserToken() {
        given()
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(유저를_저장한다("kakao-1")))
                .contentType(ContentType.JSON)
                .queryParam("platform", "AOS")
                .body(Map.of("minSupportedVersion", "2.0.0"))
                .when().patch("/api/v1/version")
                .then()
                .statusCode(HttpStatus.FORBIDDEN.value())
                .body("code", equalTo("AUTH_006"));
    }

    @DisplayName("PATCH /api/v1/version : 버전 형식이 잘못되면 COMMON_001")
    @Test
    void updateWithMalformedVersion() {
        given()
                .header(HttpHeaders.AUTHORIZATION, 관리자_액세스토큰())
                .contentType(ContentType.JSON)
                .queryParam("platform", "AOS")
                .body(Map.of("minSupportedVersion", "2.0"))
                .when().patch("/api/v1/version")
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
