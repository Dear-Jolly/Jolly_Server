package com.dearjolly.server.domain.letter.repository;

import com.dearjolly.server.domain.letter.entity.Letters;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LetterRepository extends JpaRepository<Letters, Long> {
    Optional<Letters> findFirstByUserIdOrderByIdDesc(Long userId);
}
