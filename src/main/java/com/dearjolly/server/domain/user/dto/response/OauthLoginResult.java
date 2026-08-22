package com.dearjolly.server.domain.user.dto.response;

import com.dearjolly.server.domain.user.entity.Users;

public record OauthLoginResult(
        String accessToken,
        String refreshToken,
        Long userId,
        boolean isNewUser,
        boolean termsAgreed,
        boolean nicknameRegistered
) {
    public static OauthLoginResult of(Users user, String accessToken, String refreshToken, boolean isNewUser, boolean termsAgreed) {
        return new OauthLoginResult(
                accessToken,
                refreshToken,
                user.getId(),
                isNewUser,
                termsAgreed,
                user.isNicknameRegistered()
        );
    }
}
