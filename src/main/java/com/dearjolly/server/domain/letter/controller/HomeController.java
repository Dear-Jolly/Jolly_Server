package com.dearjolly.server.domain.letter.controller;

import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.domain.letter.dto.response.HomeGetResponse;
import com.dearjolly.server.domain.letter.service.LetterService;
import com.dearjolly.server.global.auth.principal.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "홈", description = "홈 헤더 정보 조회 API")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {
    private final LetterService letterService;

    @Operation(summary = "닉네임, 모은 우표 수 조회")
    @GetMapping
    public ResponseEntity<HomeGetResponse> getHome(@LoginUser Long userId) {
        return ResponseEntity
                .status(OK)
                .body(letterService.getHome(userId));
    }
}
