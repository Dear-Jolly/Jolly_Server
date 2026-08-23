package com.dearjolly.server.domain.letter.controller;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.domain.letter.dto.request.LetterCreateRequest;
import com.dearjolly.server.domain.letter.dto.response.LetterCreateResponse;
import com.dearjolly.server.domain.letter.dto.response.LetterCreateResult;
import com.dearjolly.server.domain.letter.dto.response.LetterGetResponse;
import com.dearjolly.server.domain.letter.dto.response.LetterListResponse;
import com.dearjolly.server.domain.letter.enums.LetterSort;
import com.dearjolly.server.domain.letter.service.LetterService;
import com.dearjolly.server.global.auth.principal.LoginUser;
import com.dearjolly.server.global.exception.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "편지", description = """
        편지 작성 · 조회와 홈 헤더 조회 API. **온보딩 가드 대상**이라 미완료 유저가 호출하면 `USER_005`(400) 다.

        편지는 전달 후 수정 · 삭제할 수 없다. 상태는 `SUBMITTED` → `FEEDBACK_IN_PROGRESS` → `FEEDBACK_COMPLETED` 로 진행되며 앱은 앞의 둘을 동일하게 렌더링한다.""")
@RestController
@RequestMapping("/api/v1/letters")
@RequiredArgsConstructor
public class LetterController {
    private final LetterService letterService;

    @Operation(
            summary = "편지 작성 및 피드백 요청 (60초 이내 같은 본문 재전송은 200 으로 최초 편지 반환)",
            description = """
                    저장되면 AI 피드백이 시작되지만 **응답은 피드백을 기다리지 않고 바로 내려온다.**

                    - 본문은 **영어 전용, 1~500자**다. 숫자 · 구두점 · 이모지는 허용한다.
                    - 편지 날짜는 `writtenAt` 의 날짜 부분이다. KST 를 강제하지 않아 해외에서 쓴 편지는 현지 날짜로 기록된다.
                    - 하루 작성 개수 제한이 없다. 작성 직후 우표는 항상 `soon`(준비 중) 우표다.
                    - 검증은 `content` → `timeZone` → `writtenAt` 순서로 보며 먼저 걸린 사유 하나만 내려간다.
                    - **60초 안에 같은 내용을 다시 보내면** 새 편지를 만들지 않고 최초 편지를 `200` 으로 돌려준다.
                      앱은 `201` 과 `200` 을 구분할 필요 없다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "작성 성공"),
            @ApiResponse(
                    responseCode = "200",
                    description = "중복 전달. 60초 안에 같은 내용을 다시 보낸 경우이며, 본문은 최초 편지의 201 응답과 완전히 같다",
                    content = @Content(schema = @Schema(implementation = LetterCreateResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "`LETTER_001` · `LETTER_003` · `LETTER_004` · `LETTER_005` 검증 실패 / "
                            + "`COMMON_001` 날짜 파싱 실패 · 바디 형식 오류 / `USER_005` 온보딩 미완료",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "LETTER_001",
                                            value = """
                                                    {"status": 400, "code": "LETTER_001", "message": "편지 내용은 null일 수 없습니다."}"""),
                                    @ExampleObject(
                                            name = "LETTER_003",
                                            value = """
                                                    {"status": 400, "code": "LETTER_003", "message": "편지 내용은 500자를 초과할 수 없습니다."}"""),
                                    @ExampleObject(
                                            name = "LETTER_004",
                                            value = """
                                                    {"status": 400, "code": "LETTER_004", "message": "편지는 영어로만 작성할 수 있습니다."}"""),
                                    @ExampleObject(
                                            name = "LETTER_005",
                                            value = """
                                                    {"status": 400, "code": "LETTER_005", "message": "편지 작성 시각 정보가 올바르지 않습니다."}"""),
                                    @ExampleObject(
                                            name = "COMMON_001",
                                            value = """
                                                    {"status": 400, "code": "COMMON_001", "message": "잘못된 요청입니다."}"""),
                                    @ExampleObject(
                                            name = "USER_005",
                                            value = """
                                                    {"status": 400, "code": "USER_005", "message": "온보딩을 먼저 완료해야 합니다."}""")
                            })),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 유효하지 않은 토큰 / `AUTH_007` 탈퇴한 계정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<LetterCreateResponse> createLetter(
            @LoginUser Long userId,
            @Parameter(description = "편지 작성 요청 객체", required = true)
            @Valid @RequestBody LetterCreateRequest request
    ) {
        LetterCreateResult result = letterService.createLetter(userId, request);
        return ResponseEntity
                .status(result.created() ? CREATED : OK)
                .body(result.response());
    }

    @Operation(
            summary = "편지 상세 · 피드백 조회 (피드백이 완료된 편지는 조회 시 읽음 처리)",
            description = """
                    편지 상세와 도착한 피드백(교정문 · 팁)을 조회한다.

                    - **피드백이 완료된 편지를 조회하면 읽음 처리된다.** 별도의 읽음 처리 API 는 없다.
                    - **본인 편지만** 조회할 수 있다. 없는 편지든 남의 편지든 똑같이 `LETTER_002`(404) 다.
                    - 피드백 완료 전에도 응답은 성공하며 이때 `feedback` 은 `null` 이다. 앱은 완료 전 카드의 진입을 막는다.
                    - 우표는 종류가 운영 중 바뀔 수 있다. 앱은 `stampImage` URL 을 그대로 표시하고 우표 종류로 분기하지 않는다.
                    - 교정문은 `correctionSegments` 를 `sequence` 순서대로 이어붙여 그린다.
                      `UNCHANGED` 는 그대로, `MODIFIED` 는 `originalText` 를 취소선으로 찍고 뒤에 `correctedText` 를 붙인다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 피드백 완료 전이면 feedback 이 null 이다"),
            @ApiResponse(
                    responseCode = "400",
                    description = "`USER_005` 온보딩 미완료",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 유효하지 않은 토큰 / `AUTH_007` 탈퇴한 계정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "`LETTER_002` 존재하지 않거나 본인 편지가 아니다",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{letterId}")
    public ResponseEntity<LetterGetResponse> getLetter(
            @LoginUser Long userId,
            @Parameter(description = "조회할 편지의 ID", required = true)
            @PathVariable Long letterId
    ) {
        return ResponseEntity
                .status(OK)
                .body(letterService.getLetter(userId, letterId));
    }

    @Operation(
            summary = "전체 편지 목록 조회",
            description = """
                    홈 화면의 편지 목록을 조회한다. 닉네임 · 우표 수는 내려가지 않으므로 **홈 진입 시 `GET /api/v1/home` 과 함께 호출한다.**

                    - **본인 편지만** 조회된다. 유저 정보는 토큰에서 가져오므로 파라미터로 보내지 않는다.
                    - 편지를 쓰지 않은 날은 응답에 나타나지 않는다. 캘린더가 아니라 기록이 쌓이는 목록이다.
                    - 정렬은 서버가 처리하므로 앱은 받은 순서대로 그린다. 정렬 기준은 편지 날짜다.
                    - `FEEDBACK_COMPLETED` 이면서 `isRead == false` 면 날짜 앞에 빨간 점을 찍는다. 완료 전 편지에는 찍지 않는다.
                    - 목록에 완료되지 않은 항목이 있으면 앱은 화면 재진입 · 새로고침 때 다시 조회한다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 편지가 없으면 빈 배열이다"),
            @ApiResponse(
                    responseCode = "400",
                    description = "`COMMON_001` 페이징 파라미터 제약 위반 / `USER_005` 온보딩 미완료",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_001",
                                            value = """
                                                    {"status": 400, "code": "COMMON_001", "message": "잘못된 요청입니다."}"""),
                                    @ExampleObject(
                                            name = "USER_005",
                                            value = """
                                                    {"status": 400, "code": "USER_005", "message": "온보딩을 먼저 완료해야 합니다."}""")
                            })),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 유효하지 않은 토큰 / `AUTH_007` 탈퇴한 계정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<LetterListResponse> getLetters(
            @LoginUser Long userId,
            @Parameter(description = "페이지 번호 (0 이상)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기 (1~50)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @Parameter(description = "정렬 기준 - LATEST 최신순 / OLDEST 오래된 순")
            @RequestParam(defaultValue = "LATEST") LetterSort sort
    ) {
        return ResponseEntity
                .status(OK)
                .body(letterService.getLetters(userId, page, size, sort));
    }
}
