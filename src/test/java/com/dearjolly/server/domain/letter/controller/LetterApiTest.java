package com.dearjolly.server.domain.letter.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.enums.Status;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.support.ApiTestSupport;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class LetterApiTest extends ApiTestSupport {
    private static final String CONTENT = "I got flowers from a friend today. It really touched me.";
    private static final String TIME_ZONE = "Asia/Seoul";

    @Autowired
    LetterRepository letterRepository;

    @DisplayName("POST /api/v1/letters : 편지 작성 API")
    @Test
    void createLetter() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-letter-1", "jolly01");
        LocalDateTime writtenAt = LocalDateTime.now();

        // when
        long letterId = given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(편지_요청(CONTENT, writtenAt, TIME_ZONE))
                .when().post("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .body("letterId", notNullValue())
                .body("date", equalTo(writtenAt.toLocalDate().toString()))
                .body("createdAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"))
                .extract().jsonPath().getLong("letterId");

        Letters saved = letterRepository.findById(letterId).orElseThrow();
        assertThat(saved.getContent()).isEqualTo(CONTENT);
        assertThat(saved.getTimeZone()).isEqualTo(TIME_ZONE);
        assertThat(saved.getStatus()).isEqualTo(Status.SUBMITTED);
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getStamp()).isNull();
    }

    @DisplayName("POST /api/v1/letters : 60초 이내 같은 본문을 다시 보내면 200 으로 최초 편지를 반환한다")
    @Test
    void createLetterDuplicated() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-letter-2", "jolly02");
        Map<String, Object> body = 편지_요청(CONTENT, LocalDateTime.now(), TIME_ZONE);

        JsonPath first = given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(body)
                .when().post("/api/v1/letters")
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .extract().jsonPath();

        // when
        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(body)
                .when().post("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("letterId", equalTo(first.getInt("letterId")))
                .body("date", equalTo(first.getString("date")))
                .body("createdAt", equalTo(first.getString("createdAt")));

        assertThat(letterRepository.findAll()).hasSize(1);
    }

    @DisplayName("POST /api/v1/letters : 본문이 공백만 있으면 LETTER_001")
    @Test
    void createLetterWithBlankContent() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-letter-3", "jolly03");

        // when
        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(편지_요청("   ", LocalDateTime.now(), TIME_ZONE))
                .when().post("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("status", equalTo(400))
                .body("code", equalTo("LETTER_001"));
    }

    @DisplayName("POST /api/v1/letters : 본문이 500자를 초과하면 LETTER_003")
    @Test
    void createLetterWithTooLongContent() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-letter-4", "jolly04");

        // when
        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(편지_요청("a".repeat(501), LocalDateTime.now(), TIME_ZONE))
                .when().post("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("LETTER_003"));
    }

    @DisplayName("POST /api/v1/letters : 본문에 한글이 섞이면 LETTER_004")
    @Test
    void createLetterWithKorean() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-letter-5", "jolly05");

        // when
        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(편지_요청(CONTENT + "한글", LocalDateTime.now(), TIME_ZONE))
                .when().post("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("LETTER_004"));
    }

    @DisplayName("POST /api/v1/letters : 해석할 수 없는 타임존이면 LETTER_005")
    @Test
    void createLetterWithInvalidTimeZone() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-letter-6", "jolly06");

        // when
        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(편지_요청(CONTENT, LocalDateTime.now(), "Mars/Olympus"))
                .when().post("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("LETTER_005"));
    }

    @DisplayName("POST /api/v1/letters : 작성 시각이 서버 시각 기준 ±24시간을 벗어나면 LETTER_005")
    @Test
    void createLetterWithOutOfRangeWrittenAt() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-letter-7", "jolly07");

        // when
        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(편지_요청(CONTENT, LocalDateTime.now().plusDays(2), TIME_ZONE))
                .when().post("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("LETTER_005"));
    }

    @DisplayName("POST /api/v1/letters : 온보딩을 마치지 않았으면 USER_005")
    @Test
    void createLetterBeforeOnboarding() {
        // given
        Users user = 유저를_저장한다("kakao-letter-8");

        // when
        given().contentType(ContentType.JSON)
                .header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .body(편지_요청(CONTENT, LocalDateTime.now(), TIME_ZONE))
                .when().post("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("USER_005"));
    }

    @DisplayName("POST /api/v1/letters : 토큰이 없으면 AUTH_005")
    @Test
    void createLetterWithoutToken() {
        // when
        given().contentType(ContentType.JSON)
                .body(편지_요청(CONTENT, LocalDateTime.now(), TIME_ZONE))
                .when().post("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .body("code", equalTo("AUTH_005"));
    }

    private Map<String, Object> 편지_요청(String content, LocalDateTime writtenAt, String timeZone) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("writtenAt", writtenAt.withNano(0).toString());
        body.put("timeZone", timeZone);
        return body;
    }
}
