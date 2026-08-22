package com.dearjolly.server.domain.letter.controller;

import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.domain.letter.dto.response.HomeGetResponse;
import com.dearjolly.server.domain.letter.service.LetterService;
import com.dearjolly.server.global.auth.principal.LoginUser;
import com.dearjolly.server.global.exception.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "홈", description = "홈 헤더 정보 조회 API. **온보딩 가드 대상**이라 미완료 유저가 호출하면 `USER_005`(400) 다.")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {
    private final LetterService letterService;

    @Operation(
            summary = "닉네임, 모은 우표 수 조회",
            description = """
                    - **홈 진입 시 `GET /api/v1/letters` 와 함께 호출한다.** 편지 목록 응답에는 이 두 값이 들어 있지 않다.
                    - `totalStampCount` 는 작성한 편지 수가 아니라 **피드백이 완료된 편지 수**다.
                    - `nickname` 은 온보딩 가드 덕분에 항상 값이 있다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "`USER_005` 온보딩 미완료",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 유효하지 않은 토큰 / `AUTH_007` 탈퇴한 계정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<HomeGetResponse> getHome(@LoginUser Long userId) {
        return ResponseEntity
                .status(OK)
                .body(letterService.getHome(userId));
    }
}
