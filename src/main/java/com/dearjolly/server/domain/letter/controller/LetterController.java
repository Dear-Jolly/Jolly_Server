package com.dearjolly.server.domain.letter.controller;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.domain.letter.dto.request.LetterCreateRequest;
import com.dearjolly.server.domain.letter.dto.response.LetterCreateResponse;
import com.dearjolly.server.domain.letter.dto.response.LetterCreateResult;
import com.dearjolly.server.domain.letter.service.LetterService;
import com.dearjolly.server.global.auth.principal.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "편지", description = "편지 작성, 조회 API")
@RestController
@RequestMapping("/api/v1/letters")
@RequiredArgsConstructor
public class LetterController {
    private final LetterService letterService;

    @Operation(summary = "편지 작성 및 피드백 요청 (60초 이내 같은 본문 재전송은 200 으로 최초 편지 반환)")
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
}
