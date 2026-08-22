package com.dearjolly.server.global.auth.principal;

import com.dearjolly.server.domain.user.enums.Role;

/**
 * 인증 필터가 SecurityContext 에 심는 principal.
 * 모든 조회는 이 값의 userId 를 쓰며, 경로·파라미터의 userId 는 신뢰하지 않는다 (비기능 §4.1).
 */
public record AuthUser(
        Long userId,
        Role role
) {
}
