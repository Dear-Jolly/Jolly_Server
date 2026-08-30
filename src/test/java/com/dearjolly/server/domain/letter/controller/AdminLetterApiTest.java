package com.dearjolly.server.domain.letter.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.enums.Status;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.support.ApiTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class AdminLetterApiTest extends ApiTestSupport {
    private static final String CONTENT = "I go to school yesterday.";
    private static final LocalDate LETTER_DATE = LocalDate.of(2026, 8, 30);

    @DisplayName("GET /api/v1/admin/letters/failed : 피드백 실패 편지 조회 API")
    @Test
    void getFailedLetters() {
        // given - 실패 1통 + 아직 실패한 적 없는 대기 1통 + 완료 1통
        Users user = 온보딩을_마친_유저를_저장한다("kakao-admin-letter-1", "jolly");
        Letters failed = 첫_실패후_재시도를_기다리는_편지를_저장한다(user, CONTENT, LETTER_DATE);
        편지를_저장한다(user, "I am waiting for feedback.", LETTER_DATE);
        피드백완료_편지를_저장한다(user, "I finished my homework.", LETTER_DATE, "rose");

        // when
        given()
                .header(HttpHeaders.AUTHORIZATION, 관리자_액세스토큰())
                .when().get("/api/v1/admin/letters/failed")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("hasNext", equalTo(false))
                .body("letters", hasSize(1))
                .body("letters[0].letterId", equalTo(failed.getId().intValue()))
                .body("letters[0].userId", equalTo(user.getId().intValue()))
                .body("letters[0].nickname", equalTo("jolly"))
                .body("letters[0].content", equalTo(CONTENT))
                .body("letters[0].status", equalTo(Status.SUBMITTED.name()))
                .body("letters[0].retryCount", equalTo(1));
    }

    @DisplayName("GET /api/v1/admin/letters/failed : 실패한 편지가 없으면 빈 배열이다")
    @Test
    void getFailedLettersEmpty() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-admin-letter-2", "jolly02");
        편지를_저장한다(user, CONTENT, LETTER_DATE);

        // when
        given()
                .header(HttpHeaders.AUTHORIZATION, 관리자_액세스토큰())
                .when().get("/api/v1/admin/letters/failed")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("letters", hasSize(0))
                .body("hasNext", equalTo(false));
    }

    @DisplayName("GET /api/v1/admin/letters/failed : 일반 사용자 토큰이면 AUTH_006")
    @Test
    void getFailedLettersWithoutAdminRole() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-admin-letter-3", "jolly03");

        // when
        given()
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().get("/api/v1/admin/letters/failed")
                // then
                .then()
                .statusCode(HttpStatus.FORBIDDEN.value())
                .body("code", equalTo("AUTH_006"));
    }

    @DisplayName("GET /api/v1/admin/letters/failed : size 가 50을 넘으면 COMMON_001")
    @Test
    void getFailedLettersWithTooLargeSize() {
        // when
        given()
                .header(HttpHeaders.AUTHORIZATION, 관리자_액세스토큰())
                .queryParam("size", 51)
                .when().get("/api/v1/admin/letters/failed")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON_001"));
    }

    @DisplayName("POST /api/v1/admin/letters/{letterId}/feedback/retry : 피드백 재시도 API")
    @Test
    void retryFeedback() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-admin-letter-4", "jolly04");
        Letters failed = 첫_실패후_재시도를_기다리는_편지를_저장한다(user, CONTENT, LETTER_DATE);

        // when
        given()
                .header(HttpHeaders.AUTHORIZATION, 관리자_액세스토큰())
                .when().post("/api/v1/admin/letters/{letterId}/feedback/retry", failed.getId())
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("letterId", equalTo(failed.getId().intValue()))
                .body("status", equalTo(Status.SUBMITTED.name()))
                .body("retryCount", equalTo(0))
                .body("nextRetryAt", notNullValue());
    }

    @DisplayName("POST /api/v1/admin/letters/{letterId}/feedback/retry : 이미 완료된 편지는 LETTER_006")
    @Test
    void retryCompletedFeedback() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-admin-letter-5", "jolly05");
        Letters completed = 피드백완료_편지를_저장한다(user, CONTENT, LETTER_DATE, "pumpkin");

        // when
        given()
                .header(HttpHeaders.AUTHORIZATION, 관리자_액세스토큰())
                .when().post("/api/v1/admin/letters/{letterId}/feedback/retry", completed.getId())
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("LETTER_006"));
    }

    @DisplayName("POST /api/v1/admin/letters/{letterId}/feedback/retry : 처리 중인 편지는 LETTER_007")
    @Test
    void retryInProgressFeedback() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-admin-letter-6", "jolly06");
        Letters letter = 편지를_저장한다(user, CONTENT, LETTER_DATE);
        처리중으로_바꾼다(letter);

        // when
        given()
                .header(HttpHeaders.AUTHORIZATION, 관리자_액세스토큰())
                .when().post("/api/v1/admin/letters/{letterId}/feedback/retry", letter.getId())
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("LETTER_007"));

        assertThat(letterRepository.findById(letter.getId()).orElseThrow().getStatus())
                .isEqualTo(Status.FEEDBACK_IN_PROGRESS);
    }

    @DisplayName("POST /api/v1/admin/letters/{letterId}/feedback/retry : 없는 편지는 LETTER_002")
    @Test
    void retryFeedbackOfMissingLetter() {
        // when
        given()
                .header(HttpHeaders.AUTHORIZATION, 관리자_액세스토큰())
                .when().post("/api/v1/admin/letters/{letterId}/feedback/retry", 999_999L)
                // then
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .body("code", equalTo("LETTER_002"));
    }

    @DisplayName("POST /api/v1/admin/letters/{letterId}/feedback/retry : 토큰이 없으면 AUTH_005")
    @Test
    void retryFeedbackWithoutToken() {
        // when
        given()
                .when().post("/api/v1/admin/letters/{letterId}/feedback/retry", 1L)
                // then
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_005"));
    }

    private void 처리중으로_바꾼다(Letters letter) {
        transactionTemplate.execute(status -> {
            Letters found = letterRepository.findById(letter.getId()).orElseThrow();
            found.startFeedback();
            return found;
        });
    }
}
