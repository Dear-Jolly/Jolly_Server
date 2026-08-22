package com.dearjolly.server.global.auth.principal;

import com.dearjolly.server.domain.user.enums.Role;

public record AuthUser(
        Long userId,
        Role role
) {
}
