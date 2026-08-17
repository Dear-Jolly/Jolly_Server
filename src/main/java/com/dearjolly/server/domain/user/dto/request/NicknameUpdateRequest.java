package com.dearjolly.server.domain.user.dto.request;

import static com.dearjolly.server.domain.user.constants.UserValidationConstants.NICKNAME_MAX_LENGTH;
import static com.dearjolly.server.domain.user.constants.UserValidationConstants.NICKNAME_MIN_LENGTH;
import static com.dearjolly.server.domain.user.constants.UserValidationConstants.NICKNAME_REGEX;

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
        @Pattern(regexp = NICKNAME_REGEX, message = "올바른 닉네임 형식이 아닙니다.")
        String nickname
) {
}
