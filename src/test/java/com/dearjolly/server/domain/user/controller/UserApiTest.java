package com.dearjolly.server.domain.user.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.support.ApiTestSupport;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class UserApiTest extends ApiTestSupport {
    @DisplayName("POST /api/v1/users/terms : 약관 동의 API")
    @Test
    void agreeTerms() {
        Users user = 유저를_저장한다("kakao-1");
        Map<String, Object> body = Map.of("agreements", java.util.List.of(
                Map.of("type", "SERVICE", "agreed", true),
                Map.of("type", "PRIVACY", "agreed", true),
                Map.of("type", "MARKETING", "agreed", false)
        ));

        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(body)
                .when().post("/api/v1/users/terms")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("termsAgreed", equalTo(true));

        assertThat(termsAgreementRepository.findAllByUserIdOrderByAgreedAtDesc(user.getId())).hasSize(3);
    }

    @DisplayName("POST /api/v1/users/terms : 필수 약관에 동의하지 않으면 USER_002")
    @Test
    void agreeTermsWithoutRequired() {
        Users user = 유저를_저장한다("kakao-2");
        Map<String, Object> body = Map.of("agreements", java.util.List.of(
                Map.of("type", "SERVICE", "agreed", true),
                Map.of("type", "PRIVACY", "agreed", false)
        ));

        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(body)
                .when().post("/api/v1/users/terms")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("USER_002"))
                .body("status", equalTo(400));
    }

    @DisplayName("GET /api/v1/users : 계정 정보 조회 API")
    @Test
    void getUser() {
        Users user = 온보딩을_마친_유저를_저장한다("kakao-3", "jolly01");

        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().get("/api/v1/users")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("nickname", equalTo("jolly01"))
                .body("provider", equalTo("KAKAO"))
                .body("email", equalTo("jolly@example.com"))
                .body("marketingAgreed", equalTo(false));
    }

    @DisplayName("GET /api/v1/users : 온보딩 전이면 닉네임이 null 로 내려간다")
    @Test
    void getUserBeforeOnboarding() {
        Users user = 유저를_저장한다("kakao-4");

        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().get("/api/v1/users")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("nickname", nullValue());
    }

    @DisplayName("GET /api/v1/users : 토큰이 없으면 AUTH_005")
    @Test
    void getUserWithoutToken() {
        given().when().get("/api/v1/users")
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_005"));
    }

    @DisplayName("PATCH /api/v1/users/nickname : 닉네임 설정 API")
    @Test
    void updateNickname() {
        Users user = 유저를_저장한다("kakao-5");

        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(Map.of("nickname", "iloveJolly"))
                .when().patch("/api/v1/users/nickname")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("nickname", equalTo("iloveJolly"));
    }

    @DisplayName("PATCH /api/v1/users/nickname : 21자면 길이 위반이므로 USER_004")
    @Test
    void updateNicknameTooLong() {
        Users user = 유저를_저장한다("kakao-6");

        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(Map.of("nickname", "a".repeat(21)))
                .when().patch("/api/v1/users/nickname")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("USER_004"));
    }

    @DisplayName("PATCH /api/v1/users/nickname : 한글이 섞이면 문자 위반이므로 USER_003")
    @Test
    void updateNicknameWithHangul() {
        Users user = 유저를_저장한다("kakao-7");

        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(Map.of("nickname", "졸리조아"))
                .when().patch("/api/v1/users/nickname")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("USER_003"));
    }

    @DisplayName("PATCH /api/v1/users/nickname : 빈 값은 0자이므로 USER_004")
    @Test
    void updateNicknameWithEmpty() {
        Users user = 유저를_저장한다("kakao-n4");

        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(Map.of("nickname", ""))
                .when().patch("/api/v1/users/nickname")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("USER_004"));
    }

    @DisplayName("PATCH /api/v1/users/nickname : 공백만 있는 값은 길이를 통과하므로 USER_003")
    @Test
    void updateNicknameWithOnlyBlank() {
        Users user = 유저를_저장한다("kakao-n5");

        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(Map.of("nickname", "   "))
                .when().patch("/api/v1/users/nickname")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("USER_003"));
    }

    @DisplayName("DELETE /api/v1/users : 회원 탈퇴 API - soft delete 후 접근이 차단된다")
    @Test
    void withdraw() {
        Users user = 온보딩을_마친_유저를_저장한다("kakao-8", "jolly02");
        String token = 액세스토큰(user);

        given().header(HttpHeaders.AUTHORIZATION, token)
                .when().delete("/api/v1/users")
                .then()
                .statusCode(HttpStatus.NO_CONTENT.value());

        Users withdrawn = userRepository.findById(user.getId()).orElseThrow();
        assertThat(withdrawn.isWithdrawn()).isTrue();
        assertThat(withdrawn.getDeletedAt()).isNotNull();
        assertThat(withdrawn.getRefreshToken()).isNull();

        given().header(HttpHeaders.AUTHORIZATION, token)
                .when().get("/api/v1/users")
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_007"));
    }
}
