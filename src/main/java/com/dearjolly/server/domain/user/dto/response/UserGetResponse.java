package com.dearjolly.server.domain.user.dto.response;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.OauthProvider;

/**
 * @param nickname 온보딩 전에는 null
 * @param email provider 미제공 시 null
 */
public record UserGetResponse(
        String nickname,
        OauthProvider provider,
        String email,
        boolean marketingAgreed
) {
    public static UserGetResponse of(Users user, boolean marketingAgreed) {
        return new UserGetResponse(
                user.getNickname(),
                user.getOauthProvider(),
                user.getEmail(),
                marketingAgreed
        );
    }
}
