package com.dearjolly.server.domain.user.repository;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.domain.user.enums.Role;
import com.dearjolly.server.domain.user.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Users, Long> {
    Optional<Users> findByOauthProviderAndOauthId(OauthProvider oauthProvider, String oauthId);

    Optional<Users> findFirstByRoleAndStatus(Role role, UserStatus status);

    List<Users> findAllByDeletedAtBefore(LocalDateTime threshold);
}
