package com.dearjolly.server.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;

public record NicknameUpdateRequest(

        @NotBlank(message = "닉네임은 필수입니다.")
        @Length(
                min = NICKNAME_MIN_LENGTH,
                max = NICKNAME_MAX_LENGTH,
                message = "닉네임은 {min}자 이상 {max}자 이하여야 합니다."
        )
        @Pattern(regexp = NICKNAME_REGEX, message = "닉네임은 영문자와 숫자만 사용 가능합니다.")
        String nickname
) {
        public static final int NICKNAME_MIN_LENGTH = 2;
        public static final int NICKNAME_MAX_LENGTH = 10;
        public static final String NICKNAME_REGEX = "^[a-zA-Z0-9]+$";
}
