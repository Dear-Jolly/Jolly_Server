package com.dearjolly.server.domain.user.dto.response;

import com.dearjolly.server.domain.user.entity.Users;

public record LoginResponse(
        Long userId,
        String email,
        String nickname
) {
    public static LoginResponse of(Users user) {
        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname()
        );
    }
}
