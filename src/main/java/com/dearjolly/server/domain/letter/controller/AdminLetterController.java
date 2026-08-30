package com.dearjolly.server.domain.letter.controller;

import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.domain.letter.dto.response.AdminFailedLetterListResponse;
import com.dearjolly.server.domain.letter.dto.response.AdminLetterRetryResponse;
import com.dearjolly.server.domain.letter.service.AdminLetterService;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자", description = "운영자용 API. 관리자 권한 토큰이 필요하다.")
@RestController
@RequestMapping("/api/v1/admin/letters")
@RequiredArgsConstructor
public class AdminLetterController {
    private final AdminLetterService adminLetterService;

    @Operation(
            summary = "피드백 실패 편지 조회 (관리자)",
            description = """
                    - **앱에 실패로 보이는 편지**를 모두 내려준다. 한 번이라도 피드백에 실패하고 아직 완료되지 않은 편지다.
                    - 서버가 아직 재시도를 이어가는 중일 수 있다. `status` 는 서버 내부 상태이고,
                      `nextRetryAt` 이 있으면 그 시각에 자동으로 다시 시도한다.
                    - `nextRetryAt` 이 `null` 이면 재시도 예산을 모두 쓴 편지다. 재처리하려면 재시도 API 를 호출한다.
                    - 최근에 실패한 편지가 먼저 온다.
                    - **관리자 토큰이 필요하다.** `POST /api/v1/admin/login` 으로 발급받는다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 실패한 편지가 없으면 빈 배열이다"),
            @ApiResponse(
                    responseCode = "400",
                    description = "`COMMON_001` 페이징 파라미터 제약 위반",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 토큰 없음 또는 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "`AUTH_006` 관리자 권한 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/failed")
    public ResponseEntity<AdminFailedLetterListResponse> getFailedLetters(
            @LoginUser Long adminId,

            @Parameter(description = "페이지 번호 (0 이상)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "페이지 크기 (1~50)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity
                .status(OK)
                .body(adminLetterService.getFailedLetters(adminId, page, size));
    }

    @Operation(
            summary = "피드백 재시도 (관리자)",
            description = """
                    - 실패한 편지의 AI 피드백을 **즉시 다시 시도한다.** 재시도 횟수를 0으로 되돌리므로 예산 5회를 새로 쓴다.
                    - 우표는 준비 중(`soon`) 우표로 돌아가고, 앱에도 다시 준비 중으로 보인다.
                      재시도가 성공하면 사용자는 실패 화면을 더 이상 보지 않는다.
                    - 응답은 요청 접수 결과다. **피드백 생성을 기다리지 않는다.** 결과는 실패 편지 조회로 다시 확인한다.
                    - 이미 완료된 편지는 `LETTER_006` 이다. 되돌릴 이유가 없다.
                    - 다른 워커가 처리 중인 편지는 `LETTER_007` 이다. 같은 편지에 OpenAI 를 두 번 호출하게 되기 때문이다.
                      15분 넘게 멈춰 있는 편지는 워커가 죽은 것으로 보고 재시도를 허용한다.
                    - **관리자 토큰이 필요하다.** `POST /api/v1/admin/login` 으로 발급받는다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재시도 접수 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "`LETTER_006` 이미 피드백 완료 / `LETTER_007` 처리 중",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "LETTER_006",
                                            value = """
                                                    {"status": 400, "code": "LETTER_006", "message": "이미 피드백이 완료된 편지입니다."}"""),
                                    @ExampleObject(
                                            name = "LETTER_007",
                                            value = """
                                                    {"status": 400, "code": "LETTER_007", "message": "피드백을 처리 중인 편지입니다."}""")
                            })),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 토큰 없음 또는 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "`AUTH_006` 관리자 권한 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "`LETTER_002` 존재하지 않는 편지",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{letterId}/feedback/retry")
    public ResponseEntity<AdminLetterRetryResponse> retryFeedback(
            @LoginUser Long adminId,

            @Parameter(description = "재시도할 편지 ID", example = "16", required = true)
            @PathVariable Long letterId
    ) {
        return ResponseEntity
                .status(OK)
                .body(adminLetterService.retryFeedback(adminId, letterId));
    }
}
