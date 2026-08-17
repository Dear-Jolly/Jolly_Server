package com.dearjolly.server.domain.user.dto.response;

import com.dearjolly.server.domain.user.entity.Users;

public record NicknameUpdateResponse(
        Long userId,
        String nickname
) {
    public static NicknameUpdateResponse of(Users user) {
        return new NicknameUpdateResponse(
                user.getId(),
                user.getNickname()
        );
    }
}
