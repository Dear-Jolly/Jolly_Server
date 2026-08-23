package com.dearjolly.server.global.auth.principal;

import com.dearjolly.server.domain.user.entity.Users;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * 인증 필터가 조회한 사용자를 같은 요청 안에서 다시 쓰기 위해 담아 둔다.
 * <p>
 * 온보딩 가드가 같은 사용자를 한 번 더 조회하면 편지 · 홈 API 마다 조회가 두 번씩 나간다.
 * 담기는 시점이 트랜잭션 밖이라 지연 로딩 필드는 건드릴 수 없고, 이미 읽어 온 값만 쓴다.
 */
@Component
@RequestScope
public class AuthenticatedUserHolder {
    private Users user;

    public void set(Users user) {
        this.user = user;
    }

    public Users get() {
        return user;
    }
}
