package com.dearjolly.server.domain.user.controller;

import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.domain.user.dto.request.LoginRequest;
import com.dearjolly.server.domain.user.dto.request.NicknameUpdateRequest;
import com.dearjolly.server.domain.user.dto.response.LoginResponse;
import com.dearjolly.server.domain.user.dto.response.NicknameUpdateResponse;
import com.dearjolly.server.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity
                .status(OK)
                .body(userService.login(request));
    }

    @PatchMapping("/nickname")
    public ResponseEntity<NicknameUpdateResponse> updateNickname(
            @RequestParam Long userId,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        return ResponseEntity
                .status(OK)
                .body(userService.updateNickname(userId, request));
    }
}
