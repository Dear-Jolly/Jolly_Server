package com.dearjolly.server.global.config;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import com.dearjolly.server.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class OpenApiDocsTest extends ApiTestSupport {
    @DisplayName("GET /v3/api-docs : 인증 없이 열리고 모든 엔드포인트가 실린다")
    @Test
    void apiDocs() {
        given()
                .when().get("/v3/api-docs")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("info.title", equalTo("Dear Jolly API"))
                .body("paths", hasKey("/api/v1/auth/{provider}"))
                .body("paths", hasKey("/api/v1/auth/kakao/callback"))
                .body("paths", hasKey("/api/v1/auth/apple/callback"))
                .body("paths", hasKey("/api/v1/auth/reissue"))
                .body("paths", hasKey("/api/v1/auth/logout"))
                .body("paths", hasKey("/api/v1/users"))
                .body("paths", hasKey("/api/v1/users/terms"))
                .body("paths", hasKey("/api/v1/users/nickname"))
                .body("paths", hasKey("/api/v1/letters"))
                .body("paths", hasKey("/api/v1/letters/{letterId}"))
                .body("paths", hasKey("/api/v1/home"))
                .body("paths", hasKey("/api/v1/version"))
                .body("components.securitySchemes.bearerAuth.scheme", equalTo("bearer"));
    }

    @DisplayName("GET /v3/api-docs : 도메인별 그룹 문서가 각각 열린다")
    @ValueSource(strings = {"all", "auth", "user", "letter", "version"})
    @ParameterizedTest
    void groupedApiDocs(String group) {
        given()
                .when().get("/v3/api-docs/" + group)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("paths", notNullValue());
    }

    @DisplayName("GET /v3/api-docs : @LoginUser 는 쿼리 파라미터로 노출되지 않는다")
    @Test
    void loginUserIsNotExposedAsParameter() {
        given()
                .when().get("/v3/api-docs")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("paths.'/api/v1/home'.get", not(hasKey("parameters")));
    }

    @DisplayName("GET /v3/api-docs : 인증이 필요 없는 API 만 security 가 비어 있다")
    @Test
    void publicApiHasNoSecurity() {
        given()
                .when().get("/v3/api-docs")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("security[0]", hasKey("bearerAuth"))
                .body("paths.'/api/v1/version'.get.security", empty())
                .body("paths.'/api/v1/auth/{provider}'.get.security", empty())
                .body("paths.'/api/v1/auth/reissue'.post.security", empty())
                .body("paths.'/api/v1/auth/logout'.post", not(hasKey("security")));
    }

    @DisplayName("GET /v3/api-docs : 편지 상태에 내부 전용 FEEDBACK_FAILED 는 실리지 않는다")
    @Test
    void internalStatusIsNotDocumented() {
        given()
                .when().get("/v3/api-docs")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("components.schemas.LetterGetResponse.properties.status.enum",
                        contains("SUBMITTED", "FEEDBACK_IN_PROGRESS", "FEEDBACK_COMPLETED"))
                .body("components.schemas.LetterSummaryResponse.properties.status.enum",
                        contains("SUBMITTED", "FEEDBACK_IN_PROGRESS", "FEEDBACK_COMPLETED"));
    }

    @DisplayName("GET /v3/api-docs/swagger-config : 그룹 선택 없이 열면 전체 문서가 뜬다")
    @Test
    void primaryGroupIsAll() {
        given()
                .when().get("/v3/api-docs/swagger-config")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("urls.name", hasItem("전체"))
                .body("urls.find { it.name == '전체' }.url", equalTo("/v3/api-docs/all"))
                .body("'urls.primaryName'", equalTo("전체"));
    }

    @DisplayName("GET /swagger-ui/index.html : Swagger UI 가 인증 없이 열린다")
    @Test
    void swaggerUi() {
        given()
                .when().get("/swagger-ui/index.html")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body(containsString("swagger-ui"));
    }
}
