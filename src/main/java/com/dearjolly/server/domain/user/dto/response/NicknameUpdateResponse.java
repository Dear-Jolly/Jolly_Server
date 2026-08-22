package com.dearjolly.server.domain.user.dto.response;

import com.dearjolly.server.domain.user.entity.Users;

public record NicknameUpdateResponse(
        String nickname
) {
    public static NicknameUpdateResponse from(Users user) {
        return new NicknameUpdateResponse(user.getNickname());
    }
}
