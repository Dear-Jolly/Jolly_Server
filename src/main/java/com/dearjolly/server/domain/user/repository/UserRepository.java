package com.dearjolly.server.domain.user.repository;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Users, Long> {

    /**
     * 로그인 시 회원 조회 키는 (oauth_provider, oauth_id) 다.
     * email 은 표시용이며 provider 가 다르면 같은 주소가 존재할 수 있다 (ERD §2.1).
     */
    Optional<Users> findByOauthProviderAndOauthId(OauthProvider oauthProvider, String oauthId);

    /** 유예기간이 지난 탈퇴 계정. 조회 후 delete(entity) 로 지워야 cascade 가 동작한다 (ERD §3.3). */
    List<Users> findAllByDeletedAtBefore(LocalDateTime threshold);
}
