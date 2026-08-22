package com.dearjolly.server.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 길이(USER_004)와 문자(USER_003)를 다른 코드로 구분해야 하므로
 * Bean Validation 으로 형식을 검사하지 않고 서비스에서 순서대로 검증한다 (기능명세 §3.2.2).
 */
public record NicknameUpdateRequest(

        @NotBlank(message = "닉네임은 필수입니다.")
        String nickname
) {
}
