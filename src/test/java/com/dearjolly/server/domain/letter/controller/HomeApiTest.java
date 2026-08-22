package com.dearjolly.server.domain.letter.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.support.ApiTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class HomeApiTest extends ApiTestSupport {
    @DisplayName("GET /api/v1/home : 홈 헤더 정보 조회 API")
    @Test
    void getHome() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-home-1", "ilovesally");
        피드백완료_편지를_저장한다(user, "I got flowers from a friend today.", LocalDate.of(2025, 10, 30), "rose");
        피드백완료_편지를_저장한다(user, "Hi! Jolly. I made a new friend.", LocalDate.of(2025, 10, 29), "pumpkin");
        편지를_저장한다(user, "Lately I've been really worried.", LocalDate.of(2025, 11, 1));

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().get("/api/v1/home")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("nickname", equalTo("ilovesally"))
                .body("totalStampCount", equalTo(2));
    }

    @DisplayName("GET /api/v1/home : 피드백 완료 편지가 없으면 우표 수는 0이다")
    @Test
    void getHomeWithoutStamp() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-home-2", "jolly21");
        편지를_저장한다(user, "Lately I've been really worried.", LocalDate.of(2025, 11, 1));

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().get("/api/v1/home")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("totalStampCount", equalTo(0));
    }

    @DisplayName("GET /api/v1/home : 온보딩을 마치지 않았으면 USER_005")
    @Test
    void getHomeBeforeOnboarding() {
        // given
        Users user = 유저를_저장한다("kakao-home-3");

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().get("/api/v1/home")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("USER_005"));
    }

    @DisplayName("GET /api/v1/home : 토큰이 없으면 AUTH_005")
    @Test
    void getHomeWithoutToken() {
        // when
        given().when().get("/api/v1/home")
                // then
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_005"));
    }
}
