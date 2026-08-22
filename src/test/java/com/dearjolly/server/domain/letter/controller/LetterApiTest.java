package com.dearjolly.server.domain.letter.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.enums.Status;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.support.ApiTestSupport;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import java.time.LocalDate;
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


    @DisplayName("GET /api/v1/letters : 편지 목록 조회 API")
    @Test
    void getLetters() {
        // given - 홈 화면 시나리오: 피드백 대기 1통 + 완료 2통(미열람 1, 열람 1)
        Users user = 온보딩을_마친_유저를_저장한다("kakao-list-1", "ilovesally");
        편지를_저장한다(user, "Lately I've been really worried about my new job.", LocalDate.of(2025, 11, 1));
        피드백완료_편지를_저장한다(user, "I got flowers from a friend today.", LocalDate.of(2025, 10, 30), "rose");
        Letters read = 피드백완료_편지를_저장한다(user, "Hi! Jolly. I made a new friend.", LocalDate.of(2025, 10, 29), "pumpkin");
        read.markAsRead();
        letterRepository.save(read);

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().get("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("hasNext", equalTo(false))
                .body("letters", hasSize(3))
                .body("letters.date", contains("2025-11-01", "2025-10-30", "2025-10-29"))
                .body("letters[0].status", equalTo("SUBMITTED"))
                .body("letters[0].stampImage", nullValue())
                .body("letters[0].isRead", equalTo(false))
                .body("letters[1].status", equalTo("FEEDBACK_COMPLETED"))
                .body("letters[1].stampImage", equalTo("http://localhost:9000/dear-jolly-stamps/stamps/rose.png"))
                .body("letters[1].isRead", equalTo(false))
                .body("letters[2].status", equalTo("FEEDBACK_COMPLETED"))
                .body("letters[2].isRead", equalTo(true))
                .body("letters[2].summary", equalTo("Hi! Jolly. I made a new friend."));
    }

    @DisplayName("GET /api/v1/letters : summary 는 원문 앞 50자다")
    @Test
    void getLettersSummaryIsTruncated() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-list-2", "jolly11");
        String content = "a".repeat(60);
        편지를_저장한다(user, content, LocalDate.of(2025, 11, 1));

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().get("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("letters[0].summary", equalTo("a".repeat(50)));
    }

    @DisplayName("GET /api/v1/letters : 같은 날짜 편지는 나중에 쓴 것이 먼저 온다")
    @Test
    void getLettersOrdersSameDateByIdDesc() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-list-3", "jolly12");
        편지를_저장한다(user, "first letter of the day", LocalDate.of(2025, 11, 1));
        편지를_저장한다(user, "second letter of the day", LocalDate.of(2025, 11, 1));

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().get("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("letters.summary", contains("second letter of the day", "first letter of the day"));
    }

    @DisplayName("GET /api/v1/letters : sort=OLDEST 는 오래된 순으로 정렬한다")
    @Test
    void getLettersOldestFirst() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-list-4", "jolly13");
        편지를_저장한다(user, "older letter", LocalDate.of(2025, 10, 29));
        편지를_저장한다(user, "newer letter", LocalDate.of(2025, 11, 1));

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .queryParam("sort", "OLDEST")
                .when().get("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("letters.summary", contains("older letter", "newer letter"));
    }

    @DisplayName("GET /api/v1/letters : 다음 페이지가 있으면 hasNext 가 true 다")
    @Test
    void getLettersHasNext() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-list-5", "jolly14");
        편지를_저장한다(user, "first letter", LocalDate.of(2025, 10, 29));
        편지를_저장한다(user, "second letter", LocalDate.of(2025, 10, 30));
        편지를_저장한다(user, "third letter", LocalDate.of(2025, 11, 1));

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .queryParam("size", 2)
                .when().get("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("letters", hasSize(2))
                .body("hasNext", equalTo(true));

        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .queryParam("size", 2)
                .queryParam("page", 1)
                .when().get("/api/v1/letters")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("letters", hasSize(1))
                .body("hasNext", equalTo(false));
    }

    @DisplayName("GET /api/v1/letters : 다른 유저의 편지는 조회되지 않는다")
    @Test
    void getLettersOnlyOwn() {
        // given
        Users mine = 온보딩을_마친_유저를_저장한다("kakao-list-6", "jolly15");
        Users other = 온보딩을_마친_유저를_저장한다("kakao-list-7", "jolly16");
        편지를_저장한다(mine, "my letter", LocalDate.of(2025, 11, 1));
        피드백완료_편지를_저장한다(other, "other letter", LocalDate.of(2025, 11, 1), "star");

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(mine))
                .when().get("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("letters", hasSize(1))
                .body("letters[0].summary", equalTo("my letter"));
    }

    @DisplayName("GET /api/v1/letters : 편지가 없으면 빈 목록을 반환한다")
    @Test
    void getLettersEmpty() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-list-8", "jolly17");

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().get("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("letters", hasSize(0))
                .body("hasNext", equalTo(false));
    }

    @DisplayName("GET /api/v1/letters : size 가 50을 넘으면 COMMON_001")
    @Test
    void getLettersWithTooLargeSize() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-list-9", "jolly18");

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .queryParam("size", 51)
                .when().get("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON_001"));
    }

    @DisplayName("GET /api/v1/letters : page 가 음수면 COMMON_001")
    @Test
    void getLettersWithNegativePage() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-list-10", "jolly19");

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .queryParam("page", -1)
                .when().get("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON_001"));
    }

    @DisplayName("GET /api/v1/letters : sort 가 허용값이 아니면 COMMON_001")
    @Test
    void getLettersWithInvalidSort() {
        // given
        Users user = 온보딩을_마친_유저를_저장한다("kakao-list-11", "jolly20");

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .queryParam("sort", "RANDOM")
                .when().get("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON_001"));
    }

    @DisplayName("GET /api/v1/letters : 온보딩을 마치지 않았으면 USER_005")
    @Test
    void getLettersBeforeOnboarding() {
        // given
        Users user = 유저를_저장한다("kakao-list-12");

        // when
        given().header(HttpHeaders.AUTHORIZATION, 액세스토큰(user))
                .when().get("/api/v1/letters")
                // then
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("USER_005"));
    }

    @DisplayName("GET /api/v1/letters : 토큰이 없으면 AUTH_005")
    @Test
    void getLettersWithoutToken() {
        // when
        given().when().get("/api/v1/letters")
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
