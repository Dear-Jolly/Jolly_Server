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

    // feedback 을 함께 가져오지 않으면 편지마다 조회가 한 번씩 더 나간다.
    // 비소유 측 @OneToOne 은 프록시를 만들 수 없어, Hibernate 가 null 여부를 알려고 행마다 쿼리를 날린다.
    @EntityGraph(attributePaths = {"stamp", "feedback"})
    Slice<Letters> findAllByUserId(Long userId, Pageable pageable);

    long countByUserIdAndStatus(Long userId, Status status);
}
