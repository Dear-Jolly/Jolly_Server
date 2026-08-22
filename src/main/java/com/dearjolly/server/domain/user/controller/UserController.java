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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "사용자", description = "약관 동의, 계정 조회, 탈퇴, 닉네임 설정 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "약관 동의 및 마케팅 동의 철회")
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

    @Operation(summary = "계정 정보 조회")
    @GetMapping
    public ResponseEntity<UserGetResponse> getUser(@LoginUser Long userId) {
        return ResponseEntity
                .status(OK)
                .body(userService.getUser(userId));
    }

    @Operation(summary = "회원 탈퇴")
    @DeleteMapping
    public ResponseEntity<Void> withdraw(@LoginUser Long userId) {
        userService.withdraw(userId);
        return ResponseEntity
                .status(NO_CONTENT)
                .build();
    }

    @Operation(summary = "닉네임 등록 및 변경")
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
