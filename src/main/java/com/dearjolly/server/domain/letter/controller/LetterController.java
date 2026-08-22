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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    @Operation(summary = "편지 상세 · 피드백 조회 (피드백이 완료된 편지는 조회 시 읽음 처리)")
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

    @Operation(summary = "전체 편지 목록 조회")
    @GetMapping
    public ResponseEntity<LetterListResponse> getLetters(
            @LoginUser Long userId,
            @Parameter(description = "페이지 번호 (0 이상)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기 (1~50)")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @Parameter(description = "정렬 기준 (LATEST, OLDEST)")
            @RequestParam(defaultValue = "LATEST") LetterSort sort
    ) {
        return ResponseEntity
                .status(OK)
                .body(letterService.getLetters(userId, page, size, sort));
    }
}
