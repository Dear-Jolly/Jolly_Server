package com.dearjolly.server.domain.letter.repository;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.enums.Status;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LetterRepository extends JpaRepository<Letters, Long> {
    Optional<Letters> findFirstByUserIdOrderByIdDesc(Long userId);

    @EntityGraph(attributePaths = {"stamp", "feedback"})
    Optional<Letters> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "stamp")
    Slice<Letters> findAllByUserId(Long userId, Pageable pageable);

    long countByUserIdAndStatus(Long userId, Status status);
}
