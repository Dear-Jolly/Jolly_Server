package com.dearjolly.server.global.auth.jwt;

import com.dearjolly.server.domain.user.enums.Role;

public record JwtPayload(Long userId, Role role) {
}
