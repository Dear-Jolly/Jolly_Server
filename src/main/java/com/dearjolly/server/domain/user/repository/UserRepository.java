package com.dearjolly.server.domain.user.repository;

import com.dearjolly.server.domain.user.entity.Users;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByEmail(String email);
}
