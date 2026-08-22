package com.dearjolly.server.domain.user.controller;

import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.domain.user.dto.request.NicknameUpdateRequest;
import com.dearjolly.server.domain.user.dto.request.TermsAgreeRequest;
import com.dearjolly.server.domain.user.dto.response.NicknameUpdateResponse;
import com.dearjolly.server.domain.user.dto.response.TermsAgreeResponse;
import com.dearjolly.server.domain.user.dto.response.UserGetResponse;
import com.dearjolly.server.domain.user.service.UserService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자", description = """
        약관 동의, 계정 조회, 탈퇴, 닉네임 설정 API.

        온보딩(필수 약관 2종 동의 + 닉네임 등록)을 구성하는 API 이므로 **네 개 모두 온보딩 가드를 통과한다.**
        온보딩을 마치지 않은 유저도 호출할 수 있다.

        실패는 각 API 에 적힌 코드와 공통 인증 오류(`AUTH_005` · `AUTH_007`)뿐이다.""")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @Operation(
            summary = "약관 동의 및 마케팅 동의 철회",
            description = """
                    약관 동의 내역을 저장한다. 온보딩 약관 동의와 설정 화면의 마케팅 동의 철회가 **같은 API** 를 쓴다.

                    - 약관은 3종이다. **`SERVICE`(서비스 이용약관) · `PRIVACY`(개인정보 처리방침)는 필수, `MARKETING`(마케팅 정보 수신)은 선택**이다.
                    - 마케팅에 동의하지 않아도 서비스 이용에 제한이 없다.
                    - 필수 2건이 모두 동의 상태가 아니면 `USER_002` 이며, **이때 그 요청은 아무것도 저장되지 않는다.**
                      온보딩에서는 필수 2건을 항상 함께 보낸다.
                    - **보내지 않은 항목은 그대로 유지된다.** 마케팅만 철회하려면 `MARKETING` 한 건만 보내면 된다.
                    - **약관이 개정돼도 다시 묻지 않는다.**
                    - 약관 본문은 이 API 가 주지 않는다. 버전 조회 API 의 웹뷰 링크로 연다.
                    - 생성이지만 응답은 `201` 이 아니라 `200` 이다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공. 반영 후의 필수 약관 동의 완료 여부를 돌려준다"),
            @ApiResponse(
                    responseCode = "400",
                    description = "`USER_002` 필수 약관 미동의 / `COMMON_001` 정의되지 않은 약관 종류 · 바디 형식 오류",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "USER_002",
                                            value = """
                                                    {"status": 400, "code": "USER_002", "message": "필수 약관에 모두 동의해야 합니다."}"""),
                                    @ExampleObject(
                                            name = "COMMON_001",
                                            value = """
                                                    {"status": 400, "code": "COMMON_001", "message": "잘못된 요청입니다."}""")
                            })),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 유효하지 않은 토큰 / `AUTH_007` 탈퇴한 계정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/terms")
    public ResponseEntity<TermsAgreeResponse> agreeTerms(
            @LoginUser Long userId,
            @Parameter(description = "약관 동의 요청 객체", required = true)
            @Valid @RequestBody TermsAgreeRequest request
    ) {
        return ResponseEntity
                .status(OK)
                .body(userService.agreeTerms(userId, request));
    }

    @Operation(
            summary = "계정 정보 조회",
            description = """
                    설정 화면에 표시할 계정 정보를 조회한다.

                    - **이메일이 비어 있을 수 있다.** 애플에서 이메일 제공을 거부한 경우이며, 이때 앱은 로그인 수단만 표시한다.
                    - 온보딩 전에도 호출할 수 있고, 이 경우 `nickname` 이 비어 있다.
                    - `marketingAgreed` 는 마케팅 동의 이력이 없으면 `false` 다.
                    - 실패는 공통 인증 오류(`AUTH_005` · `AUTH_007`)뿐이다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 유효하지 않은 토큰 / `AUTH_007` 탈퇴한 계정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<UserGetResponse> getUser(@LoginUser Long userId) {
        return ResponseEntity
                .status(OK)
                .body(userService.getUser(userId));
    }

    @Operation(
            summary = "회원 탈퇴",
            description = """
                    회원 탈퇴를 처리한다. **모든 편지와 계정 정보가 삭제되며 복구할 수 없다.**

                    - 탈퇴 즉시 로그인이 풀린다. 남아 있던 액세스 토큰도 `AUTH_007` 로 거절된다.
                    - 탈퇴 후 같은 소셜 계정으로 다시 로그인하면 **신규 가입**으로 처리되고, 이전 편지는 돌아오지 않는다.
                    - 소셜 연결 해제(카카오 unlink / 애플 revoke)는 서버가 처리한다. **앱이 인가 코드를 따로 보낼 필요가 없다.**
                    - 온보딩 미완료 상태에서도 탈퇴할 수 있다.
                    - 실패는 공통 인증 오류(`AUTH_005` · `AUTH_007`)뿐이다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "탈퇴 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 유효하지 않은 토큰 / `AUTH_007` 탈퇴한 계정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping
    public ResponseEntity<Void> withdraw(@LoginUser Long userId) {
        userService.withdraw(userId);
        return ResponseEntity
                .status(NO_CONTENT)
                .build();
    }

    @Operation(
            summary = "닉네임 등록 및 변경",
            description = """
                    닉네임을 등록하거나 변경한다. 온보딩(이름 입력)과 설정(이름 변경)이 **같은 API** 를 쓴다.

                    - **영문 · 숫자 1~20자만** 허용한다. 공백 · 특수기호 · 한글은 불가다.
                    - 길이는 **문자 수** 기준이라 앱의 `10/20` 카운터와 일치한다.
                    - **중복을 허용한다.** 같은 닉네임을 써도 에러가 나지 않는다.
                    - 변경 횟수 제한이 없다.

                    **검증 순서** — **길이를 먼저 보고 통과하면 문자를 본다.** 두 조건을 동시에 어겨도 에러는 하나만 내려간다.
                    21자 한글은 `USER_003` 이 아니라 `USER_004` 다.

                    | 순서 | 규칙 | 값 | code |
                    | --- | --- | --- | --- |
                    | 1 | 길이 | 1 ~ 20자 (문자 수). `null` · `""` 는 0자로 본다 | `USER_004` |
                    | 2 | 허용 문자 | 영문 + 숫자 (`^[A-Za-z0-9]+$`) | `USER_003` |

                    앱은 사유별 문구(`공백을 포함할 수 없어요` / `특수 기호를 포함할 수 없어요` / `한글을 포함할 수 없어요`)를
                    클라이언트에서 판별해 표시한다. 서버는 최종 방어선이다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "`USER_004` 길이 위반 / `USER_003` 허용하지 않는 문자",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "USER_004",
                                            value = """
                                                    {"status": 400, "code": "USER_004", "message": "닉네임은 1자 이상 20자 이하여야 합니다."}"""),
                                    @ExampleObject(
                                            name = "USER_003",
                                            value = """
                                                    {"status": 400, "code": "USER_003", "message": "닉네임은 공백, 특수기호, 한글 없이 작성해야 합니다."}""")
                            })),
            @ApiResponse(
                    responseCode = "401",
                    description = "`AUTH_005` 유효하지 않은 토큰 / `AUTH_007` 탈퇴한 계정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/nickname")
    public ResponseEntity<NicknameUpdateResponse> updateNickname(
            @LoginUser Long userId,
            @Parameter(description = "닉네임 변경 요청 객체", required = true)
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        return ResponseEntity
                .status(OK)
                .body(userService.updateNickname(userId, request));
    }
}
